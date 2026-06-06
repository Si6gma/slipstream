package com.si6gma.slipstream;

public final class GroundEffectMath {

    private GroundEffectMath() {}

    /** Quadratic proximity falloff: 1.0 at the surface, 0.0 at effectHeight. */
    public static double proximity(double distToSurface, double effectHeight) {
        double linear = 1.0 - (distToSurface / effectHeight);
        return linear * linear;
    }

    /**
     * Bidirectional stabilising force that drives ySpeed toward 0 (level flight).
     * Positive when descending (pulls up), negative when ascending (pulls down).
     * Only active within a ±30° pitch window — steeper angles disengage it entirely
     * so the player can intentionally climb or dive.
     * Scales with v² like real aerodynamic lift: faster = stronger hold.
     * liftStrength is the fraction of the ySpeed error corrected per tick [0, 1].
     */
    public static double liftForce(double ySpeed, double hSpeed, double maxSpeed,
                                   double proximity, double liftStrength) {
        double pitchDeg = Math.toDegrees(Math.atan2(ySpeed, hSpeed));
        if (Math.abs(pitchDeg) > 30.0) return 0.0;
        double angleFactor = 1.0 - (Math.abs(pitchDeg) / 30.0);
        double speedFactor = (hSpeed / maxSpeed) * (hSpeed / maxSpeed);
        // Restoring force toward ySpeed=0, capped so it never overshoots to the other side
        double correction = -ySpeed * angleFactor * speedFactor * proximity * liftStrength;
        return ySpeed < 0 ? Math.min(correction, -ySpeed) : Math.max(correction, -ySpeed);
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
