package com.si6gma.slipstream;

public final class GroundEffectMath {

  private GroundEffectMath() {}

  /**
   * Quadratic proximity falloff: 1.0 within the first 3 blocks of the surface, then falls to 0.0 at
   * effectHeight. The flat zone lets players skim comfortably without a penalty for being a block
   * or two higher than ideal.
   */
  public static double proximity(double distToSurface, double effectHeight) {
    double buffer = Math.min(3.0, effectHeight - 1.0);
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
   * Uses the player's look pitch (intent) rather than velocity pitch so the ground-effect barrier
   * responds to where the player is aiming, making skimming over water feel smooth instead of
   * fighting a lagging trajectory vector.
   *
   * <p>pitchDeg follows the same convention as {@code Math.atan2(ySpeed, hSpeed)}: positive is
   * ascending, negative is descending. Callers should pass {@code -player.getXRot()}.
   *
   * <p>When descending, includes an anti-gravity term (~0.02/tick) that offsets elytra's residual
   * gravity so equilibrium is level flight rather than a slow sink. liftStrength is the fraction of
   * ySpeed error corrected per tick [0, 1].
   */
  public static double liftForce(
      double ySpeed,
      double pitchDeg,
      double proximity,
      double liftStrength,
      double hSpeed,
      double maxSpeed) {
    if (liftStrength <= 0.0 || maxSpeed <= 0.0) return 0.0;
    if (Math.abs(pitchDeg) > 30.0) return 0.0;
    double speedRatio = Math.min(hSpeed / maxSpeed, 1.0);
    double angleFactor = 1.0 - (Math.abs(pitchDeg) / 30.0);
    // Anti-gravity: offsets elytra's ~0.02/tick residual gravity at level flight.
    // Scaled by speed so low-speed flight can't coast indefinitely; at max speed the player gets
    // the full 0.02 compensation and can hold altitude while skimming.
    // Only when descending; ascending players are pulled back by damping alone.
    double antiGravity = (ySpeed < 0) ? 0.02 * angleFactor * proximity * speedRatio : 0.0;
    double correction = -ySpeed * angleFactor * proximity * liftStrength * speedRatio + antiGravity;
    // Cap: never overshoot past ySpeed=0
    return ySpeed < 0 ? Math.min(correction, -ySpeed) : Math.max(correction, -ySpeed);
  }

  /**
   * Horizontal acceleration delta to add this tick. Returns 0 if hSpeed is already at or above
   * maxSpeed, or if the entity is ascending beyond the dead-band — ground effect only accelerates
   * level or descending flight. The 0.05 dead-band absorbs the ~0.02/tick anti-gravity noise so
   * lift nudging velocity.y just past zero does not toggle the gate every tick.
   */
  public static double boostDelta(
      double hSpeed, double ySpeed, double proximity, double acceleration, double maxSpeed) {
    if (hSpeed >= maxSpeed || ySpeed > 0.05) return 0.0;
    return proximity * acceleration;
  }
}
