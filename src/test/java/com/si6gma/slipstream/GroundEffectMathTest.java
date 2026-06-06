package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GroundEffectMathTest {

  // proximity()

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
    // 0–3 blocks above surface should all return full proximity
    assertEquals(1.0, GroundEffectMath.proximity(0, 20), 1e-9);
    assertEquals(1.0, GroundEffectMath.proximity(1, 20), 1e-9);
    assertEquals(1.0, GroundEffectMath.proximity(3, 20), 1e-9);
  }

  @Test
  void proximity_aboveBuffer_fallsOff() {
    // Just above the buffer the value should be less than 1 and greater than 0
    double p = GroundEffectMath.proximity(4, 20);
    assertTrue(p > 0.0 && p < 1.0, "proximity just above buffer should be between 0 and 1");
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

  // liftForce()

  @Test
  void liftForce_whenHorizontal_isZero() {
    assertEquals(0.0, GroundEffectMath.liftForce(0.0, 1.5, 1.0, 0.6), 1e-9);
  }

  @Test
  void liftForce_whenDescending_isPositive() {
    // ySpeed=-0.05, hSpeed=1.5 → pitch ≈ -1.9°, well inside the window
    assertTrue(GroundEffectMath.liftForce(-0.05, 1.5, 1.0, 0.6) > 0);
  }

  @Test
  void liftForce_whenAscending_isNegative() {
    // Bidirectional: pulls down when drifting up inside the window
    assertTrue(GroundEffectMath.liftForce(0.05, 1.5, 1.0, 0.6) < 0);
  }

  @Test
  void liftForce_neverOvershoots() {
    // Correction must not push ySpeed past zero in either direction
    for (double ySpeed : new double[] {-0.01, -0.05, -0.1, 0.01, 0.05, 0.1}) {
      double lift = GroundEffectMath.liftForce(ySpeed, 1.5, 1.0, 0.6);
      assertTrue(
          Math.abs(lift) <= Math.abs(ySpeed),
          "Lift must not overshoot ySpeed=0 at ySpeed=" + ySpeed);
    }
  }

  @Test
  void liftForce_outsideAngleWindow_isZero() {
    // ySpeed=-0.5, hSpeed=0.5 → pitch ≈ -45°, outside the ±30° window
    assertEquals(0.0, GroundEffectMath.liftForce(-0.5, 0.5, 1.0, 0.6), 1e-9);
    assertEquals(0.0, GroundEffectMath.liftForce(0.5, 0.5, 1.0, 0.6), 1e-9);
  }

  @Test
  void liftForce_scalesWithProximity() {
    double liftLow = GroundEffectMath.liftForce(-0.05, 1.5, 0.5, 0.6);
    double liftHigh = GroundEffectMath.liftForce(-0.05, 1.5, 1.0, 0.6);
    assertTrue(liftHigh > liftLow, "More proximity should produce more lift");
  }

  @Test
  void liftForce_antiGravityHoldsLevelFlight() {
    // Simulates one tick: vanilla applies ~-0.02 gravity, then our lift runs.
    // Starting from ySpeed=0, after gravity ySpeed=-0.02; lift should bring it back to 0.
    double afterGravity = -0.02;
    double lift = GroundEffectMath.liftForce(afterGravity, 1.5, 1.0, 0.6);
    double result = afterGravity + lift;
    assertEquals(
        0.0,
        result,
        1e-9,
        "Anti-gravity should fully cancel elytra's residual gravity at level flight");
  }

  // boostDelta()

  @Test
  void boostDelta_whenAtMaxSpeed_isZero() {
    assertEquals(0.0, GroundEffectMath.boostDelta(3.0, 0.0, 1.0, 0.001, 3.0), 1e-9);
  }

  @Test
  void boostDelta_whenAboveMaxSpeed_isZero() {
    assertEquals(0.0, GroundEffectMath.boostDelta(4.0, 0.0, 1.0, 0.001, 3.0), 1e-9);
  }

  @Test
  void boostDelta_whenAscendingBeyondDeadBand_isZero() {
    assertEquals(0.0, GroundEffectMath.boostDelta(1.0, 0.06, 1.0, 0.001, 3.0), 1e-9);
  }

  @Test
  void boostDelta_withinDeadBand_isNonZero() {
    assertTrue(GroundEffectMath.boostDelta(1.0, 0.04, 1.0, 0.001, 3.0) > 0.0);
  }

  @Test
  void boostDelta_scalesWithProximity() {
    double low = GroundEffectMath.boostDelta(0, 0.0, 0.5, 0.001, 3.0);
    double high = GroundEffectMath.boostDelta(0, 0.0, 1.0, 0.001, 3.0);
    assertEquals(2 * low, high, 1e-9);
  }

  @Test
  void boostDelta_isNonNegative() {
    assertTrue(GroundEffectMath.boostDelta(0, 0.0, 0.8, 0.001, 3.0) >= 0);
  }
}
