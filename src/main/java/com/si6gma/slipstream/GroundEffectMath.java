package com.si6gma.slipstream;

public final class GroundEffectMath {

    private GroundEffectMath() {}

    /** Quadratic proximity falloff: 1.0 at the surface, 0.0 at effectHeight. */
    public static double proximity(double distToSurface, double effectHeight) {
        double linear = 1.0 - (distToSurface / effectHeight);
        return linear * linear;
    }

    /**
     * Upward lift force to apply this tick.
     * Only active within a ±30° pitch window — outside that the player can freely dive or climb.
     * Within the window, fades linearly to zero at the ±30° edges so transitions feel natural.
     * Never reverses an ascent; caps at -ySpeed so it neutralises descent without bouncing.
     */
    public static double liftForce(double ySpeed, double hSpeed, double proximity, double liftStrength) {
        if (ySpeed >= 0) return 0.0;
        double pitchDeg = Math.toDegrees(Math.atan2(ySpeed, hSpeed));
        if (pitchDeg < -30.0) return 0.0;
        // 1.0 at level flight, 0.0 at the -30° edge
        double angleFactor = 1.0 + (pitchDeg / 30.0);
        double raw = proximity * liftStrength * angleFactor;
        return Math.min(raw, -ySpeed);
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
