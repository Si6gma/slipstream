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
import com.si6gma.slipstream.network.ServerConfigOverride;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    if (cfg.particlesEnabled && cfg.draftParticlesEnabled) {
      drawWakes(level, cfg, local.tickCount);
    }

    boolean drafting = false;
    if (cfg.draftingEnabled) {
      for (UUID id : WakeTrackers.client().ids()) {
        if (id.equals(local.getUUID())) continue;
        DraftQuery q =
            DraftingMath.nearest(
                WakeTrackers.client().trailFor(id), local.position(), local.tickCount, cfg);
        if (q != null && q.strength() > DRAFT_ENTRY_THRESHOLD) {
          drafting = true;
          break;
        }
      }
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

  private static void drawWakes(ClientLevel level, SlipstreamConfig cfg, int now) {
    var vortex = ModParticles.wingVortex();
    if (vortex == null) return;
    double lifetimeSeconds = cfg.wakeLifetimeTicks / 20.0;
    if (lifetimeSeconds <= 0.0) return;
    for (UUID id : WakeTrackers.client().ids()) {
      List<WakeSample> samples = WakeTrackers.client().trailFor(id).samples();
      // Every third sample keeps the wake readable without flooding the particle budget.
      for (int i = 0; i < samples.size(); i += 3) {
        WakeSample s = samples.get(i);
        double fade = 1.0 - Math.min(1.0, ((now - s.tick()) / 20.0) / lifetimeSeconds);
        if (fade <= 0.15) continue;
        // Drawing sparsely rather than faintly: an old wake thins instead of dimming.
        if (RANDOM.nextFloat() > fade) continue;
        level.addParticle(vortex, s.position().x, s.position().y, s.position().z, 0, 0, 0);
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
