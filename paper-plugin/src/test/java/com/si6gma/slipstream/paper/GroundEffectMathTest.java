package com.si6gma.slipstream.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GroundEffectMathTest {

  @Test
  void proximity_atSurface_isOne() {
    assertEquals(1.0, GroundEffectMath.proximity(0, 20), 1e-9);
  }

  @Test
  void proximity_atMaxHeight_isZero() {
    assertEquals(0.0, GroundEffectMath.proximity(20, 20), 1e-9);
  }

  @Test
  void proximity_withinBuffer_isOne() {
    // 0–3 blocks above surface should all return full proximity (flat zone)
    assertEquals(1.0, GroundEffectMath.proximity(0, 20), 1e-9);
    assertEquals(1.0, GroundEffectMath.proximity(1, 20), 1e-9);
    assertEquals(1.0, GroundEffectMath.proximity(3, 20), 1e-9);
  }

  @Test
  void proximity_isMonotonicallyDecreasing() {
    double prev = GroundEffectMath.proximity(0, 20);
    for (int d = 1; d <= 20; d++) {
      double curr = GroundEffectMath.proximity(d, 20);
      assertTrue(curr <= prev, "proximity should decrease as distance increases");
      prev = curr;
    }
  }
}
