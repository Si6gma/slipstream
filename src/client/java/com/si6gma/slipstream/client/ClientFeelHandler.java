package com.si6gma.slipstream.client;

import com.si6gma.slipstream.GroundEffectMath;
import com.si6gma.slipstream.GroundEffectParticles;
import com.si6gma.slipstream.GroundEffectSample;
import com.si6gma.slipstream.GroundEffectSampler;
import com.si6gma.slipstream.LocalGroundEffectState;
import com.si6gma.slipstream.LocalParticleSink;
import com.si6gma.slipstream.ModParticles;
import com.si6gma.slipstream.SlipstreamConfig;
import com.si6gma.slipstream.draft.DraftQuery;
import com.si6gma.slipstream.draft.DraftingMath;
import com.si6gma.slipstream.draft.WakeSample;
import com.si6gma.slipstream.draft.WakeTrackers;
import com.si6gma.slipstream.draft.WakeTrail;
import com.si6gma.slipstream.network.ServerConfigOverride;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * End of client tick: smooths the FOV kick, keeps the wind loop alive, and for every gliding
 * player in range plays surface sounds and, on servers without the mod, renders particles.
 */
public final class ClientFeelHandler {

  private static final double RANGE_SQ = 64.0 * 64.0;
  private static final RandomSource RANDOM = RandomSource.create();
  private static final double DRAFT_ENTRY_THRESHOLD = 0.25;

  private static float fovKick;
  private static GroundEffectWindSound wind;
  private static boolean wasDrafting;
  /** How far we left the view last tick, so a larger change means the player steered. NaN = idle. */
  private static float assistedYaw = Float.NaN;
  private static final float PLAYER_STEER_DEGREES = 0.75f;
  private static final double AIM_LOOKAHEAD_BLOCKS = 8.0;

  private ClientFeelHandler() {}

  public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(ClientFeelHandler::tick);
  }

  /** Smoothed FOV widening fraction, read by the FOV mixin. */
  public static float fovKick() {
    return fovKick;
  }

  private static void tick(Minecraft client) {
    LocalPlayer local = client.player;
    ClientLevel level = client.level;
    if (local == null || level == null) {
      fovKick = 0.0f;
      LocalGroundEffectState.clear();
      wasDrafting = false;
      // Only ever clear our own side here. The server tracker is cleared on the server thread by
      // SERVER_STOPPED; touching it from the client thread races the integrated server.
      WakeTrackers.client().clear();
      return;
    }

    SlipstreamConfig cfg = ServerConfigOverride.get();

    double target =
        cfg.fovKickEnabled && ServerConfigOverride.isBoostAllowed()
            ? GroundEffectMath.fovKickTarget(
                cfg.fovKickStrength,
                LocalGroundEffectState.proximity(),
                LocalGroundEffectState.speedRatio())
            : 0.0;
    fovKick = (float) GroundEffectMath.smooth(fovKick, target, 0.1);

    startWindIfNeeded(client, local, cfg);

    boolean localParticles =
        cfg.particlesEnabled
            && cfg.clientParticlesOnVanillaServers
            && !ServerConfigOverride.isActive()
            && !client.hasSingleplayerServer();
    LocalParticleSink sink = localParticles ? new LocalParticleSink(level, RANDOM) : null;
    Vec3 eye = local.position();

    for (Player p : level.players()) {
      if (!p.isFallFlying()) continue;
      if (p.position().distanceToSqr(eye) > RANGE_SQ) continue;

      Vec3 v = p.getDeltaMovement();
      double wakeSpeed = Math.sqrt(v.x * v.x + v.z * v.z);
      if (wakeSpeed >= cfg.effectSpeedThreshold * cfg.maxSpeedBlocksPerTick) {
        WakeTrackers.client()
            .record(
                p.getUUID(),
                p.position(),
                new Vec3(v.x / wakeSpeed, 0, v.z / wakeSpeed),
                wakeSpeed,
                local.tickCount,
                cfg);
      }

      // Cosmetic only: this setting hides other players' effects, it must never disable drafting,
      // so it is applied after the wake above has already been recorded.
      boolean remote = p != local;
      if (remote && !cfg.remotePlayerParticles) continue;

      GroundEffectSample s = ((GroundEffectSampler) p).slipstream$sample(cfg);
      if (s == null) continue;
      Vec3 pos = p.position();

      if (cfg.soundsEnabled) playSurfaceSounds(level, p, s, cfg, pos);
      if (sink != null) GroundEffectParticles.emit(s, cfg, p.tickCount, RANDOM, pos, sink);
    }

    WakeTrackers.client().prune(local.tickCount, cfg);

    // Find the strongest wake, not merely the first one that qualifies, so the wake drawn as
    // ridden is the same one the flight code actually applies forces from.
    UUID draftedLeader = null;
    DraftQuery draftedQuery = null;
    if (cfg.draftingEnabled) {
      for (UUID id : WakeTrackers.client().ids()) {
        if (id.equals(local.getUUID())) continue;
        DraftQuery q =
            DraftingMath.nearest(
                WakeTrackers.client().trailFor(id), local.position(), local.tickCount, cfg);
        if (q != null && (draftedQuery == null || q.strength() > draftedQuery.strength())) {
          draftedQuery = q;
          draftedLeader = id;
        }
      }
    }
    boolean drafting = draftedQuery != null && draftedQuery.strength() > DRAFT_ENTRY_THRESHOLD;

    if (cfg.particlesEnabled && cfg.draftParticlesEnabled) {
      drawWakes(level, cfg, local.tickCount, drafting ? draftedLeader : null);
      if (drafting) drawDraftStrength(level, local, draftedQuery);
    }

    if (drafting) {
      assistCamera(local, draftedQuery, cfg);
    } else {
      assistedYaw = Float.NaN;
    }

    if (drafting && !wasDrafting && cfg.soundsEnabled) {
      level.playLocalSound(
          local.getX(),
          local.getY(),
          local.getZ(),
          SoundEvents.PLAYER_ATTACK_SWEEP,
          SoundSource.PLAYERS,
          (float) (0.35 * cfg.soundVolume),
          1.6f,
          false);
    }
    wasDrafting = drafting;
  }

  /**
   * Draws every tracked wake. The one the local player is currently riding is drawn at full
   * density with no thinning, so entering and leaving a slipstream is unmistakable and the centre
   * line is visible to steer along. Every other wake stays sparse.
   *
   * @param draftedLeader the player whose wake the local player is drafting, or null for none
   */
  private static void drawWakes(
      ClientLevel level, SlipstreamConfig cfg, int now, UUID draftedLeader) {
    var idleLook = ModParticles.wakeTrail();
    var riddenLook = ModParticles.draftActive();
    if (idleLook == null || riddenLook == null) return;
    double lifetimeSeconds = cfg.wakeLifetimeTicks / 20.0;
    if (lifetimeSeconds <= 0.0) return;
    for (UUID id : WakeTrackers.client().ids()) {
      WakeTrail trail = WakeTrackers.client().trailFor(id);
      boolean riding = id.equals(draftedLeader);
      // Every third sample keeps an idle wake readable without flooding the particle budget. The
      // wake being ridden uses every sample, which is the difference you actually notice.
      int step = riding ? 1 : 3;
      double floor = riding ? 0.05 : 0.15;
      for (int i = 0; i < trail.size(); i += step) {
        WakeSample s = trail.sampleAt(i);
        double fade = 1.0 - Math.min(1.0, ((now - s.tick()) / 20.0) / lifetimeSeconds);
        if (fade <= floor) continue;
        // Drawing sparsely rather than faintly: an old wake thins instead of dimming. The ridden
        // wake skips that thinning entirely so it reads as solid for as long as it is usable.
        if (!riding && RANDOM.nextFloat() > fade) continue;
        level.addParticle(
            riding ? riddenLook : idleLook,
            s.position().x,
            s.position().y,
            s.position().z,
            0,
            0,
            0);
      }
    }
  }

  /**
   * Eases the player's view toward the wake ahead of them while drafting.
   *
   * <p>This exists because the pull moves your position but not your aim, and elytra flight is
   * steered by where you look. Without it, following a leader through a turn means the wake
   * direction changes while your view lags behind, the look divergence grows, and the pull
   * releases even though you never meant to leave. Assisting the view keeps that release honest
   * and makes the slipstream carry you rather than merely shove you sideways.
   *
   * <p>It aims at a point further along the wake rather than at the wake's heading, so climbs and
   * dives are followed as well as turns. It yields completely the moment the player turns their
   * own view, so it can never take the controls away.
   */
  private static void assistCamera(LocalPlayer local, DraftQuery query, SlipstreamConfig cfg) {
    if (!cfg.draftCameraAssist || cfg.draftCameraAssistStrength <= 0.0) {
      assistedYaw = Float.NaN;
      return;
    }
    // If the view moved by more than our own last nudge, the player is steering. Stand down for
    // this tick and resync, so the assist never wrestles the mouse.
    if (!Float.isNaN(assistedYaw)
        && Math.abs(Mth.wrapDegrees(local.getYRot() - assistedYaw)) > PLAYER_STEER_DEGREES) {
      assistedYaw = Float.NaN;
      return;
    }

    Vec3 target = query.point().add(query.wakeHeading().scale(AIM_LOOKAHEAD_BLOCKS));
    Vec3 toTarget = target.subtract(local.getEyePosition());
    double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
    if (horizontal < 1.0e-4) {
      assistedYaw = Float.NaN;
      return;
    }

    float targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
    float targetPitch = (float) Math.toDegrees(-Math.atan2(toTarget.y, horizontal));
    float rate = (float) (cfg.draftCameraAssistStrength * query.strength());

    float yaw = local.getYRot() + Mth.wrapDegrees(targetYaw - local.getYRot()) * rate;
    float pitch = local.getXRot() + (targetPitch - local.getXRot()) * rate;
    local.setYRot(yaw);
    local.setXRot(Mth.clamp(pitch, -90.0f, 90.0f));
    assistedYaw = yaw;
  }

  /**
   * Continuous feedback that the local player is drafting and how hard. Particles stream past the
   * player at a density set by draft strength, and when off the centre line a short arc of
   * particles points at it, so the correction to make is obvious rather than guessed.
   */
  private static void drawDraftStrength(ClientLevel level, LocalPlayer local, DraftQuery query) {
    var vortex = ModParticles.draftActive();
    if (vortex == null) return;
    double strength = query.strength();
    if (strength <= 0.0) return;

    Vec3 velocity = local.getDeltaMovement();
    int count = (int) Math.ceil(strength * 4.0);
    for (int i = 0; i < count; i++) {
      double side = (RANDOM.nextDouble() - 0.5) * 1.6;
      double vertical = (RANDOM.nextDouble() - 0.5) * 1.0;
      double ahead = 1.0 + RANDOM.nextDouble() * 2.0;
      // Spawned ahead and swept backwards, so they read as air moving past rather than exhaust.
      level.addParticle(
          vortex,
          local.getX() + velocity.x * ahead + side,
          local.getY() + 0.4 + vertical,
          local.getZ() + velocity.z * ahead + side,
          -velocity.x * 0.3,
          0.0,
          -velocity.z * 0.3);
    }

    // Off the centre line by enough to matter: show which way it is.
    double offset = query.lateralOffset();
    if (offset > 0.4) {
      Vec3 toCentre = query.toCentre();
      for (int i = 1; i <= 2; i++) {
        double along = offset * (i / 3.0);
        level.addParticle(
            vortex,
            local.getX() + toCentre.x * along,
            local.getY() + 0.4 + toCentre.y * along,
            local.getZ() + toCentre.z * along,
            0,
            0,
            0);
      }
    }
  }

  private static void playSurfaceSounds(
      ClientLevel level, Player p, GroundEffectSample s, SlipstreamConfig cfg, Vec3 pos) {
    if (s.isWater() && s.distToSurface() <= cfg.waterSprayHeightBlocks) {
      if (p.tickCount % 6 != 0) return;
      double waterProximity = 1.0 - s.distToSurface() / cfg.waterSprayHeightBlocks;
      float volume =
          (float)
              GroundEffectMath.wakeVolume(
                  waterProximity,
                  GroundEffectMath.speedRatio(s.hSpeed(), cfg.maxSpeedBlocksPerTick),
                  cfg.soundVolume);
      float pitch = 1.1f + RANDOM.nextFloat() * 0.2f;
      level.playLocalSound(
          pos.x - s.travelDir().x,
          s.surfaceY(),
          pos.z - s.travelDir().z,
          SoundEvents.PLAYER_SWIM,
          SoundSource.PLAYERS,
          volume,
          pitch,
          false);
    } else if (!s.isWater() && !s.surfaceBlock().isAir() && s.proximity() > 0.6) {
      if (p.tickCount % 4 != 0) return;
      float volume = (float) GroundEffectMath.skimVolume(s.proximity(), cfg.soundVolume);
      float pitch = 0.8f + RANDOM.nextFloat() * 0.2f;
      level.playLocalSound(
          pos.x,
          s.surfaceY(),
          pos.z,
          s.surfaceBlock().getSoundType().getStepSound(),
          SoundSource.BLOCKS,
          volume,
          pitch,
          false);
    }
  }

  private static void startWindIfNeeded(Minecraft client, LocalPlayer local, SlipstreamConfig cfg) {
    if (!cfg.soundsEnabled || !local.isFallFlying() || LocalGroundEffectState.proximity() <= 0.0) {
      return;
    }
    if (wind == null || !client.getSoundManager().isActive(wind)) {
      wind = new GroundEffectWindSound(local);
      client.getSoundManager().play(wind);
    }
  }
}
