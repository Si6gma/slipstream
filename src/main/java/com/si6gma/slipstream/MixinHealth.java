package com.si6gma.slipstream;

/**
 * Notices when the physics injection has stopped running.
 *
 * <p>The entire force surface is one {@code @Inject} at the TAIL of {@code LivingEntity.travel}.
 * Another movement mod that cancels that method at HEAD stops the injection ever running, and the
 * failure is silent in the worst way: particles are emitted from a different path, so wakes keep
 * appearing while no force is applied at all. A player reports that drafting "does nothing" and
 * every log is clean.
 *
 * <p>So the injection leaves a heartbeat, and the client tick notices when it stops. This cannot
 * repair anything. It turns an invisible incompatibility into one line in the log naming the
 * likely cause, which is the difference between a bug report that can be answered and one that
 * cannot.
 */
public final class MixinHealth {

  /**
   * Ticks of gliding with no heartbeat before we conclude the injection is not running. Long
   * enough that an ordinary hitch or a dimension change cannot trip it.
   */
  private static final int SILENCE_TICKS = 40;

  private static volatile int lastBeatTick = Integer.MIN_VALUE;
  private static volatile boolean warned;

  private MixinHealth() {}

  /** Called from the travel injection for the local player, every tick it actually runs. */
  public static void beat(int tick) {
    lastBeatTick = tick;
  }

  /**
   * Called from the client tick while the local player is gliding. Returns true exactly once, the
   * first time the injection has been silent long enough to mean something, so the caller logs a
   * single line rather than one a tick.
   */
  public static boolean shouldWarn(int nowTick) {
    if (warned) return false;
    if (lastBeatTick == Integer.MIN_VALUE) {
      // Nothing has run yet. Give it the same grace period rather than warning at world join,
      // since the first travel call has not necessarily happened.
      lastBeatTick = nowTick;
      return false;
    }
    int elapsed = nowTick - lastBeatTick;
    // A negative elapsed is a dimension change resetting the counter, not a missing injection.
    if (elapsed < 0) {
      lastBeatTick = nowTick;
      return false;
    }
    if (elapsed < SILENCE_TICKS) return false;
    warned = true;
    return true;
  }

  /** Forgets the heartbeat and the warning, for a disconnect or a world change. */
  public static void reset() {
    lastBeatTick = Integer.MIN_VALUE;
    warned = false;
  }
}
