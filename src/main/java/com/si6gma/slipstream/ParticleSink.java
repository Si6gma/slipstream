package com.si6gma.slipstream;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;

/** Destination for ground effect particles. One implementation per side. */
public interface ParticleSink {

  /**
   * One particle. Matches {@code ServerLevel.sendParticles} with count 0: the client spawns it with
   * velocity {@code (vx * speed, vy * speed, vz * speed)}.
   */
  void single(
      ParticleOptions type,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      double speed);

  /**
   * A counted burst, matching the semantics of {@code ServerLevel.sendParticles} with a count
   * above zero: each particle is offset by a gaussian scaled by the spread on each axis and given
   * a gaussian velocity scaled by speed.
   */
  void burst(
      ParticleOptions type,
      double x,
      double y,
      double z,
      int count,
      double spreadX,
      double spreadY,
      double spreadZ,
      double speed);

  /** Receives one expanded burst particle. */
  @FunctionalInterface
  interface Spread {
    void accept(double x, double y, double z, double vx, double vy, double vz);
  }

  /** Expands a counted burst the way the vanilla client does for a counted particle packet. */
  static void spread(
      RandomSource random,
      double x,
      double y,
      double z,
      int count,
      double spreadX,
      double spreadY,
      double spreadZ,
      double speed,
      Spread out) {
    for (int i = 0; i < count; i++) {
      out.accept(
          x + random.nextGaussian() * spreadX,
          y + random.nextGaussian() * spreadY,
          z + random.nextGaussian() * spreadZ,
          random.nextGaussian() * speed,
          random.nextGaussian() * speed,
          random.nextGaussian() * speed);
    }
  }
}
