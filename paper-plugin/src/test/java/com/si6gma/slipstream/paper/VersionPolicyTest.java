package com.si6gma.slipstream.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.si6gma.slipstream.paper.VersionPolicy.Action;
import com.si6gma.slipstream.paper.VersionPolicy.ClientState;
import com.si6gma.slipstream.paper.VersionPolicy.EnforcementPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VersionPolicyTest {

  @ParameterizedTest
  @EnumSource(EnforcementPolicy.class)
  void decide_vanilla_isNeverActedOnUnderAnyPolicy(EnforcementPolicy policy) {
    assertEquals(Action.DO_NOTHING, VersionPolicy.decide(ClientState.VANILLA, policy));
  }

  @ParameterizedTest
  @EnumSource(EnforcementPolicy.class)
  void decide_compatible_alwaysSendsConfigRegardlessOfPolicy(EnforcementPolicy policy) {
    assertEquals(Action.SEND_CONFIG, VersionPolicy.decide(ClientState.COMPATIBLE, policy));
  }

  @ParameterizedTest
  @EnumSource(EnforcementPolicy.class)
  void decide_mismatched_followsPolicy(EnforcementPolicy policy) {
    Action expected =
        switch (policy) {
          case OFF -> Action.SEND_CONFIG;
          case DISABLE -> Action.WITHHOLD_AND_MESSAGE;
          case KICK -> Action.KICK;
        };
    assertEquals(expected, VersionPolicy.decide(ClientState.MISMATCHED, policy));
  }

  @ParameterizedTest
  @EnumSource(EnforcementPolicy.class)
  void decide_legacy_followsSamePolicyAsMismatched(EnforcementPolicy policy) {
    assertEquals(
        VersionPolicy.decide(ClientState.MISMATCHED, policy),
        VersionPolicy.decide(ClientState.LEGACY, policy));
  }

  @Test
  void decide_off_sendsConfigForMismatchedAndLegacy() {
    assertEquals(
        Action.SEND_CONFIG, VersionPolicy.decide(ClientState.MISMATCHED, EnforcementPolicy.OFF));
    assertEquals(
        Action.SEND_CONFIG, VersionPolicy.decide(ClientState.LEGACY, EnforcementPolicy.OFF));
  }

  @Test
  void decide_disable_withholdsAndMessagesForMismatchedAndLegacy() {
    assertEquals(
        Action.WITHHOLD_AND_MESSAGE,
        VersionPolicy.decide(ClientState.MISMATCHED, EnforcementPolicy.DISABLE));
    assertEquals(
        Action.WITHHOLD_AND_MESSAGE,
        VersionPolicy.decide(ClientState.LEGACY, EnforcementPolicy.DISABLE));
  }

  @Test
  void decide_kick_kicksMismatchedAndLegacy() {
    assertEquals(
        Action.KICK, VersionPolicy.decide(ClientState.MISMATCHED, EnforcementPolicy.KICK));
    assertEquals(Action.KICK, VersionPolicy.decide(ClientState.LEGACY, EnforcementPolicy.KICK));
  }
}
