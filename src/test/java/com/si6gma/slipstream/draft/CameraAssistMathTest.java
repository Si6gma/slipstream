package com.si6gma.slipstream.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The rule that decides whether the camera assist is helping or wrestling. */
class CameraAssistMathTest {

  private static final double RECOVERY = CameraAssistMath.AUTHORITY_RECOVERY_PER_TICK;

  // playerInputDeg()

  @Test
  void playerInputDeg_isZeroWhenTheViewSatWhereTheAssistLeftIt() {
    assertEquals(0.0, CameraAssistMath.playerInputDeg(90.0, 90.0, -10.0, -10.0), 1e-9);
  }

  @Test
  void playerInputDeg_seesPitchAndNotOnlyYaw() {
    // The whole complaint about the old assist: pitch was never watched, so looking down while
    // drafting was fought every tick with no way out.
    double pitchOnly = CameraAssistMath.playerInputDeg(90.0, 90.0, -20.0, -14.0);
    assertEquals(6.0, pitchOnly, 1e-9, "moving only pitch must register as steering");
    assertTrue(
        CameraAssistMath.yieldTarget(pitchOnly) <= 0.0, "looking down must release the assist");
  }

  @Test
  void playerInputDeg_combinesAxesAsADiagonalNotASum() {
    double both = CameraAssistMath.playerInputDeg(93.0, 90.0, -14.0, -10.0);
    assertEquals(5.0, both, 1e-9, "3 and 4 degrees is a 5 degree flick, not a 7 degree one");
  }

  @Test
  void playerInputDeg_wrapsAcrossTheYawSeam() {
    // Turning past due south must read as two degrees of steering, not three hundred and fifty.
    assertEquals(2.0, CameraAssistMath.playerInputDeg(-179.0, 179.0, 0.0, 0.0), 1e-9);
    assertEquals(2.0, CameraAssistMath.playerInputDeg(179.0, -179.0, 0.0, 0.0), 1e-9);
  }

  // yieldTarget()

  @Test
  void yieldTarget_keepsFullAuthorityBelowTheDeadzone() {
    assertEquals(1.0, CameraAssistMath.yieldTarget(0.0), 1e-9);
    assertEquals(1.0, CameraAssistMath.yieldTarget(CameraAssistMath.INPUT_DEADZONE_DEG), 1e-9);
  }

  @Test
  void yieldTarget_surrendersEverythingAtAFullSteer() {
    assertEquals(0.0, CameraAssistMath.yieldTarget(CameraAssistMath.FULL_YIELD_DEG), 1e-9);
    assertEquals(0.0, CameraAssistMath.yieldTarget(90.0), 1e-9);
  }

  @Test
  void yieldTarget_easesOffRatherThanSwitchingOff() {
    // The point of item 5. A nudge must cost the assist some authority, not all of it, so there
    // is a handover rather than the old one tick stand down and full strength snap back.
    double midway = (CameraAssistMath.INPUT_DEADZONE_DEG + CameraAssistMath.FULL_YIELD_DEG) / 2.0;
    double held = CameraAssistMath.yieldTarget(midway);
    assertTrue(held > 0.0 && held < 1.0, "a half steer must be a partial handover, got " + held);
    assertEquals(0.5, held, 1e-9, "the ramp between deadzone and full yield is linear");
  }

  @Test
  void yieldTarget_neverIncreasesWithInput() {
    double prev = Double.MAX_VALUE;
    for (double input = 0.0; input <= 8.0; input += 0.1) {
      double v = CameraAssistMath.yieldTarget(input);
      assertTrue(v <= prev + 1e-12, "authority rose at input " + input);
      assertTrue(v >= 0.0 && v <= 1.0, "authority left [0, 1] at input " + input);
      prev = v;
    }
  }

  // nextAuthority()

  @Test
  void nextAuthority_surrendersImmediately() {
    // The hand has to be obeyed on the tick it moves, so there is no easing on the way down.
    assertEquals(0.2, CameraAssistMath.nextAuthority(1.0, 0.2, RECOVERY), 1e-9);
    assertEquals(0.0, CameraAssistMath.nextAuthority(1.0, 0.0, RECOVERY), 1e-9);
  }

  @Test
  void nextAuthority_returnsGraduallyNotAtOnce() {
    double first = CameraAssistMath.nextAuthority(0.0, 1.0, RECOVERY);
    assertEquals(RECOVERY, first, 1e-9, "authority must creep back, not snap back");
    assertTrue(first < 1.0);
  }

  @Test
  void nextAuthority_staysInRange() {
    assertEquals(1.0, CameraAssistMath.nextAuthority(1.0, 1.0, RECOVERY), 1e-9);
    assertEquals(0.0, CameraAssistMath.nextAuthority(-5.0, -5.0, RECOVERY), 1e-9);
    assertTrue(CameraAssistMath.nextAuthority(0.99, 1.0, 1.0) <= 1.0);
  }

  @Test
  void nextAuthority_ignoresNonFiniteState() {
    assertEquals(0.0, CameraAssistMath.nextAuthority(Double.NaN, 0.0, RECOVERY), 1e-9);
  }

  // assistRate()

  @Test
  void assistRate_isZeroWithoutAuthority() {
    assertEquals(0.0, CameraAssistMath.assistRate(0.5, 1.0, 0.0), 1e-9);
  }

  @Test
  void assistRate_scalesWithAuthorityAndDraftStrength() {
    assertEquals(0.25, CameraAssistMath.assistRate(0.5, 1.0, 0.5), 1e-9);
    assertEquals(0.125, CameraAssistMath.assistRate(0.5, 0.5, 0.5), 1e-9);
  }

  // Behaviour over time

  @Test
  void holdingTheMouseKeepsTheAssistOffInsteadOfStuttering() {
    // The old assist stood down for one tick, then applied at full strength on the next, so a
    // player holding a turn was nudged every other tick. Authority must stay at zero for as long
    // as the hand keeps moving.
    double authority = 1.0;
    for (int tick = 0; tick < 20; tick++) {
      double input = CameraAssistMath.playerInputDeg(95.0, 90.0, 0.0, 0.0);
      double target = CameraAssistMath.yieldTarget(input);
      authority = CameraAssistMath.nextAuthority(authority, target, RECOVERY);
      assertEquals(0.0, authority, 1e-9, "assist regained authority mid steer at tick " + tick);
      assertEquals(0.0, CameraAssistMath.assistRate(0.5, 1.0, authority), 1e-9);
    }
  }

  @Test
  void releasingTheMouseReturnsTheAssistOverAboutASecond() {
    double authority = 0.0;
    int ticks = 0;
    while (authority < 1.0 && ticks < 200) {
      authority = CameraAssistMath.nextAuthority(authority, 1.0, RECOVERY);
      ticks++;
    }
    assertEquals(1.0, authority, 1e-9);
    assertTrue(ticks > 10, "authority came back too abruptly: " + ticks + " ticks");
    assertTrue(ticks <= 40, "authority took too long to come back: " + ticks + " ticks");
  }
}
