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

    // liftForce()

    @Test
    void liftForce_whenHorizontal_isZero() {
        assertEquals(0.0, GroundEffectMath.liftForce(0.0, 1.5, 3.0, 1.0, 0.6), 1e-9);
    }

    @Test
    void liftForce_whenDescending_isPositive() {
        assertTrue(GroundEffectMath.liftForce(-0.05, 1.5, 3.0, 1.0, 0.6) > 0);
    }

    @Test
    void liftForce_whenAscending_isNegative() {
        assertTrue(GroundEffectMath.liftForce(0.05, 1.5, 3.0, 1.0, 0.6) < 0);
    }

    @Test
    void liftForce_neverOvershoots() {
        for (double ySpeed : new double[]{-0.01, -0.05, -0.1, 0.01, 0.05, 0.1}) {
            double lift = GroundEffectMath.liftForce(ySpeed, 1.5, 3.0, 1.0, 0.6);
            assertTrue(Math.abs(lift) <= Math.abs(ySpeed),
                    "Lift must not overshoot ySpeed=0 at ySpeed=" + ySpeed);
        }
    }

    @Test
    void liftForce_outsideAngleWindow_isZero() {
        assertEquals(0.0, GroundEffectMath.liftForce(-0.5, 0.5, 3.0, 1.0, 0.6), 1e-9);
        assertEquals(0.0, GroundEffectMath.liftForce(0.5, 0.5, 3.0, 1.0, 0.6), 1e-9);
    }

    // boostDelta()

    @Test
    void boostDelta_whenAtMaxSpeed_isZero() {
        assertEquals(0.0, GroundEffectMath.boostDelta(3.0, 1.0, 0.001, 3.0), 1e-9);
    }

    @Test
    void boostDelta_whenAboveMaxSpeed_isZero() {
        assertEquals(0.0, GroundEffectMath.boostDelta(4.0, 1.0, 0.001, 3.0), 1e-9);
    }

    @Test
    void boostDelta_scalesWithProximity() {
        double low = GroundEffectMath.boostDelta(0, 0.5, 0.001, 3.0);
        double high = GroundEffectMath.boostDelta(0, 1.0, 0.001, 3.0);
        assertEquals(2 * low, high, 1e-9);
    }
}
