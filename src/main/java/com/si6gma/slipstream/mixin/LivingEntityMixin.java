package com.si6gma.slipstream.mixin;

import com.si6gma.slipstream.GroundEffectMath;
import com.si6gma.slipstream.GroundEffectParticles;
import com.si6gma.slipstream.GroundEffectSample;
import com.si6gma.slipstream.GroundEffectSampler;
import com.si6gma.slipstream.LocalGroundEffectState;
import com.si6gma.slipstream.ServerParticleSink;
import com.si6gma.slipstream.SlipstreamConfig;
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
      if (isLocalPlayer) LocalGroundEffectState.clear();
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
