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

  public static void set(double newProximity, double newSpeedRatio, boolean newOverWater) {
    proximity = newProximity;
    speedRatio = newSpeedRatio;
    overWater = newOverWater;
  }

  public static void clear() {
    proximity = 0.0;
    speedRatio = 0.0;
    overWater = false;
  }
}
