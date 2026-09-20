package com.si6gma.slipstream.draft;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.si6gma.slipstream.SlipstreamConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Two gliders through a hard turn, driven along a scripted path by the same tracker the client
 * ticks. The feature has only ever been flown solo, so this is the closest the suite gets to a
 * second player: a leader turning at sixty degrees a second with a follower riding their line.
 *
 * <p>The turn is the case that matters. A follower on a straight wake keeps drafting almost
 * whatever the geometry does, while a turn is where a heading that lags the path pushes the look
 * divergence past the release angle and drops the pull for no reason the player can see.
 */
class DraftingTurnTest {

  private static final UUID LEADER = UUID.nameUUIDFromBytes("leader".getBytes());

  private static final double SPEED = 1.2; // blocks per tick, a normal elytra cruise
  private static final double TURN_DEG_PER_TICK = 3.0; // sixty degrees a second
  private static final int STRAIGHT_TICKS = 40;
  private static final int TURN_TICKS = 30; // ninety degrees of turn
  private static final int FOLLOW_LAG_TICKS = 10; // about twelve blocks back

  /** Straight east, then a constant rate turn onto north. */
  private static List<Vec3> leaderPath() {
    List<Vec3> path = new ArrayList<>();
    double x = 0.0;
    double z = 0.0;
    double headingDeg = 0.0;
    for (int t = 0; t <= STRAIGHT_TICKS + TURN_TICKS; t++) {
      path.add(new Vec3(x, 70.0, z));
      if (t >= STRAIGHT_TICKS) headingDeg += TURN_DEG_PER_TICK;
      double rad = Math.toRadians(headingDeg);
      x += Math.cos(rad) * SPEED;
      z += Math.sin(rad) * SPEED;
    }
    return path;
  }

  /** Angle between two horizontal directions, in degrees. */
  private static double divergenceDeg(Vec3 a, Vec3 b) {
    double aLen = Math.sqrt(a.x * a.x + a.z * a.z);
    double bLen = Math.sqrt(b.x * b.x + b.z * b.z);
    double dot = (a.x * b.x + a.z * b.z) / (aLen * bLen);
    return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
  }

  @Test
  void followerKeepsTheDraftAllTheWayThroughAHardTurn() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    List<Vec3> path = leaderPath();
    WakeTracker tracker = new WakeTracker();

    int checked = 0;
    double worstDivergence = 0.0;
    double weakest = 1.0;

    for (int t = 0; t < path.size(); t++) {
      tracker.record(LEADER, path.get(t), SPEED, t, cfg);
      tracker.prune(t, cfg);

      int followerIndex = t - FOLLOW_LAG_TICKS;
      // Give the trail a couple of sampling intervals to form before asking anything of it.
      if (followerIndex < 2) continue;

      Vec3 follower = path.get(followerIndex);
      Vec3 followerLook = follower.subtract(path.get(followerIndex - 1));

      DraftQuery q = DraftingMath.nearest(tracker.trailFor(LEADER), follower, t, cfg);
      assertNotNull(q, "follower left the wake at tick " + t + " while riding the leader's line");
      assertTrue(q.strength() > 0.0, "zero strength at tick " + t);

      double divergence = divergenceDeg(followerLook, q.wakeHeading());
      assertTrue(
          divergence < cfg.draftReleaseAngleDeg,
          "pull released at tick " + t + ": divergence " + divergence + " degrees");

      worstDivergence = Math.max(worstDivergence, divergence);
      weakest = Math.min(weakest, q.strength());
      checked++;
    }

    assertTrue(checked > STRAIGHT_TICKS, "the simulation did not run long enough to mean anything");
    // A follower sitting exactly on the leader's line reads as almost dead astern: the only
    // divergence left is the half sampling interval between the segment's averaged heading and
    // the follower's instantaneous one, which is 4.5 degrees at this turn rate. Bounding it well
    // under the 35 degree release angle is what says the heading is tracking the path. This bound
    // is a feature level guard, not a sensitive lag detector; that job belongs to
    // DraftingGeometryTest.nearest_wakeHeadingComesFromThePathNotTheReportedVelocity.
    assertTrue(
        worstDivergence < 6.0,
        "worst divergence " + worstDivergence + " degrees is too close to the release angle");
    assertTrue(weakest > 0.0, "weakest strength " + weakest);
  }

  @Test
  void followerOffToOneSideIsPulledTowardTheLineNotAcrossIt() {
    // Same turn, but the follower flies parallel to the leader's line and one block to its side.
    // toCentre must point back at the line the whole way round rather than swinging wide.
    SlipstreamConfig cfg = new SlipstreamConfig();
    List<Vec3> path = leaderPath();
    WakeTracker tracker = new WakeTracker();

    int checked = 0;
    for (int t = 0; t < path.size(); t++) {
      tracker.record(LEADER, path.get(t), SPEED, t, cfg);
      tracker.prune(t, cfg);

      int followerIndex = t - FOLLOW_LAG_TICKS;
      if (followerIndex < 2) continue;

      Vec3 on = path.get(followerIndex);
      Vec3 along = on.subtract(path.get(followerIndex - 1));
      double alongLen = Math.sqrt(along.x * along.x + along.z * along.z);
      Vec3 side = new Vec3(-along.z / alongLen, 0.0, along.x / alongLen);
      Vec3 follower = on.add(side);

      DraftQuery q = DraftingMath.nearest(tracker.trailFor(LEADER), follower, t, cfg);
      assertNotNull(q, "follower one block off the line lost the wake at tick " + t);
      assertTrue(q.lateralOffset() < 1.2, "offset drifted at tick " + t + ": " + q.lateralOffset());
      // toCentre points from the follower back onto the line, so it opposes the sideways offset.
      double dotSide = q.toCentre().x * side.x + q.toCentre().z * side.z;
      assertTrue(dotSide < 0.0, "pull pointed away from the line at tick " + t);
      checked++;
    }
    assertTrue(checked > STRAIGHT_TICKS, "the simulation did not run long enough to mean anything");
  }
}
