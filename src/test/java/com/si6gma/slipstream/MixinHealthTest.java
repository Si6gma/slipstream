package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Turning a silent mixin incompatibility into one line in the log. */
class MixinHealthTest {

  @BeforeEach
  void reset() {
    MixinHealth.reset();
  }

  @Test
  void aRunningInjectionNeverWarns() {
    for (int tick = 0; tick < 500; tick++) {
      MixinHealth.beat(tick);
      assertFalse(MixinHealth.shouldWarn(tick), "warned while the injection was running");
    }
  }

  @Test
  void aBriefHitchIsNotAnIncompatibility() {
    MixinHealth.beat(100);
    assertFalse(MixinHealth.shouldWarn(120), "twenty ticks of silence is a hitch, not a failure");
  }

  @Test
  void sustainedSilenceWarns() {
    MixinHealth.beat(100);
    assertTrue(MixinHealth.shouldWarn(200), "two seconds of gliding with no physics should warn");
  }

  @Test
  void itWarnsOnlyOnce() {
    // A warning every tick would bury the log it is trying to make readable.
    MixinHealth.beat(100);
    assertTrue(MixinHealth.shouldWarn(200));
    for (int tick = 201; tick < 400; tick++) {
      assertFalse(MixinHealth.shouldWarn(tick), "warned twice at tick " + tick);
    }
  }

  @Test
  void aFreshStartIsGivenTheSameGracePeriod() {
    // Nothing has run yet at world join, because the first travel call has not necessarily
    // happened. Warning there would fire on every single login.
    assertFalse(MixinHealth.shouldWarn(5000));
    assertFalse(MixinHealth.shouldWarn(5020));
    assertTrue(MixinHealth.shouldWarn(5100), "still silent long after the grace period");
  }

  @Test
  void aBackwardsClockIsADimensionChangeNotAFailure() {
    // tickCount resets under us on a dimension change. That is not a missing injection.
    MixinHealth.beat(5000);
    assertFalse(MixinHealth.shouldWarn(10));
    assertFalse(MixinHealth.shouldWarn(20));
    assertTrue(MixinHealth.shouldWarn(200), "silence after the reset should still warn");
  }

  @Test
  void resetClearsTheWarning() {
    MixinHealth.beat(100);
    assertTrue(MixinHealth.shouldWarn(200));
    MixinHealth.reset();
    MixinHealth.beat(100);
    assertTrue(MixinHealth.shouldWarn(200), "a new session should be able to warn again");
  }
}
