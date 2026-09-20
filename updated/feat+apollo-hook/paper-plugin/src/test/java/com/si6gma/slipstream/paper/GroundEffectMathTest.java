/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.si6gma.slipstream.paper.GroundEffectMath
 *  org.junit.jupiter.api.Assertions
 *  org.junit.jupiter.api.Test
 */
package com.si6gma.slipstream.paper;

import com.si6gma.slipstream.paper.GroundEffectMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GroundEffectMathTest {
    GroundEffectMathTest() {
    }

    @Test
    void proximity_atSurface_isOne() {
        Assertions.assertEquals((double)1.0, (double)GroundEffectMath.proximity((double)0.0, (double)20.0), (double)1.0E-9);
    }

    @Test
    void proximity_atMaxHeight_isZero() {
        Assertions.assertEquals((double)0.0, (double)GroundEffectMath.proximity((double)20.0, (double)20.0), (double)1.0E-9);
    }

    @Test
    void proximity_withinBuffer_isOne() {
        Assertions.assertEquals((double)1.0, (double)GroundEffectMath.proximity((double)0.0, (double)20.0), (double)1.0E-9);
        Assertions.assertEquals((double)1.0, (double)GroundEffectMath.proximity((double)1.0, (double)20.0), (double)1.0E-9);
        Assertions.assertEquals((double)1.0, (double)GroundEffectMath.proximity((double)3.0, (double)20.0), (double)1.0E-9);
    }

    @Test
    void proximity_isMonotonicallyDecreasing() {
        double prev = GroundEffectMath.proximity((double)0.0, (double)20.0);
        for (int d = 1; d <= 20; ++d) {
            double curr = GroundEffectMath.proximity((double)d, (double)20.0);
            Assertions.assertTrue((curr <= prev ? 1 : 0) != 0, (String)"proximity should decrease as distance increases");
            prev = curr;
        }
    }
}
