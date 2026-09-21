package com.si6gma.slipstream.paper;

/**
 * A cheap server side estimate of whether a glider is riding someone's wake, and how squarely.
 *
 * <p>The server does not build wake trails. That was dropped deliberately, and the real drafting
 * geometry runs on the client from tracked player positions. So this is an approximation: is there
 * another glider close ahead, and am I pointed the same way they are.
 *
 * <p>Approximating is the right call rather than a compromise. The alternative is for the client
 * to report its own draft strength, and the moment that strength buys a resource saving, a client
 * that always claims a perfect draft never wears out an elytra. Anything the server works out for
 * itself cannot be lied about.
 *
 * <p>Free of Bukkit types so it can be tested: {@code paper-api} is {@code compileOnly}.
 */
final class DraftEstimate {

  /** Blocks behind a leader that still counts as their wake. */
  static final double RANGE = 12.0;

  /** Cosine of the widest angle that still counts, about thirty degrees. */
  static final double MIN_ALIGNMENT = 0.866;

  /** The most durability drafting may ever save, so an elytra never becomes immortal. */
  static final double MAX_SAVE = 0.75;

  private DraftEstimate() {}

  /**
   * Draft strength in [0, 1] for a follower at this offset from a leader.
   *
   * @param distance blocks between the two
   * @param alignment dot product of the follower's heading with the direction to the leader, both
   *     horizontal unit vectors, so 1 is directly ahead and 0 is abeam
   * @param headingAgreement dot product of the two gliders' headings, so 1 is flying the same way
   */
  static double strength(double distance, double alignment, double headingAgreement) {
    if (distance <= 0.0 || distance >= RANGE) return 0.0;
    if (alignment < MIN_ALIGNMENT) return 0.0;
    if (headingAgreement < MIN_ALIGNMENT) return 0.0;
    return 1.0 - (distance / RANGE);
  }

  /**
   * Whether this tick's durability hit is skipped, given a roll in [0, 1).
   *
   * <p>Vanilla takes one point every twenty ticks, so there is no fraction of a point to shave.
   * Skipping a proportion of the hits is the only way to express a partial saving at all, and it
   * averages out to the same thing over a flight.
   */
  static boolean skipDamage(double strength, double roll) {
    if (!(strength > 0.0)) return false;
    double clamped = Math.min(1.0, strength);
    return roll < clamped * MAX_SAVE;
  }
}
