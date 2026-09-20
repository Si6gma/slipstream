package com.si6gma.slipstream.draft;

import com.si6gma.slipstream.SlipstreamConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Picks the one wake a follower is riding.
 *
 * <p>This exists so the flight code and the client visuals cannot disagree. They previously each
 * ran their own search over different candidate sets: the visuals walked every tracked trail,
 * while the physics walked nearby players. A leader who logged out or flew out of range kept a
 * trail for a few seconds, so the client would paint that wake, play the entry cue and steer the
 * camera onto a wake the physics was refusing to boost.
 */
public final class DraftScan {

  /** The chosen leader and the follower's position within their wake. */
  public record Target(UUID leaderId, DraftQuery query) {}

  private DraftScan() {}

  /**
   * Strongest wake among nearby players, or null when the follower is not in anyone's wake.
   *
   * @param candidates players to consider, normally the level's player list
   * @param follower the player doing the drafting, excluded from its own search
   * @param followerPos position to test, passed separately so callers may use a mid tick position
   * @param nowTick the caller's tick clock, which must match the one the trails were stamped with
   * @param rangeSq how far away a leader may be, squared
   */
  public static Target strongest(
      List<? extends Player> candidates,
      Player follower,
      Vec3 followerPos,
      WakeTracker tracker,
      int nowTick,
      double rangeSq,
      SlipstreamConfig cfg) {
    if (!cfg.draftingEnabled) return null;

    Target best = null;
    for (Player other : candidates) {
      if (other == follower) continue;
      if (other.position().distanceToSqr(followerPos) > rangeSq) continue;

      DraftQuery query =
          DraftingMath.nearest(tracker.trailFor(other.getUUID()), followerPos, nowTick, cfg);
      if (query == null) continue;
      if (best == null || query.strength() > best.query().strength()) {
        best = new Target(other.getUUID(), query);
      }
    }
    return best;
  }
}
