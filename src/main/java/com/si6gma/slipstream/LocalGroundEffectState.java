package com.si6gma.slipstream;

/**
 * The local player's latest ground effect state, written by the travel mixin on the client and
 * read by the wind sound and FOV kick. Lives in the main source set because the mixin does; it is
 * inert on a dedicated server.
 */
public final class LocalGroundEffectState {

  private static volatile double proximity;
  private static volatile double speedRatio;
  private static volatile boolean overWater;
  /** Negative when no surface was within the effect height this tick. */
  private static volatile double distToSurface = -1.0;

  private LocalGroundEffectState() {}

  public static double proximity() {
    return proximity;
  }

  public static double speedRatio() {
    return speedRatio;
  }

  public static boolean overWater() {
    return overWater;
  }

  /** Blocks to the surface below, or negative when there was none in range. */
  public static double distToSurface() {
    return distToSurface;
  }

  public static void set(
      double newProximity, double newSpeedRatio, boolean newOverWater, double newDistToSurface) {
    proximity = newProximity;
    speedRatio = newSpeedRatio;
    overWater = newOverWater;
    distToSurface = newDistToSurface;
  }

  public static void clear() {
    proximity = 0.0;
    speedRatio = 0.0;
    overWater = false;
    distToSurface = -1.0;
  }
}
