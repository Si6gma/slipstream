package com.si6gma.slipstream;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Ground effect particle geometry. Side agnostic; the sink decides where particles go. */
public final class GroundEffectParticles {

  private GroundEffectParticles() {}

  public static void emit(
      GroundEffectSample s,
      SlipstreamConfig cfg,
      int tick,
      RandomSource random,
      Vec3 pos,
      ParticleSink sink) {
    if (!cfg.particlesEnabled) return;

    double hSpeed = s.hSpeed();
    double proximity = s.proximity();
    double distToSurface = s.distToSurface();
    double surfaceY = s.surfaceY();
    boolean isWater = s.isWater();
    Vec3 travelDir = s.travelDir();
    Vec3 right = s.right();
    double speedGate = cfg.effectSpeedThreshold * cfg.maxSpeedBlocksPerTick;

    // Vortex + contact burst: every 2 ticks (lightweight, keep dense trail)
    if (tick % 2 == 0) {
      var wingVortex = ModParticles.wingVortex();
      if (wingVortex != null && hSpeed >= speedGate) {
        double wingOffset = 1.2;
        double vortexOut = 0.12 * proximity;
        sink.single(
            wingVortex,
            pos.x + right.x * wingOffset,
            pos.y + 0.3,
            pos.z + right.z * wingOffset,
            right.x * vortexOut - travelDir.x * 0.03,
            0.01,
            right.z * vortexOut - travelDir.z * 0.03,
            0.0);
        sink.single(
            wingVortex,
            pos.x - right.x * wingOffset,
            pos.y + 0.3,
            pos.z - right.z * wingOffset,
            -right.x * vortexOut - travelDir.x * 0.03,
            0.01,
            -right.z * vortexOut - travelDir.z * 0.03,
            0.0);
      }

      if (isWater && distToSurface <= cfg.waterSprayHeightBlocks) {
        double waterProximity = 1.0 - (distToSurface / cfg.waterSprayHeightBlocks);
        int contactCount = 2 + (int) (waterProximity * 3);
        sink.burst(
            ParticleTypes.SPLASH,
            pos.x + right.x,
            surfaceY + 0.05,
            pos.z + right.z,
            contactCount,
            0.2,
            0.05,
            0.2,
            1.0);
        sink.burst(
            ParticleTypes.SPLASH,
            pos.x - right.x,
            surfaceY + 0.05,
            pos.z - right.z,
            contactCount,
            0.2,
            0.05,
            0.2,
            1.0);
      }
    }

    // Heavier spray/dust loops: every 3 ticks
    if (tick % 3 == 0) {
      if (isWater && distToSurface <= cfg.waterSprayHeightBlocks) {
        double waterProximity = 1.0 - (distToSurface / cfg.waterSprayHeightBlocks);

        // Wingtip spray arcs (capped at 8/side)
        int sprayCount = 2 + (int) (waterProximity * hSpeed * 6);
        for (int i = 0; i < Math.min(sprayCount, 8); i++) {
          double wingPos = 0.8 + random.nextDouble() * 0.7;
          double spawnJitter = (random.nextDouble() - 0.5) * 0.3;
          double outward = (0.3 + random.nextDouble() * 0.3) * waterProximity;
          double forward = hSpeed * (0.08 + random.nextDouble() * 0.08);
          double up = (0.9 + random.nextDouble() * 1.2) * waterProximity;
          sink.single(
              ParticleTypes.SPLASH,
              pos.x + right.x * wingPos + travelDir.x * spawnJitter,
              surfaceY + 0.05,
              pos.z + right.z * wingPos + travelDir.z * spawnJitter,
              right.x * outward + travelDir.x * forward,
              up,
              right.z * outward + travelDir.z * forward,
              1.0);
          sink.single(
              ParticleTypes.SPLASH,
              pos.x - right.x * wingPos + travelDir.x * spawnJitter,
              surfaceY + 0.05,
              pos.z - right.z * wingPos + travelDir.z * spawnJitter,
              -right.x * outward + travelDir.x * forward,
              up,
              -right.z * outward + travelDir.z * forward,
              1.0);
        }

        // Wake trail (capped at 5)
        int wakeCount = 1 + (int) (waterProximity * hSpeed * 3);
        for (int i = 0; i < Math.min(wakeCount, 5); i++) {
          double trailBack = 0.3 + random.nextDouble() * 2.0;
          double trailSide = (random.nextDouble() - 0.5) * 0.8;
          sink.single(
              ParticleTypes.SPLASH,
              pos.x - travelDir.x * trailBack + right.x * trailSide,
              surfaceY + 0.05,
              pos.z - travelDir.z * trailBack + right.z * trailSide,
              (random.nextDouble() - 0.5) * 0.04,
              0.08 + random.nextDouble() * 0.08,
              (random.nextDouble() - 0.5) * 0.04,
              1.0);
        }

        // Fine mist
        if (waterProximity > 0.5 && random.nextInt(3) == 0) {
          sink.single(
              ParticleTypes.FALLING_WATER,
              pos.x
                  + travelDir.x * random.nextDouble() * 1.5
                  + right.x * (random.nextDouble() - 0.5) * 1.5,
              surfaceY + 0.15 + random.nextDouble() * 0.4,
              pos.z
                  + travelDir.z * random.nextDouble() * 1.5
                  + right.z * (random.nextDouble() - 0.5) * 1.5,
              travelDir.x * 0.02,
              0.02,
              travelDir.z * 0.02,
              1.0);
        }

      } else if (!isWater && !s.surfaceBlock().isAir()) {
        // Ground dust (capped at 4)
        int dustCount = 1 + (int) (proximity * hSpeed * 1.5);
        var dustParticle = new BlockParticleOption(ParticleTypes.BLOCK, s.surfaceBlock());
        for (int i = 0; i < Math.min(dustCount, 4); i++) {
          double scatterX = (random.nextDouble() - 0.5) * 2.5;
          double scatterZ = (random.nextDouble() - 0.5) * 2.5;
          sink.single(
              dustParticle,
              pos.x + scatterX,
              surfaceY + 0.1,
              pos.z + scatterZ,
              scatterX * 0.04,
              0.05 + random.nextDouble() * 0.08,
              scatterZ * 0.04,
              0.0);
        }

        // POOF puffs (capped at 2, only when close)
        if (proximity > 0.3) {
          int puffCount = 1 + (int) (proximity * hSpeed * 0.5);
          for (int i = 0; i < Math.min(puffCount, 2); i++) {
            sink.single(
                ParticleTypes.POOF,
                pos.x
                    - travelDir.x * (0.5 + random.nextDouble() * 1.5)
                    + right.x * (random.nextDouble() - 0.5),
                surfaceY + 0.2 + random.nextDouble() * 0.3,
                pos.z
                    - travelDir.z * (0.5 + random.nextDouble() * 1.5)
                    + right.z * (random.nextDouble() - 0.5),
                (random.nextDouble() - 0.5) * 0.02,
                0.03 + random.nextDouble() * 0.03,
                (random.nextDouble() - 0.5) * 0.02,
                1.0);
          }
        }
      }
    }
  }
}
