package com.si6gma.slipstream.paper;

/** Mirror of the Fabricside GroundEffectMath  keep formulas in sync. */
final class GroundEffectMath {

    private GroundEffectMath() {}

    /** Quadratic proximity falloff: 1.0 at the surface, 0.0 at effectHeight. */
    static double proximity(double distToSurface, double effectHeight) {
        double linear = 1.0  (distToSurface / effectHeight);
        return linear * linear;
    }
}
