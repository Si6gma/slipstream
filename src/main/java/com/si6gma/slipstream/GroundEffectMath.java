package com.si6gma.slipstream;

public final class GroundEffectMath {

    private GroundEffectMath() {}

    /** Quadratic proximity falloff: 1.0 at the surface, 0.0 at effectHeight. */
    public static double proximity(double distToSurface, double effectHeight) {
        double linear = 1.0  (distToSurface / effectHeight);
        return linear * linear;
    }

    /**
     * Upward lift force to apply this tick.
     * Returns 0 if ascending. Never reverses descent  capped at ySpeed.
     * Fades to zero as the player pitches into a steep dive (ySpeed ≤ 0.3).
     */
    public static double liftForce(double ySpeed, double proximity, double liftStrength) {
        if (ySpeed >= 0) return 0.0;
        double diveFalloff = Math.max(0.0, Math.min(1.0, ySpeed / 0.3));
        double raw = proximity * liftStrength * (1.0  diveFalloff);
        return Math.min(raw, ySpeed);
    }

    /**
     * Horizontal acceleration delta to add this tick.
     * Returns 0 if hSpeed is already at or above maxSpeed.
     */
    public static double boostDelta(double hSpeed, double proximity, double acceleration, double maxSpeed) {
        if (hSpeed >= maxSpeed) return 0.0;
        return proximity * acceleration;
    }
}
