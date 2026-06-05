package com.si6gma.slipstream.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundEffectMathTest {

    @Test
    void proximity_atSurface_isOne() {
        assertEquals(1.0, GroundEffectMath.proximity(0, 20), 1e-9);
    }

    @Test
    void proximity_atMaxHeight_isZero() {
        assertEquals(0.0, GroundEffectMath.proximity(20, 20), 1e-9);
    }

    @Test
    void proximity_atHalfHeight_isOneFourth() {
        assertEquals(0.25, GroundEffectMath.proximity(10, 20), 1e-9);
    }

    @Test
    void proximity_isMonotonicallyDecreasing() {
        double prev = GroundEffectMath.proximity(0, 20);
        for (int d = 1; d <= 20; d++) {
            double curr = GroundEffectMath.proximity(d, 20);
            assertTrue(curr <= prev, "proximity should decrease as distance increases");
            prev = curr;
        }
    }
}
