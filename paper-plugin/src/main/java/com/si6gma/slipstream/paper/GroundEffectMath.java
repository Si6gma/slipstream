package com.si6gma.slipstream.paper;

/** Mirror of the Fabric-side GroundEffectMath; keep formulas in sync. */
final class GroundEffectMath {

  private GroundEffectMath() {}

  /**
   * Quadratic proximity falloff: 1.0 within the first 3 blocks of the surface, then falls to 0.0
   * at effectHeight.
   */
  static double proximity(double distToSurface, double effectHeight) {
    double buffer = Math.min(3.0, effectHeight - 1.0);
    double adjusted = Math.max(0.0, distToSurface - buffer);
    double range = effectHeight - buffer;
    if (range <= 0) return 1.0;
    double linear = 1.0 - (adjusted / range);
    return linear * linear;
  }

  /** hSpeed / maxSpeed clamped to [0, 1]. Returns 0 for a non positive maxSpeed. */
  static double speedRatio(double hSpeed, double maxSpeed) {
    if (maxSpeed <= 0.0) return 0.0;
    return Math.max(0.0, Math.min(hSpeed / maxSpeed, 1.0));
  }

  /**
   * Horizontal acceleration to add this tick. Zero at or above the ceiling, and zero while
   * climbing beyond the deadband, because the ground effect only accelerates level or descending
   * flight. The 0.05 deadband absorbs the antigravity term so lift nudging ySpeed just past zero
   * does not toggle the gate every tick.
   */
  static double boostDelta(
      double hSpeed, double ySpeed, double proximity, double acceleration, double maxSpeed) {
    if (hSpeed >= maxSpeed || ySpeed > 0.05) return 0.0;
    return proximity * acceleration;
  }

  /**
   * Bidirectional stabilising force driving ySpeed toward level flight, within a thirty degree
   * pitch window so a player can still climb or dive deliberately. Includes the antigravity term
   * that offsets elytra's residual sink, and cannot overshoot past level.
   *
   * <p>pitchDeg is positive ascending, so callers pass the negated Bukkit pitch.
   */
  static double liftForce(
      double ySpeed,
      double pitchDeg,
      double proximity,
      double liftStrength,
      double hSpeed,
      double maxSpeed) {
    if (liftStrength <= 0.0 || maxSpeed <= 0.0) return 0.0;
    if (Math.abs(pitchDeg) > 30.0) return 0.0;
    double normalized = Math.min(hSpeed / maxSpeed, 1.0);
    double speedRatio = 1.0 - Math.pow(1.0 - normalized, 1.5);
    double angleFactor = 1.0 - (Math.abs(pitchDeg) / 30.0);
    double antiGravity = (ySpeed < 0) ? 0.02 * angleFactor * proximity * speedRatio : 0.0;
    double correction = -ySpeed * angleFactor * proximity * liftStrength * speedRatio + antiGravity;
    return ySpeed < 0 ? Math.min(correction, -ySpeed) : Math.max(correction, -ySpeed);
  }
}
