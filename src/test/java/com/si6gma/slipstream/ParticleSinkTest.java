package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class ParticleSinkTest {

  @Test
  void spread_emitsCountParticlesAroundOrigin() {
    List<double[]> out = new ArrayList<>();
    ParticleSink.spread(
        RandomSource.create(42L),
        10.0,
        64.0,
        -3.0,
        5,
        0.2,
        0.05,
        0.2,
        1.0,
        (x, y, z, vx, vy, vz) -> out.add(new double[] {x, y, z, vx, vy, vz}));
    assertEquals(5, out.size());
    for (double[] p : out) {
      // Gaussian spread: essentially always within 5 sigma of the origin.
      assertTrue(Math.abs(p[0] - 10.0) < 1.0, "x offset too large: " + p[0]);
      assertTrue(Math.abs(p[1] - 64.0) < 0.25, "y offset too large: " + p[1]);
      assertTrue(Math.abs(p[2] + 3.0) < 1.0, "z offset too large: " + p[2]);
    }
  }

  @Test
  void spread_zeroSpeedGivesZeroVelocity() {
    List<double[]> out = new ArrayList<>();
    ParticleSink.spread(
        RandomSource.create(1L),
        0,
        0,
        0,
        3,
        1.0,
        1.0,
        1.0,
        0.0,
        (x, y, z, vx, vy, vz) -> out.add(new double[] {vx, vy, vz}));
    assertEquals(3, out.size());
    for (double[] v : out) {
      assertEquals(0.0, v[0], 1e-12);
      assertEquals(0.0, v[1], 1e-12);
      assertEquals(0.0, v[2], 1e-12);
    }
  }
}
