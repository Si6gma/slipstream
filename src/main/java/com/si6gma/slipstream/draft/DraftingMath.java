package com.si6gma.slipstream.draft;

import com.si6gma.slipstream.SlipstreamConfig;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Pure geometry and force curves for drafting. No Minecraft runtime state. */
public final class DraftingMath {

  private static final double TICKS_PER_SECOND = 20.0;
  private static final double EPSILON = 1.0e-6;

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
    if (radius <= 0.0) return 0.0;
    double lifetimeSeconds = cfg.wakeLifetimeTicks / TICKS_PER_SECOND;
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
    List<WakeSample> samples = trail.samples(); // newest first
    if (samples.size() < 2) return null;

    Vec3 bestPoint = null;
    Vec3 bestHeading = null;
    double bestDistSq = Double.MAX_VALUE;
    double bestAgeTicks = 0.0;

    for (int i = 0; i < samples.size() - 1; i++) {
      WakeSample newer = samples.get(i);
      WakeSample older = samples.get(i + 1);

      // Skip any segment the follower has passed: being ahead of the leader is not drafting.
      if (follower.subtract(newer.position()).dot(newer.heading()) > 0.0) continue;

      Vec3 seg = newer.position().subtract(older.position());
      double segLenSq = seg.lengthSqr();
      if (segLenSq < EPSILON) continue;

      double t = follower.subtract(older.position()).dot(seg) / segLenSq;
      t = Math.max(0.0, Math.min(1.0, t));
      Vec3 point = older.position().add(seg.scale(t));
      double distSq = follower.distanceToSqr(point);
      if (distSq >= bestDistSq) continue;

      bestDistSq = distSq;
      bestPoint = point;
      bestHeading = newer.heading();
      bestAgeTicks = nowTick - (older.tick() + (newer.tick() - older.tick()) * t);
    }

    if (bestPoint == null) return null;

    double ageSeconds = Math.max(0.0, bestAgeTicks / TICKS_PER_SECOND);
    double lateralOffset = Math.sqrt(bestDistSq);
    double radius = wakeRadius(ageSeconds, cfg);
    double strength = strength(ageSeconds, lateralOffset, radius, cfg);
    if (strength <= 0.0) return null;

    Vec3 toCentre =
        lateralOffset < EPSILON
            ? Vec3.ZERO
            : bestPoint.subtract(follower).scale(1.0 / lateralOffset);
    return new DraftQuery(bestPoint, toCentre, lateralOffset, ageSeconds, strength, bestHeading);
  }
}
