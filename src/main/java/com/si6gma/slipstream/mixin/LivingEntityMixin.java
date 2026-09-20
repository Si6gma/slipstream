package com.si6gma.slipstream.mixin;

import com.si6gma.slipstream.GroundEffectMath;
import com.si6gma.slipstream.GroundEffectParticles;
import com.si6gma.slipstream.GroundEffectSample;
import com.si6gma.slipstream.GroundEffectSampler;
import com.si6gma.slipstream.LocalGroundEffectState;
import com.si6gma.slipstream.ServerParticleSink;
import com.si6gma.slipstream.SlipstreamConfig;
import com.si6gma.slipstream.draft.DraftQuery;
import com.si6gma.slipstream.draft.DraftingMath;
import com.si6gma.slipstream.draft.WakeTracker;
import com.si6gma.slipstream.draft.WakeTrackers;
import com.si6gma.slipstream.draft.WakeTrail;
import com.si6gma.slipstream.network.ServerConfigOverride;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements GroundEffectSampler {

  // Per entity raycast cache. Instance fields are GC'd with the entity, so no
  // explicit cleanup needed. Dead entities can't call travel(), so stale cache is never read.
  @Unique private BlockHitResult ege$cachedHit;
  @Unique private double ege$cacheX, ege$cacheY, ege$cacheZ;
  @Unique private int ege$cacheAge;

  // Per tick sample memo. travel() and the client feel handler both sample the same entity in the
  // same tick; without this the shared raycast cache would age twice per tick and refresh early.
  @Unique private GroundEffectSample ege$sample;
  @Unique private int ege$sampleTick = -1;

  /** Only leaders within this many blocks are considered, squared for a cheap comparison. */
  @Unique private static final double EGE$DRAFT_RANGE_SQ = 64.0 * 64.0;

  @Override
  public GroundEffectSample slipstream$sample(SlipstreamConfig cfg) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (ege$sampleTick == self.tickCount) return ege$sample;
    GroundEffectSample computed = ege$computeSample(cfg);
    ege$sampleTick = self.tickCount;
    ege$sample = computed;
    return computed;
  }

  @Unique
  private GroundEffectSample ege$computeSample(SlipstreamConfig cfg) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (!self.isFallFlying()) return null;
    if (self.isUnderWater() || self.isInLava()) return null;

    Vec3 velocity = self.getDeltaMovement();
    double hSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
    if (hSpeedSq < 0.0025) return null;

    double hSpeed = Math.sqrt(hSpeedSq);
    Vec3 pos = self.position();
    Vec3 travelDir = new Vec3(velocity.x / hSpeed, 0, velocity.z / hSpeed);

    // O(1) heightmap precheck bail before any raycast when clearly too high
    int heightmapY =
        self.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
    if (pos.y - heightmapY > cfg.effectHeightBlocks) return null;

    // Raycast cache reuse: skip raycast if player moved <1 block and cache is ≤3 ticks old
    ege$cacheAge++;
    double dx = pos.x - ege$cacheX;
    double dz = pos.z - ege$cacheZ;
    double dy = pos.y - ege$cacheY;
    if (ege$cachedHit == null || ege$cacheAge > 3 || dx * dx + dy * dy + dz * dz > 1.0) {
      ege$cachedHit =
          self.level()
              .clip(
                  new ClipContext(
                      pos,
                      pos.add(0, -cfg.effectHeightBlocks, 0),
                      ClipContext.Block.COLLIDER,
                      ClipContext.Fluid.ANY,
                      self));
      ege$cacheX = pos.x;
      ege$cacheY = pos.y;
      ege$cacheZ = pos.z;
      ege$cacheAge = 0;
    }
    BlockHitResult surfaceHit = ege$cachedHit;
    if (surfaceHit.getType() == HitResult.Type.MISS) return null;

    double distToSurface = pos.y - surfaceHit.getLocation().y;
    if (distToSurface <= 0 || distToSurface >= cfg.effectHeightBlocks) return null;

    double proximity = GroundEffectMath.proximity(distToSurface, cfg.effectHeightBlocks);
    BlockState surfaceBlock = self.level().getBlockState(surfaceHit.getBlockPos());
    boolean isWater = surfaceBlock.getFluidState().is(FluidTags.WATER);
    Vec3 right = new Vec3(travelDir.z, 0, -travelDir.x);
    return new GroundEffectSample(
        distToSurface,
        proximity,
        surfaceBlock,
        surfaceHit.getLocation().y,
        isWater,
        hSpeed,
        travelDir,
        right);
  }

  /**
   * Applies the strongest available draft to the local player: a forward boost along their own
   * heading, a pull toward the wake centreline, and a small bonus for leading others.
   */
  @Unique
  private Vec3 ege$applyDrafting(
      Player player, Vec3 velocity, SlipstreamConfig cfg, double groundProximity) {
    WakeTracker tracker = WakeTrackers.forLevel(player.level());
    Vec3 pos = player.position();
    int now = player.tickCount;

    double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    if (hSpeed < 1.0e-4) return velocity;
    Vec3 heading = new Vec3(velocity.x / hSpeed, 0, velocity.z / hSpeed);

    DraftQuery best = null;
    int drafterCount = 0;
    WakeTrail myTrail = tracker.trailFor(player.getUUID());

    for (Player other : player.level().players()) {
      if (other == player) continue;
      if (other.position().distanceToSqr(pos) > EGE$DRAFT_RANGE_SQ) continue;

      DraftQuery q = DraftingMath.nearest(tracker.trailFor(other.getUUID()), pos, now, cfg);
      if (q != null && (best == null || q.strength() > best.strength())) best = q;

      // Anyone sitting in my own wake earns me the leader bonus.
      if (other.isFallFlying()
          && DraftingMath.nearest(myTrail, other.position(), now, cfg) != null) {
        drafterCount++;
      }
    }

    Vec3 result = velocity;

    if (best != null) {
      double boost = DraftingMath.boostDelta(hSpeed, best.strength(), cfg);
      if (boost > 0.0) result = result.add(heading.scale(boost));

      double divergence = ege$lookDivergenceDeg(player, best.wakeHeading());
      double pull = DraftingMath.pullForce(best.lateralOffset(), divergence, best.strength(), cfg);
      if (pull > 0.0 && best.toCentre().lengthSqr() > 0.0) {
        Vec3 step = best.toCentre().scale(pull);
        // Near a surface the ground effect owns the vertical axis, so the two never fight for it.
        double verticalShare = 1.0 - Math.max(0.0, Math.min(1.0, groundProximity));
        result = result.add(step.x, step.y * verticalShare, step.z);
      }
    }

    double bonus = DraftingMath.leaderBonus(drafterCount, cfg);
    if (bonus > 0.0) {
      double currentHSpeed = Math.sqrt(result.x * result.x + result.z * result.z);
      if (currentHSpeed < cfg.maxSpeedBlocksPerTick) {
        // A leader keeps the normal ceiling; only an actual drafter gets the raised one.
        double room = cfg.maxSpeedBlocksPerTick - currentHSpeed;
        result = result.add(heading.scale(Math.min(bonus, room)));
      }
    }

    // The pull can point partly forward when the nearest wake point is ahead of the follower, so
    // the composed velocity is clamped here. Without this the pull bypasses the ceiling a server
    // configured through draftSpeedMultiplier.
    double finalHSpeed = Math.sqrt(result.x * result.x + result.z * result.z);
    double cap = DraftingMath.draftCap(cfg);
    if (finalHSpeed > cap && finalHSpeed > 1.0e-6) {
      double scale = cap / finalHSpeed;
      result = new Vec3(result.x * scale, result.y, result.z * scale);
    }

    return result;
  }

  /** Absolute angle in degrees between the player's horizontal look and the wake heading. */
  @Unique
  private double ege$lookDivergenceDeg(Player player, Vec3 wakeHeading) {
    Vec3 look = player.getLookAngle();
    double lookLen = Math.sqrt(look.x * look.x + look.z * look.z);
    if (lookLen < 1.0e-4) return 180.0;
    double dot = (look.x * wakeHeading.x + look.z * wakeHeading.z) / lookLen;
    return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
  }

  @Inject(method = "travel", at = @At("TAIL"))
  private void applyGroundEffect(Vec3 travelVector, CallbackInfo ci) {
    LivingEntity self = (LivingEntity) (Object) this;

    // On client: returns server override if one was received, else local config.
    // On server: active is always null, so always returns local config.
    SlipstreamConfig cfg = ServerConfigOverride.get();
    boolean isLocalPlayer =
        self.level().isClientSide() && self instanceof Player player && player.isLocalPlayer();

    GroundEffectSample sample = slipstream$sample(cfg);
    if (sample == null) {
      if (isLocalPlayer) {
        LocalGroundEffectState.clear();
        // Drafting does not need a surface below. Away from the ground there is no ground effect
        // lift to yield to, so the pull keeps its full vertical component (proximity zero).
        Player player = (Player) (Object) this;
        if (ServerConfigOverride.isBoostAllowed() && cfg.draftingEnabled) {
          Vec3 current = self.getDeltaMovement();
          Vec3 drafted = ege$applyDrafting(player, current, cfg, 0.0);
          if (drafted != current) player.setDeltaMovement(drafted);
        }
      }
      return;
    }

    Vec3 velocity = self.getDeltaMovement();
    double hSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
    double hSpeed = sample.hSpeed();
    Vec3 pos = self.position();
    Vec3 travelDir = sample.travelDir();
    double maxSpeedSq = cfg.maxSpeedBlocksPerTick * cfg.maxSpeedBlocksPerTick;
    double speedGate = cfg.effectSpeedThreshold * cfg.maxSpeedBlocksPerTick;
    double proximity = sample.proximity();

    if (self.level().isClientSide()) {
      // Speed boost must be client side (elytra is client authoritative)
      if (!(self instanceof Player player) || !player.isLocalPlayer()) return;

      LocalGroundEffectState.set(
          proximity,
          GroundEffectMath.speedRatio(hSpeed, cfg.maxSpeedBlocksPerTick),
          sample.isWater());

      if (ServerConfigOverride.isBoostAllowed()) {
        Vec3 result = velocity;
        if (hSpeedSq < maxSpeedSq) {
          double delta =
              GroundEffectMath.boostDelta(
                  hSpeed,
                  velocity.y,
                  proximity,
                  cfg.accelerationPerTick,
                  cfg.maxSpeedBlocksPerTick);
          if (delta > 0) {
            result = velocity.add(travelDir.scale(delta));
            double newHSpeedSq = result.x * result.x + result.z * result.z;
            if (newHSpeedSq > maxSpeedSq) {
              result = velocity.add(travelDir.scale(cfg.maxSpeedBlocksPerTick - hSpeed));
            }
          }
        }
        // Lift gate: speed threshold AND look pitch within ±30°.
        // Look direction is the primary intent signal: looking steeper than 30° means
        // the player wants to dive or climb freely, so lift disengages immediately
        // rather than fighting the velocity change.
        double resultHSpeed = Math.sqrt(result.x * result.x + result.z * result.z);
        if (resultHSpeed >= speedGate && Math.abs(player.getXRot()) <= 30.0f) {
          // Use look pitch so the ground effect barrier follows player intent, not a lagging
          // velocity vector. getXRot() is negative when looking up, so negate to match the
          // atan2(ySpeed, hSpeed) convention used by liftForce.
          double lookPitchDeg = -player.getXRot();
          double lift =
              GroundEffectMath.liftForce(
                  result.y,
                  lookPitchDeg,
                  proximity,
                  cfg.liftStrength,
                  resultHSpeed,
                  cfg.maxSpeedBlocksPerTick);
          if (lift != 0.0) result = result.add(0, lift, 0);
        }
        if (cfg.draftingEnabled) {
          result = ege$applyDrafting(player, result, cfg, proximity);
        }
        if (result != velocity) player.setDeltaMovement(result);
      }

    } else {
      if (!(self instanceof ServerPlayer player)) return;
      GroundEffectParticles.emit(
          sample,
          cfg,
          player.tickCount,
          player.getRandom(),
          pos,
          new ServerParticleSink(player.level()));
    }
  }
}
