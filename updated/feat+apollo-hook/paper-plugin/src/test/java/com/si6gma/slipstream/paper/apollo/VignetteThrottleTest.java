/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.si6gma.slipstream.paper.apollo.VignetteThrottle
 *  org.junit.jupiter.api.Assertions
 *  org.junit.jupiter.api.Test
 */
package com.si6gma.slipstream.paper.apollo;

import com.si6gma.slipstream.paper.apollo.VignetteThrottle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VignetteThrottleTest {
    VignetteThrottleTest() {
    }

    @Test
    void opacity_scalesLinearlyWithProximity() {
        Assertions.assertEquals((double)0.0, (double)VignetteThrottle.opacity((double)0.0, (double)0.5), (double)1.0E-6);
        Assertions.assertEquals((double)0.25, (double)VignetteThrottle.opacity((double)0.5, (double)0.5), (double)1.0E-6);
        Assertions.assertEquals((double)0.5, (double)VignetteThrottle.opacity((double)1.0, (double)0.5), (double)1.0E-6);
    }

    @Test
    void opacity_clampsProximityOutsideUnitRange() {
        Assertions.assertEquals((double)0.0, (double)VignetteThrottle.opacity((double)-2.0, (double)0.5), (double)1.0E-6);
        Assertions.assertEquals((double)0.5, (double)VignetteThrottle.opacity((double)4.0, (double)0.5), (double)1.0E-6);
    }

    @Test
    void firstActiveTick_alwaysSends() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.1f, 100L));
    }

    @Test
    void sameBucket_doesNotResend() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.3f, 100L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.31f, 200L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.29f, 300L));
    }

    @Test
    void bucketChangeInsideInterval_isSuppressed() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.1f, 100L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.4f, 101L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.4f, 103L));
    }

    @Test
    void bucketChangeAfterInterval_sends() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.1f, 100L));
        Assertions.assertTrue((boolean)throttle.shouldSend(0.4f, 104L));
    }

    @Test
    void suppressedChange_stillSendsOnceTheIntervalElapses() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.1f, 100L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.45f, 102L));
        Assertions.assertTrue((boolean)throttle.shouldSend(0.45f, 104L));
    }

    @Test
    void reset_makesTheNextTickSendAgain() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.25f, 100L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.25f, 500L));
        throttle.reset();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.25f, 501L));
    }

    @Test
    void zeroOpacity_isItsOwnBucket() {
        VignetteThrottle throttle = new VignetteThrottle();
        Assertions.assertTrue((boolean)throttle.shouldSend(0.0f, 100L));
        Assertions.assertFalse((boolean)throttle.shouldSend(0.0f, 200L));
        Assertions.assertTrue((boolean)throttle.shouldSend(0.5f, 300L));
    }
}
