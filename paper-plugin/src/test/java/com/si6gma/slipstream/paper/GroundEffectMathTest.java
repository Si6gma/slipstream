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

  // The server authoritative forces. This class is a hand kept mirror of the Fabric side, so
  // these pin the behaviour that has to match rather than merely that it compiles.

  @Test
  void boostDelta_scalesWithProximity() {
    assertEquals(0.005, GroundEffectMath.boostDelta(1.0, -0.1, 1.0, 0.005, 1.5), 1e-9);
    assertEquals(0.0025, GroundEffectMath.boostDelta(1.0, -0.1, 0.5, 0.005, 1.5), 1e-9);
    assertEquals(0.0, GroundEffectMath.boostDelta(1.0, -0.1, 0.0, 0.005, 1.5), 1e-9);
  }

  @Test
  void boostDelta_refusesAtOrAboveTheCeiling() {
    assertEquals(0.0, GroundEffectMath.boostDelta(1.5, -0.1, 1.0, 0.005, 1.5), 1e-9);
    assertEquals(0.0, GroundEffectMath.boostDelta(2.0, -0.1, 1.0, 0.005, 1.5), 1e-9);
  }

  @Test
  void boostDelta_refusesWhileClimbing() {
    // Ground effect accelerates level or descending flight only. The deadband absorbs the
    // antigravity term so lift nudging ySpeed just past zero cannot toggle the gate every tick.
    assertEquals(0.0, GroundEffectMath.boostDelta(1.0, 0.2, 1.0, 0.005, 1.5), 1e-9);
    assertTrue(GroundEffectMath.boostDelta(1.0, 0.04, 1.0, 0.005, 1.5) > 0.0);
  }

  @Test
  void liftForce_pushesUpWhenSinking() {
    assertTrue(GroundEffectMath.liftForce(-0.2, 0.0, 1.0, 0.6, 1.5, 1.5) > 0.0);
  }

  @Test
  void liftForce_pushesDownWhenClimbing() {
    assertTrue(GroundEffectMath.liftForce(0.2, 0.0, 1.0, 0.6, 1.5, 1.5) < 0.0);
  }

  @Test
  void liftForce_neverOvershootsLevel() {
    for (double ySpeed = -1.0; ySpeed <= 1.0; ySpeed += 0.05) {
      double lift = GroundEffectMath.liftForce(ySpeed, 0.0, 1.0, 0.6, 1.5, 1.5);
      double after = ySpeed + lift;
      if (ySpeed < 0) {
        assertTrue(after <= 1e-9, "sinking overshot to " + after);
      } else if (ySpeed > 0) {
        assertTrue(after >= -1e-9, "climbing overshot to " + after);
      }
    }
  }

  @Test
  void liftForce_disengagesOutsideThePitchWindow() {
    // Past thirty degrees the player is deliberately climbing or diving and is left alone.
    assertEquals(0.0, GroundEffectMath.liftForce(-0.2, 31.0, 1.0, 0.6, 1.5, 1.5), 1e-9);
    assertEquals(0.0, GroundEffectMath.liftForce(-0.2, -31.0, 1.0, 0.6, 1.5, 1.5), 1e-9);
    assertTrue(GroundEffectMath.liftForce(-0.2, 29.0, 1.0, 0.6, 1.5, 1.5) != 0.0);
  }

  @Test
  void liftForce_isZeroWithoutStrengthOrCeiling() {
    assertEquals(0.0, GroundEffectMath.liftForce(-0.2, 0.0, 1.0, 0.0, 1.5, 1.5), 1e-9);
    assertEquals(0.0, GroundEffectMath.liftForce(-0.2, 0.0, 1.0, 0.6, 1.5, 0.0), 1e-9);
  }

  @Test
  void speedRatio_clampsToTheUnitRange() {
    assertEquals(0.0, GroundEffectMath.speedRatio(0.0, 1.5), 1e-9);
    assertEquals(1.0, GroundEffectMath.speedRatio(1.5, 1.5), 1e-9);
    assertEquals(1.0, GroundEffectMath.speedRatio(9.0, 1.5), 1e-9);
    assertEquals(0.0, GroundEffectMath.speedRatio(1.0, 0.0), 1e-9);
  }
}
