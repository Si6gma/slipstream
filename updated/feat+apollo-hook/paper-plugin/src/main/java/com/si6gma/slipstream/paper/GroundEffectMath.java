/*
 * Decompiled with CFR 0.152.
 */
package com.si6gma.slipstream.paper;

final class GroundEffectMath {
    private GroundEffectMath() {
    }

    static double proximity(double distToSurface, double effectHeight) {
        double buffer = Math.min(3.0, effectHeight - 1.0);
        double adjusted = Math.max(0.0, distToSurface - buffer);
        double range = effectHeight - buffer;
        if (range <= 0.0) {
            return 1.0;
        }
        double linear = 1.0 - adjusted / range;
        return linear * linear;
    }
}
