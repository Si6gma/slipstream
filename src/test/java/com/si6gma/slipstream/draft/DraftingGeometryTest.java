package com.si6gma.slipstream.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.si6gma.slipstream.SlipstreamConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DraftingGeometryTest {

  /** A leader flying east along y=70, z=0, one sample every 2 ticks, newest at x=10, tick 110. */
  private static WakeTrail straightEastTrail() {
    WakeTrail trail = new WakeTrail();
    for (int i = 0; i <= 5; i++) {
      trail.record(new Vec3(i * 2, 70, 0), 1.0, 100 + i * 2);
    }
    return trail;
  }

  // wakeRadius()

  @Test
  void wakeRadius_growsWithAge() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(1.5, DraftingMath.wakeRadius(0.0, cfg), 1e-9);
    assertEquals(1.5 + 1.2, DraftingMath.wakeRadius(1.0, cfg), 1e-9);
    assertTrue(DraftingMath.wakeRadius(2.0, cfg) > DraftingMath.wakeRadius(1.0, cfg));
  }

  // strength()

  @Test
  void strength_isOneAtCentreOfFreshWake() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double r = DraftingMath.wakeRadius(0.0, cfg);
    assertEquals(1.0, DraftingMath.strength(0.0, 0.0, r, cfg), 1e-9);
  }

  @Test
  void strength_isZeroAtAndBeyondTheEdge() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double r = DraftingMath.wakeRadius(0.0, cfg);
    assertEquals(0.0, DraftingMath.strength(0.0, r, r, cfg), 1e-9);
    assertEquals(0.0, DraftingMath.strength(0.0, r * 2, r, cfg), 1e-9);
  }

  @Test
  void strength_isZeroBeyondLifetime() {
    SlipstreamConfig cfg = new SlipstreamConfig(); // 60 ticks = 3.0 seconds
    double r = DraftingMath.wakeRadius(3.0, cfg);
    assertEquals(0.0, DraftingMath.strength(3.0, 0.0, r, cfg), 1e-9);
    assertEquals(0.0, DraftingMath.strength(9.0, 0.0, r, cfg), 1e-9);
  }

  @Test
  void strength_decreasesWithAgeAndWithOffset() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double prev = Double.MAX_VALUE;
    for (double age = 0.0; age <= 3.0; age += 0.25) {
      double s = DraftingMath.strength(age, 0.0, DraftingMath.wakeRadius(age, cfg), cfg);
      assertTrue(s <= prev, "strength should not increase with age");
      prev = s;
    }
    double r = DraftingMath.wakeRadius(0.0, cfg);
    prev = Double.MAX_VALUE;
    for (double off = 0.0; off <= r; off += 0.1) {
      double s = DraftingMath.strength(0.0, off, r, cfg);
      assertTrue(s <= prev, "strength should not increase with offset");
      prev = s;
    }
  }

  // nearest()

  @Test
  void nearest_returnsNullForEmptyOrSingleSampleTrail() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertNull(DraftingMath.nearest(new WakeTrail(), Vec3.ZERO, 110, cfg));
    WakeTrail one = new WakeTrail();
    one.record(Vec3.ZERO, 1.0, 100);
    assertNull(DraftingMath.nearest(one, new Vec3(-1, 70, 0), 110, cfg));
  }

  @Test
  void nearest_findsCentrelineDirectlyBehindLeader() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    // Follower sits on the path at x=6, which is a recorded sample position.
    DraftQuery q = DraftingMath.nearest(straightEastTrail(), new Vec3(6, 70, 0), 110, cfg);
    assertNotNull(q);
    assertEquals(0.0, q.lateralOffset(), 1e-6);
    assertEquals(6.0, q.point().x, 1e-6);
    assertTrue(q.strength() > 0.0);
  }

  @Test
  void nearest_measuresLateralOffsetOffThePath() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(straightEastTrail(), new Vec3(6, 70, 1.0), 110, cfg);
    assertNotNull(q);
    assertEquals(1.0, q.lateralOffset(), 1e-6);
    // toCentre points from the follower back onto the path, so toward -z here.
    assertEquals(-1.0, q.toCentre().z, 1e-6);
  }

  @Test
  void nearest_excludesFollowerAheadOfLeader() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    // Leader's newest sample is x=10 heading east. A follower at x=20 is ahead of all of it.
    assertNull(DraftingMath.nearest(straightEastTrail(), new Vec3(20, 70, 0), 110, cfg));
  }

  @Test
  void nearest_returnsNullWhenOutsideTheWakeRadius() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    // Far off to the side: beyond base radius plus spread over this trail's age range.
    assertNull(DraftingMath.nearest(straightEastTrail(), new Vec3(6, 70, 40), 110, cfg));
  }

  @Test
  void nearest_agesWithDistanceBackAlongTheTrail() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery near = DraftingMath.nearest(straightEastTrail(), new Vec3(9, 70, 0), 110, cfg);
    DraftQuery far = DraftingMath.nearest(straightEastTrail(), new Vec3(1, 70, 0), 110, cfg);
    assertNotNull(near);
    assertNotNull(far);
    assertTrue(far.ageSeconds() > near.ageSeconds(), "further back is older");
    assertTrue(far.strength() < near.strength(), "older wake is weaker");
  }

  @Test
  void nearest_followsACurvedPath() {
    // Leader turns: east then north. A follower inside the corner should still find the path.
    WakeTrail trail = new WakeTrail();
    trail.record(new Vec3(0, 70, 0), 1.0, 100);
    trail.record(new Vec3(4, 70, 0), 1.0, 102);
    trail.record(new Vec3(8, 70, 0), 1.0, 104);
    trail.record(new Vec3(8, 70, 4), 1.0, 106);
    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(trail, new Vec3(8, 70, 2), 108, cfg);
    assertNotNull(q);
    assertTrue(q.lateralOffset() < 0.5, "should latch onto the northbound leg");
  }

  @Test
  void nearest_returnsNullWhenTheClockRanBackwards() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    // Trail stamped at ticks 100..110, queried at tick 5 after a dimension change reset the clock.
    assertNull(DraftingMath.nearest(straightEastTrail(), new Vec3(6, 70, 0), 5, cfg));
  }

  // wakeHeading()

  @Test
  void nearest_wakeHeadingComesFromThePathNotTheReportedVelocity() {
    // A remote leader's reported velocity is a multi tick lerp toward an already stale broadcast
    // value, so through a hard turn it still points down the old leg while the leader is visibly
    // on the new one. Reading that velocity made the wake heading lag the wake itself, the look
    // divergence check fired, and the pull released during exactly the turns the mechanic exists
    // for. The heading has to come from where the leader actually went.
    WakeTrail trail = new WakeTrail();
    trail.record(new Vec3(0, 70, 0), 1.0, 100);
    trail.record(new Vec3(4, 70, 0), 1.0, 102);
    trail.record(new Vec3(4, 70, 4), 1.0, 104); // turned north

    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(trail, new Vec3(4, 70, 2), 104, cfg);
    assertNotNull(q);
    assertEquals(0.0, q.wakeHeading().x, 1e-6, "heading must not keep pointing down the old leg");
    assertEquals(1.0, q.wakeHeading().z, 1e-6, "heading must follow the northbound leg");
  }

  @Test
  void nearest_wakeHeadingIsAHorizontalUnitVector() {
    // Climbs and dives belong to the geometry, not the heading, which the look divergence check
    // and the camera assist both read as a compass bearing.
    WakeTrail trail = new WakeTrail();
    trail.record(new Vec3(0, 60, 0), 1.0, 100);
    trail.record(new Vec3(3, 64, 0), 1.0, 102);

    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(trail, new Vec3(2, 63, 0), 102, cfg);
    assertNotNull(q);
    assertEquals(0.0, q.wakeHeading().y, 1e-9, "a climbing leg still yields a flat heading");
    assertEquals(1.0, q.wakeHeading().length(), 1e-9);
  }

  @Test
  void nearest_ignoresASegmentWithNoMovement() {
    // Two samples at one spot give no direction to derive, so that segment carries no heading.
    WakeTrail trail = new WakeTrail();
    trail.record(new Vec3(0, 70, 0), 1.0, 100);
    trail.record(new Vec3(4, 70, 0), 1.0, 102);
    trail.record(new Vec3(4, 70, 0), 1.0, 104);

    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(trail, new Vec3(2, 70, 0), 104, cfg);
    assertNotNull(q);
    assertEquals(1.0, q.wakeHeading().length(), 1e-9, "no NaN heading from a zero length segment");
  }

  @Test
  void nearest_ignoresAPurelyVerticalSegment() {
    // A vertical drop has no compass bearing. The horizontal leg before it is still draftable.
    WakeTrail trail = new WakeTrail();
    trail.record(new Vec3(0, 70, 0), 1.0, 100);
    trail.record(new Vec3(4, 70, 0), 1.0, 102);
    trail.record(new Vec3(4, 66, 0), 1.0, 104);

    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(trail, new Vec3(3, 70, 0), 104, cfg);
    assertNotNull(q, "the earlier horizontal leg is still draftable");
    assertEquals(1.0, q.wakeHeading().x, 1e-9);
  }

  // The firework slingshot

  @Test
  void aBoostedWakeAcceleratesHarderThanAPlainOne() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    double plain = DraftingMath.boostDelta(0.5, 1.0, 1.0, cfg);
    double slung = DraftingMath.boostDelta(0.5, 1.0, DraftingMath.FIREWORK_WAKE_BOOST, cfg);
    assertTrue(slung > plain, "a rocket should leave something worth chasing");
    assertEquals(plain * DraftingMath.FIREWORK_WAKE_BOOST, slung, 1e-9);
  }

  @Test
  void aBoostedWakeStillRespectsTheCeiling() {
    // A slingshot gets you to overtake speed faster. It does not take you past a limit the
    // server set, or the speed multiplier would mean nothing.
    SlipstreamConfig cfg = new SlipstreamConfig();
    double cap = DraftingMath.draftCap(cfg);
    double delta = DraftingMath.boostDelta(cap - 0.001, 1.0, 99.0, cfg);
    assertTrue(cap - 0.001 + delta <= cap + 1e-9, "slingshot broke the ceiling");
    assertEquals(0.0, DraftingMath.boostDelta(cap, 1.0, 99.0, cfg), 1e-9);
  }

  @Test
  void aBoostBelowOneNeverWeakensTheDraft() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(
        DraftingMath.boostDelta(0.5, 1.0, 1.0, cfg),
        DraftingMath.boostDelta(0.5, 1.0, 0.1, cfg),
        1e-9);
  }

  @Test
  void aBoostedWakeAgesOutFasterThanAPlainOne() {
    // The slingshot is a window you have to be in position for, not a lane left lying around.
    SlipstreamConfig cfg = new SlipstreamConfig(); // 60 ticks = 3.0 seconds
    double radius = DraftingMath.wakeRadius(1.5, cfg);
    double plain = DraftingMath.strength(1.5, 0.0, radius, 1.0, cfg);
    double slung = DraftingMath.strength(1.5, 0.0, radius, DraftingMath.FIREWORK_WAKE_BOOST, cfg);
    assertTrue(plain > 0.0, "a plain wake is still alive at 1.5 seconds");
    assertEquals(0.0, slung, 1e-9, "a boosted wake should be gone by then");
  }

  @Test
  void aBoostedWakeStrengthStaysWithinTheUnitRange() {
    // The boost is spent on forward acceleration and never on the pull, because being yanked
    // sideways harder is not a reward.
    SlipstreamConfig cfg = new SlipstreamConfig();
    double radius = DraftingMath.wakeRadius(0.0, cfg);
    double slung = DraftingMath.strength(0.0, 0.0, radius, DraftingMath.FIREWORK_WAKE_BOOST, cfg);
    assertTrue(slung <= 1.0 + 1e-9, "strength escaped the unit range: " + slung);
    assertTrue(slung > 0.0);
  }

  @Test
  void nearest_carriesTheBoostStampOfTheSegmentYouAreIn() {
    WakeTrail trail = new WakeTrail();
    trail.record(new Vec3(0, 70, 0), 1.0, 100, 1.0);
    trail.record(new Vec3(4, 70, 0), 1.0, 102, DraftingMath.FIREWORK_WAKE_BOOST);

    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(trail, new Vec3(2, 70, 0), 102, cfg);
    assertNotNull(q);
    assertEquals(DraftingMath.FIREWORK_WAKE_BOOST, q.boost(), 1e-9);
  }

  @Test
  void anUnstampedWakeCarriesNoBoost() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    DraftQuery q = DraftingMath.nearest(straightEastTrail(), new Vec3(6, 70, 0), 110, cfg);
    assertNotNull(q);
    assertEquals(1.0, q.boost(), 1e-9);
  }
}
