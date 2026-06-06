package com.si6gma.slipstream;

public final class GroundEffectMath {

  private GroundEffectMath() {}

  /**
   * Quadratic proximity falloff: 1.0 within the first 3 blocks of the surface, then falls to 0.0 at
   * effectHeight. The flat zone lets players skim comfortably without a penalty for being a block
   * or two higher than ideal.
   */
  public static double proximity(double distToSurface, double effectHeight) {
    final double buffer = 3.0;
    double adjusted = Math.max(0.0, distToSurface - buffer);
    double range = effectHeight - buffer;
    if (range <= 0) return 1.0;
    double linear = 1.0 - (adjusted / range);
    return linear * linear;
  }

  /**
   * Bidirectional stabilising force that drives ySpeed toward 0 (level flight). Positive when
   * descending (pulls up), negative when ascending (pulls down). Only active within a ±30° pitch
   * window — steeper angles disengage it entirely so the player can intentionally climb or dive.
   * When descending, includes an anti-gravity term (~0.02/tick) that offsets elytra's residual
   * gravity so equilibrium is level flight rather than a slow sink. liftStrength is the fraction of
   * ySpeed error corrected per tick [0, 1].
   */
  public static double liftForce(
      double ySpeed, double hSpeed, double proximity, double liftStrength) {
    if (liftStrength <= 0.0) return 0.0;
    double pitchDeg = Math.toDegrees(Math.atan2(ySpeed, hSpeed));
    if (Math.abs(pitchDeg) > 30.0) return 0.0;
    double angleFactor = 1.0 - (Math.abs(pitchDeg) / 30.0);
    // Anti-gravity: offsets elytra's ~0.02/tick residual gravity at level flight.
    // Only when descending; ascending players are pulled back by damping alone.
    double antiGravity = (ySpeed < 0) ? 0.02 * angleFactor * proximity : 0.0;
    double correction = -ySpeed * angleFactor * proximity * liftStrength + antiGravity;
    // Cap: never overshoot past ySpeed=0
    return ySpeed < 0 ? Math.min(correction, -ySpeed) : Math.max(correction, -ySpeed);
  }

  /**
   * Horizontal acceleration delta to add this tick. Returns 0 if hSpeed is already at or above
   * maxSpeed.
   */
  public static double boostDelta(
      double hSpeed, double proximity, double acceleration, double maxSpeed) {
    if (hSpeed >= maxSpeed) return 0.0;
    return proximity * acceleration;
  }
}
