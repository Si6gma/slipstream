package com.si6gma.slipstream.client;

import com.si6gma.slipstream.GroundEffectMath;
import com.si6gma.slipstream.GroundEffectParticles;
import com.si6gma.slipstream.GroundEffectSample;
import com.si6gma.slipstream.GroundEffectSampler;
import com.si6gma.slipstream.LocalGroundEffectState;
import com.si6gma.slipstream.LocalParticleSink;
import com.si6gma.slipstream.SlipstreamConfig;
import com.si6gma.slipstream.network.ServerConfigOverride;
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

  private static float fovKick;

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
      boolean remote = p != local;
      if (remote && !cfg.remotePlayerParticles) continue;
      if (p.position().distanceToSqr(eye) > RANGE_SQ) continue;

      GroundEffectSample s = ((GroundEffectSampler) p).slipstream$sample(cfg);
      if (s == null) continue;
      Vec3 pos = p.position();

      if (cfg.soundsEnabled) playSurfaceSounds(level, p, s, cfg, pos);
      if (sink != null) GroundEffectParticles.emit(s, cfg, p.tickCount, RANDOM, pos, sink);
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

  // Filled in by the wind sound task.
  private static void startWindIfNeeded(Minecraft client, LocalPlayer local, SlipstreamConfig cfg) {}
}
