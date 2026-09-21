package com.si6gma.slipstream;

/**
 * The local player's latest drafting state, written by the client tick and read by the debug
 * overlay. A sibling of {@link LocalGroundEffectState}, and kept in the main source set for the
 * same reason: the overlay is client only, but this is plain state with no client types in it.
 *
 * <p>The overlay deliberately reads a recorded value rather than running its own wake search. A
 * second search could disagree with the one the flight code used, which is exactly the class of
 * bug the shared {@code DraftScan} was introduced to end.
 */
public final class LocalDraftState {

  private static volatile double strength;
  private static volatile String leaderName;

  private LocalDraftState() {}

  /** Draft strength in [0, 1], or 0 when not drafting. */
  public static double strength() {
    return strength;
  }

  /** Whose wake it is, or null when not drafting. */
  public static String leaderName() {
    return leaderName;
  }

  public static void set(double newStrength, String newLeaderName) {
    strength = newStrength;
    leaderName = newLeaderName;
  }

  public static void clear() {
    strength = 0.0;
    leaderName = null;
  }
}
