package com.si6gma.slipstream.draft;

/**
 * How much of the view the camera assist is allowed to steer this tick. Pure scalars, no Minecraft
 * runtime state, so the rule that decides whether the assist is fighting the player can be tested
 * without a client.
 *
 * <p>The assist used to watch yaw alone and treat steering as a switch: any view movement past a
 * fraction of a degree stood it down for one tick, after which it applied at full strength again,
 * so holding the mouse produced a stutter rather than a handover. Pitch was never watched at all,
 * so a player looking down was fought every tick with no way out.
 *
 * <p>Two rules replace that. Authority is graded, not binary: a nudge costs a little of it and a
 * real steer costs all of it. And it is surrendered instantly but returned slowly, because the
 * hand has to be obeyed the moment it moves, while snapping back the instant it pauses is what
 * makes an assist feel like it is wrestling you mid correction.
 */
public final class CameraAssistMath {

  /** View movement at or below this, in degrees per tick, is noise rather than steering. */
  public static final double INPUT_DEADZONE_DEG = 0.25;

  /** View movement at or above this, in degrees per tick, hands the view back entirely. */
  public static final double FULL_YIELD_DEG = 4.0;

  /** Fraction of authority returned per tick once the player stops steering. */
  public static final double AUTHORITY_RECOVERY_PER_TICK = 0.04;

  private CameraAssistMath() {}

  /**
   * Degrees the player moved the view themselves: the difference between where the view sits now
   * and where the assist left it at the end of the previous tick.
   *
   * <p>Yaw and pitch are combined as a diagonal rather than summed, so a flick that moves both
   * axes is not counted twice. Both axes feed one number because the assist holds one authority:
   * deliberately looking down releases the turn help too. That is the conservative reading of
   * never fighting the player, and it is the half worth revisiting first if flying says the assist
   * gives up too easily.
   */
  public static double playerInputDeg(
      double yawNow, double yawLeftByAssist, double pitchNow, double pitchLeftByAssist) {
    double yawDelta = wrapDegrees(yawNow - yawLeftByAssist);
    double pitchDelta = pitchNow - pitchLeftByAssist;
    return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
  }

  /**
   * The most authority the assist may hold given this tick's input, in [0, 1]. Full below the
   * deadzone, nothing at or above the full yield angle, and a straight ramp between the two so a
   * gentle correction costs the assist a little rather than all of it.
   */
  public static double yieldTarget(double inputDeg) {
    if (!(inputDeg > INPUT_DEADZONE_DEG)) return 1.0;
    if (inputDeg >= FULL_YIELD_DEG) return 0.0;
    return 1.0 - ((inputDeg - INPUT_DEADZONE_DEG) / (FULL_YIELD_DEG - INPUT_DEADZONE_DEG));
  }

  /**
   * Authority for this tick. Drops straight to the target, because the player's hand has to take
   * effect on the tick it moves, and climbs back at a fixed rate, because returning it the instant
   * the mouse pauses is what made the old version stutter mid correction.
   */
  public static double nextAuthority(double current, double yieldTarget, double recoveryPerTick) {
    double clampedTarget = clamp01(yieldTarget);
    double clampedCurrent = clamp01(current);
    if (clampedTarget <= clampedCurrent) return clampedTarget;
    return Math.min(clampedTarget, clampedCurrent + Math.max(0.0, recoveryPerTick));
  }

  /**
   * Fraction of the way the view is eased toward the wake this tick. Zero once the assist has no
   * authority, so a steering player is never moved at all rather than moved a little.
   */
  public static double assistRate(double configStrength, double draftStrength, double authority) {
    if (configStrength <= 0.0 || draftStrength <= 0.0) return 0.0;
    return clamp01(configStrength * draftStrength * clamp01(authority));
  }

  private static double clamp01(double v) {
    if (!Double.isFinite(v)) return 0.0;
    return Math.max(0.0, Math.min(1.0, v));
  }

  /** Shortest signed distance between two bearings, in degrees. */
  private static double wrapDegrees(double deg) {
    double wrapped = deg % 360.0;
    if (wrapped >= 180.0) wrapped -= 360.0;
    if (wrapped < -180.0) wrapped += 360.0;
    return wrapped;
  }
}
