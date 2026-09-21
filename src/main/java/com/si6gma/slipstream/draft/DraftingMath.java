package com.si6gma.slipstream.draft;

import com.si6gma.slipstream.SlipstreamConfig;
import net.minecraft.world.phys.Vec3;

/** Pure geometry and force curves for drafting. No Minecraft runtime state. */
public final class DraftingMath {

  private static final double TICKS_PER_SECOND = 20.0;
  private static final double EPSILON = 1.0e-6;

  /**
   * Forward acceleration multiplier for wake left while the leader was under a firework. Popping
   * a rocket puts a genuine slingshot behind you for a moment, which rewards flying as a group
   * using a verb players already know rather than a new one.
   */
  public static final double FIREWORK_WAKE_BOOST = 2.5;

  /**
   * Fraction of the normal lifetime a boosted wake keeps. Deliberately short: a slingshot should
   * be a window you have to be in position for, not a fast lane left lying around for three
   * seconds after the leader has gone.
   */
  public static final double FIREWORK_WAKE_LIFETIME_FRACTION = 0.4;

  private DraftingMath() {}

  /** A wake spreads as it ages, so an old wake is wide and weak rather than narrow and strong. */
  public static double wakeRadius(double ageSeconds, SlipstreamConfig cfg) {
    return cfg.wakeBaseRadius + Math.max(0.0, ageSeconds) * cfg.wakeSpreadRate;
  }

  /**
   * Combined falloff in [0, 1]. Age falls off linearly to zero at the configured lifetime; lateral
   * offset falls off quadratically to zero at the wake edge, so the centre is meaningfully better
   * than the rim, matching how the ground effect rewards flying precisely.
   */
  public static double strength(
      double ageSeconds, double lateralOffset, double radius, SlipstreamConfig cfg) {
    return strength(ageSeconds, lateralOffset, radius, 1.0, cfg);
  }

  /**
   * As above, for wake carrying a boost stamp. A boosted sample ages out faster, so the slingshot
   * is a window rather than a lane. Strength itself stays within [0, 1]: the boost is spent on
   * forward acceleration, never on the pull, because a violent sideways yank is not a reward.
   */
  public static double strength(
      double ageSeconds, double lateralOffset, double radius, double boost, SlipstreamConfig cfg) {
    if (radius <= 0.0) return 0.0;
    double lifetimeSeconds = cfg.wakeLifetimeTicks / TICKS_PER_SECOND;
    if (boost > 1.0) lifetimeSeconds *= FIREWORK_WAKE_LIFETIME_FRACTION;
    if (lifetimeSeconds <= 0.0) return 0.0;
    double ageFalloff = 1.0 - (Math.max(0.0, ageSeconds) / lifetimeSeconds);
    if (ageFalloff <= 0.0) return 0.0;
    double lateral = 1.0 - (Math.max(0.0, lateralOffset) / radius);
    if (lateral <= 0.0) return 0.0;
    return Math.min(1.0, ageFalloff) * lateral * lateral;
  }

  /**
   * Nearest point on the trail's centreline to the follower, or null when the follower is ahead of
   * the leader, outside the wake, or the trail is too short to form a segment.
   */
  public static DraftQuery nearest(
      WakeTrail trail, Vec3 follower, int nowTick, SlipstreamConfig cfg) {
    if (trail.size() < 2) return null;

    Vec3 bestPoint = null;
    Vec3 bestHeading = null;
    double bestBoost = 1.0;
    double bestDistSq = Double.MAX_VALUE;
    double bestAgeTicks = 0.0;

    for (int i = 0; i < trail.size() - 1; i++) {
      WakeSample newer = trail.sampleAt(i);
      WakeSample older = trail.sampleAt(i + 1);

      Vec3 seg = newer.position().subtract(older.position());
      double segLenSq = seg.lengthSqr();
      if (segLenSq < EPSILON) continue;

      // The heading is this segment's own direction rather than anything recorded alongside it,
      // so the line the pull aims at and the bearing it releases against are the same line.
      Vec3 heading = horizontalUnit(seg);
      if (heading == null) continue;

      // Skip any segment the follower has passed: being ahead of the leader is not drafting.
      if (follower.subtract(newer.position()).dot(heading) > 0.0) continue;

      double t = follower.subtract(older.position()).dot(seg) / segLenSq;
      t = Math.max(0.0, Math.min(1.0, t));
      Vec3 point = older.position().add(seg.scale(t));
      double distSq = follower.distanceToSqr(point);
      if (distSq >= bestDistSq) continue;

      bestDistSq = distSq;
      bestPoint = point;
      bestHeading = heading;
      bestBoost = newer.boost();
      bestAgeTicks = nowTick - (older.tick() + (newer.tick() - older.tick()) * t);
    }

    if (bestPoint == null) return null;

    // A negative age means the query clock is behind the sample clock, which happens after a
    // dimension change. Clamping it to zero would read as a perfectly fresh wake, so refuse it.
    if (bestAgeTicks < 0.0) return null;
    double ageSeconds = bestAgeTicks / TICKS_PER_SECOND;
    double lateralOffset = Math.sqrt(bestDistSq);
    double radius = wakeRadius(ageSeconds, cfg);
    double strength = strength(ageSeconds, lateralOffset, radius, bestBoost, cfg);
    if (strength <= 0.0) return null;

    Vec3 toCentre =
        lateralOffset < EPSILON
            ? Vec3.ZERO
            : bestPoint.subtract(follower).scale(1.0 / lateralOffset);
    return new DraftQuery(
        bestPoint, toCentre, lateralOffset, ageSeconds, strength, bestHeading, bestBoost);
  }

  /**
   * Flattened unit direction of a wake segment, or null when the segment is purely vertical. A
   * dive has no compass bearing, and both the look divergence check and the camera assist read
   * this as one.
   */
  private static Vec3 horizontalUnit(Vec3 v) {
    double lenSq = v.x * v.x + v.z * v.z;
    if (lenSq < EPSILON) return null;
    double len = Math.sqrt(lenSq);
    return new Vec3(v.x / len, 0.0, v.z / len);
  }

  /** Speed ceiling while drafting. Above the normal cap so a follower can actually overtake. */
  public static double draftCap(SlipstreamConfig cfg) {
    return cfg.maxSpeedBlocksPerTick * cfg.draftSpeedMultiplier;
  }

  /**
   * Forward acceleration to add this tick. The caller applies it along the follower's own heading,
   * never the leader's, so drafting accelerates without steering.
   */
  public static double boostDelta(double hSpeed, double strength, SlipstreamConfig cfg) {
    return boostDelta(hSpeed, strength, 1.0, cfg);
  }

  /**
   * As above, with the wake's boost stamp spent here and nowhere else. The ceiling still holds:
   * a slingshot gets you to overtake speed far faster, it does not take you past a limit the
   * server set.
   */
  public static double boostDelta(
      double hSpeed, double strength, double boost, SlipstreamConfig cfg) {
    if (!cfg.draftingEnabled || strength <= 0.0) return 0.0;
    double cap = draftCap(cfg);
    if (hSpeed >= cap) return 0.0;
    return Math.min(strength * cfg.draftAccelerationPerTick * Math.max(1.0, boost), cap - hSpeed);
  }

  /** Closing speed wanted per block of remaining offset. Falls to zero as the gap closes. */
  private static final double APPROACH_RATE = 0.30;

  /**
   * Correction applied along {@link DraftQuery#toCentre}, positive to pull inward and negative to
   * brake. Mirrors the ground effect lift force by steering a velocity rather than shoving a
   * position.
   *
   * <p>The earlier version added an impulse sized against the remaining distance, which meant that
   * on arriving at the centre line the follower still carried all that inward velocity and sailed
   * straight through it, springing back and forth. Here the wanted closing speed shrinks as the
   * gap shrinks, so the correction turns into a brake before arrival and the follower settles onto
   * the line instead of oscillating about it.
   *
   * @param lateralError distance from the wake centre line, in blocks, never negative
   * @param closingSpeed current speed toward the centre line, negative when moving away
   * @param lookDivergenceDeg angle between the player's horizontal look and the wake heading
   */
  public static double pullForce(
      double lateralError,
      double closingSpeed,
      double lookDivergenceDeg,
      double strength,
      SlipstreamConfig cfg) {
    if (!cfg.draftingEnabled || strength <= 0.0 || cfg.draftPullStrength <= 0.0) return 0.0;
    if (lateralError <= 0.0) return 0.0;
    double release = cfg.draftReleaseAngleDeg;
    if (release <= 0.0) return 0.0;
    double divergence = Math.abs(lookDivergenceDeg);
    if (divergence >= release) return 0.0;
    double angleFactor = 1.0 - (divergence / release);
    double wantedClosing = lateralError * APPROACH_RATE;
    double correction = (wantedClosing - closingSpeed) * angleFactor * strength * cfg.draftPullStrength;
    // Hard guarantee on top of the damping: this tick's closing speed may never exceed the gap
    // itself, so no combination of tuning can carry the follower across the line and reintroduce
    // the rubber band. Braking corrections are left alone.
    double resulting = closingSpeed + correction;
    if (resulting > lateralError) {
      correction = lateralError - closingSpeed;
    }
    return correction;
  }

  /**
   * Forward acceleration a leader gains from flyers in its wake. True to the aerodynamics, where a
   * trailing body reduces the leader's wake drag. Deliberately far smaller than drafting itself.
   */
  public static double leaderBonus(int drafterCount, SlipstreamConfig cfg) {
    if (!cfg.draftingEnabled || drafterCount <= 0) return 0.0;
    int counted = Math.min(drafterCount, cfg.draftLeaderBonusMaxDrafters);
    return counted * cfg.draftLeaderBonusPerDrafter * cfg.draftAccelerationPerTick;
  }
}
