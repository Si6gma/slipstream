package com.si6gma.slipstream;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

/** Sends particles to every client tracking the area. Same wire calls as the old mixin. */
public final class ServerParticleSink implements ParticleSink {

  private final ServerLevel level;

  public ServerParticleSink(ServerLevel level) {
    this.level = level;
  }

  @Override
  public void single(
      ParticleOptions type,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      double speed) {
    level.sendParticles(type, x, y, z, 0, vx, vy, vz, speed);
  }

  @Override
  public void burst(
      ParticleOptions type,
      double x,
      double y,
      double z,
      int count,
      double spreadX,
      double spreadY,
      double spreadZ,
      double speed) {
    level.sendParticles(type, x, y, z, count, spreadX, spreadY, spreadZ, speed);
  }
}
