package com.si6gma.slipstream.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.si6gma.slipstream.SlipstreamConfig;
import org.junit.jupiter.api.Test;

class DraftingForcesTest {

  // draftCap() and boostDelta()

  @Test
  void draftCap_isTheOvertakeMarginAboveNormalMaxSpeed() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(1.5 * 1.15, DraftingMath.draftCap(cfg), 1e-9);
    assertTrue(
        DraftingMath.draftCap(cfg) > cfg.maxSpeedBlocksPerTick, "overtaking must be possible");
  }

  @Test
  void boostDelta_isZeroWithoutStrength() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(0.0, DraftingMath.boostDelta(1.0, 0.0, cfg), 1e-9);
  }

  @Test
  void boostDelta_scalesWithStrength() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(0.008, DraftingMath.boostDelta(0.5, 1.0, cfg), 1e-9);
    assertEquals(0.004, DraftingMath.boostDelta(0.5, 0.5, cfg), 1e-9);
  }

  @Test
  void boostDelta_neverExceedsTheDraftCap() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double cap = DraftingMath.draftCap(cfg);
    double speed = cap - 0.001;
    double delta = DraftingMath.boostDelta(speed, 1.0, cfg);
    assertTrue(speed + delta <= cap + 1e-9, "must not overshoot the cap");
    assertEquals(0.0, DraftingMath.boostDelta(cap, 1.0, cfg), 1e-9);
    assertEquals(0.0, DraftingMath.boostDelta(cap + 5.0, 1.0, cfg), 1e-9);
  }

  @Test
  void boostDelta_isZeroWhenDraftingDisabled() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.draftingEnabled = false;
    assertEquals(0.0, DraftingMath.boostDelta(0.5, 1.0, cfg), 1e-9);
  }

  // pullForce()

  @Test
  void pullForce_drivesLateralErrorTowardZero() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double pull = DraftingMath.pullForce(2.0, 0.0, 1.0, cfg);
    assertTrue(pull > 0.0);
    assertEquals(2.0 * 0.25, pull, 1e-9);
  }

  @Test
  void pullForce_neverOvershootsTheCentreline() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.draftPullStrength = 1.0;
    for (double err : new double[] {0.01, 0.5, 2.0, 10.0}) {
      assertTrue(DraftingMath.pullForce(err, 0.0, 1.0, cfg) <= err + 1e-9);
    }
  }

  @Test
  void pullForce_convergesWithoutOscillating() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double err = 3.0;
    for (int i = 0; i < 200; i++) {
      double step = DraftingMath.pullForce(err, 0.0, 1.0, cfg);
      assertTrue(step >= 0.0, "pull never pushes away from the centreline");
      err -= step;
      assertTrue(err >= -1e-9, "error never crosses zero");
    }
    assertTrue(err < 0.01, "should have converged, got " + err);
  }

  @Test
  void pullForce_releasesPastTheReleaseAngle() {
    SlipstreamConfig cfg = new SlipstreamConfig(); // release at 35 degrees
    assertEquals(0.0, DraftingMath.pullForce(2.0, 35.0, 1.0, cfg), 1e-9);
    assertEquals(0.0, DraftingMath.pullForce(2.0, 90.0, 1.0, cfg), 1e-9);
    assertEquals(0.0, DraftingMath.pullForce(2.0, -90.0, 1.0, cfg), 1e-9);
  }

  @Test
  void pullForce_fadesSmoothlyTowardTheReleaseAngle() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double full = DraftingMath.pullForce(2.0, 0.0, 1.0, cfg);
    double half = DraftingMath.pullForce(2.0, 17.5, 1.0, cfg);
    assertEquals(full * 0.5, half, 1e-9);
    assertEquals(half, DraftingMath.pullForce(2.0, -17.5, 1.0, cfg), 1e-9);
  }

  @Test
  void pullForce_isZeroWithoutStrengthOrWhenDisabled() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(0.0, DraftingMath.pullForce(2.0, 0.0, 0.0, cfg), 1e-9);
    cfg.draftingEnabled = false;
    assertEquals(0.0, DraftingMath.pullForce(2.0, 0.0, 1.0, cfg), 1e-9);
  }

  // leaderBonus()

  @Test
  void leaderBonus_scalesWithDraftersAndCaps() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double per = 0.15 * 0.008;
    assertEquals(0.0, DraftingMath.leaderBonus(0, cfg), 1e-12);
    assertEquals(per, DraftingMath.leaderBonus(1, cfg), 1e-12);
    assertEquals(per * 3, DraftingMath.leaderBonus(3, cfg), 1e-12);
    assertEquals(per * 3, DraftingMath.leaderBonus(50, cfg), 1e-12);
  }

  @Test
  void leaderBonus_isSmallerThanDrafting() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertTrue(
        DraftingMath.leaderBonus(3, cfg) < DraftingMath.boostDelta(0.5, 1.0, cfg),
        "leading must never beat drafting");
  }

  // Chain behaviour

  @Test
  void chainOfFive_convergesOnTheCapRatherThanDiverging() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double cap = DraftingMath.draftCap(cfg);
    double[] speed = {1.0, 1.0, 1.0, 1.0, 1.0};
    for (int tick = 0; tick < 2000; tick++) {
      for (int i = 1; i < speed.length; i++) {
        speed[i] += DraftingMath.boostDelta(speed[i], 1.0, cfg);
      }
      speed[0] += DraftingMath.leaderBonus(1, cfg);
      speed[0] = Math.min(speed[0], cfg.maxSpeedBlocksPerTick);
    }
    for (int i = 1; i < speed.length; i++) {
      assertTrue(speed[i] <= cap + 1e-9, "follower " + i + " exceeded the cap: " + speed[i]);
    }
    assertTrue(speed[0] <= cfg.maxSpeedBlocksPerTick + 1e-9, "leader keeps the normal cap");
  }
}
