package com.si6gma.slipstream;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Spawns particles directly into a level. On the client this renders them locally; on the server
 * {@code Level.addParticle} is a no op, so this sink is only ever constructed client side.
 */
public final class LocalParticleSink implements ParticleSink {

  private final Level level;
  private final RandomSource random;

  public LocalParticleSink(Level level, RandomSource random) {
    this.level = level;
    this.random = random;
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
    // Same as the vanilla client's count 0 path: velocity is scaled by speed.
    level.addParticle(type, x, y, z, vx * speed, vy * speed, vz * speed);
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
    ParticleSink.spread(
        random,
        x,
        y,
        z,
        count,
        spreadX,
        spreadY,
        spreadZ,
        speed,
        (px, py, pz, vx, vy, vz) -> level.addParticle(type, px, py, pz, vx, vy, vz));
  }
}
