package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SlipstreamConfigTest {

  @Test
  void validatePostLoad_nanValues_resetToDefaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.effectHeightBlocks = Double.NaN;
    cfg.accelerationPerTick = Double.NaN;
    cfg.maxSpeedBlocksPerTick = Double.NaN;
    cfg.waterSprayHeightBlocks = Double.NaN;
    cfg.liftStrength = Double.NaN;
    cfg.effectSpeedThreshold = Double.NaN;
    cfg.validatePostLoad();
    assertEquals(20.0, cfg.effectHeightBlocks, 1e-9);
    assertEquals(0.005, cfg.accelerationPerTick, 1e-9);
    assertEquals(1.5, cfg.maxSpeedBlocksPerTick, 1e-9);
    assertEquals(5.0, cfg.waterSprayHeightBlocks, 1e-9);
    assertEquals(0.6, cfg.liftStrength, 1e-9);
    assertEquals(0.3, cfg.effectSpeedThreshold, 1e-9);
  }

  @Test
  void validatePostLoad_infinityValues_resetToDefaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.effectHeightBlocks = Double.POSITIVE_INFINITY;
    cfg.accelerationPerTick = Double.NEGATIVE_INFINITY;
    cfg.maxSpeedBlocksPerTick = Double.POSITIVE_INFINITY;
    cfg.validatePostLoad();
    assertEquals(20.0, cfg.effectHeightBlocks, 1e-9);
    assertEquals(0.005, cfg.accelerationPerTick, 1e-9);
    assertEquals(1.5, cfg.maxSpeedBlocksPerTick, 1e-9);
  }

  @Test
  void validatePostLoad_belowMin_clampsToMin() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.effectHeightBlocks = 0.0;
    cfg.accelerationPerTick = -1.0;
    cfg.maxSpeedBlocksPerTick = 0.0;
    cfg.waterSprayHeightBlocks = 0.0;
    cfg.liftStrength = -0.5;
    cfg.effectSpeedThreshold = -0.1;
    cfg.validatePostLoad();
    assertEquals(1.0, cfg.effectHeightBlocks, 1e-9);
    assertEquals(0.0, cfg.accelerationPerTick, 1e-9);
    assertEquals(0.1, cfg.maxSpeedBlocksPerTick, 1e-9);
    assertEquals(1.0, cfg.waterSprayHeightBlocks, 1e-9);
    assertEquals(0.0, cfg.liftStrength, 1e-9);
    assertEquals(0.0, cfg.effectSpeedThreshold, 1e-9);
  }

  @Test
  void validatePostLoad_aboveMax_clampsToMax() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.effectHeightBlocks = 300.0;
    cfg.accelerationPerTick = 2.0;
    cfg.maxSpeedBlocksPerTick = 50.0;
    cfg.liftStrength = 1.5;
    cfg.effectSpeedThreshold = 2.0;
    cfg.validatePostLoad();
    assertEquals(256.0, cfg.effectHeightBlocks, 1e-9);
    assertEquals(1.0, cfg.accelerationPerTick, 1e-9);
    assertEquals(20.0, cfg.maxSpeedBlocksPerTick, 1e-9);
    assertEquals(1.0, cfg.liftStrength, 1e-9);
    assertEquals(1.0, cfg.effectSpeedThreshold, 1e-9);
  }

  @Test
  void validatePostLoad_waterSprayExceedsEffectHeight_clampsToEffectHeight() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.effectHeightBlocks = 10.0;
    cfg.waterSprayHeightBlocks = 15.0;
    cfg.validatePostLoad();
    assertEquals(10.0, cfg.waterSprayHeightBlocks, 1e-9);
  }

  @Test
  void validatePostLoad_validDefaults_unchanged() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.validatePostLoad();
    assertEquals(20.0, cfg.effectHeightBlocks, 1e-9);
    assertEquals(0.005, cfg.accelerationPerTick, 1e-9);
    assertEquals(1.5, cfg.maxSpeedBlocksPerTick, 1e-9);
    assertEquals(5.0, cfg.waterSprayHeightBlocks, 1e-9);
    assertEquals(0.6, cfg.liftStrength, 1e-9);
    assertEquals(0.3, cfg.effectSpeedThreshold, 1e-9);
  }

  @Test
  void newClientFields_defaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(true, cfg.soundsEnabled);
    assertEquals(1.0, cfg.soundVolume, 1e-9);
    assertEquals(true, cfg.fovKickEnabled);
    assertEquals(0.1, cfg.fovKickStrength, 1e-9);
    assertEquals(true, cfg.clientParticlesOnVanillaServers);
    assertEquals(true, cfg.remotePlayerParticles);
  }

  @Test
  void validatePostLoad_newFields_nanResetToDefaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.soundVolume = Double.NaN;
    cfg.fovKickStrength = Double.NaN;
    cfg.validatePostLoad();
    assertEquals(1.0, cfg.soundVolume, 1e-9);
    assertEquals(0.1, cfg.fovKickStrength, 1e-9);
  }

  @Test
  void validatePostLoad_newFields_clampToRange() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.soundVolume = 5.0;
    cfg.fovKickStrength = -1.0;
    cfg.validatePostLoad();
    assertEquals(2.0, cfg.soundVolume, 1e-9);
    assertEquals(0.0, cfg.fovKickStrength, 1e-9);

    cfg.soundVolume = -1.0;
    cfg.fovKickStrength = 3.0;
    cfg.validatePostLoad();
    assertEquals(0.0, cfg.soundVolume, 1e-9);
    assertEquals(0.5, cfg.fovKickStrength, 1e-9);
  }

  @Test
  void copy_isIndependentAndEqualFieldwise() {
    SlipstreamConfig original = new SlipstreamConfig();
    original.effectHeightBlocks = 33.0;
    original.soundVolume = 1.75;
    original.fovKickEnabled = false;
    original.remotePlayerParticles = false;

    SlipstreamConfig copy = original.copy();
    assertEquals(33.0, copy.effectHeightBlocks, 1e-9);
    assertEquals(1.75, copy.soundVolume, 1e-9);
    assertEquals(false, copy.fovKickEnabled);
    assertEquals(false, copy.remotePlayerParticles);

    copy.effectHeightBlocks = 99.0;
    copy.soundVolume = 0.1;
    assertEquals(33.0, original.effectHeightBlocks, 1e-9);
    assertEquals(1.75, original.soundVolume, 1e-9);
  }

  @Test
  void draftFields_defaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals(true, cfg.draftingEnabled);
    assertEquals(0.008, cfg.draftAccelerationPerTick, 1e-9);
    assertEquals(1.15, cfg.draftSpeedMultiplier, 1e-9);
    assertEquals(0.25, cfg.draftPullStrength, 1e-9);
    assertEquals(35.0, cfg.draftReleaseAngleDeg, 1e-9);
    assertEquals(1.5, cfg.wakeBaseRadius, 1e-9);
    assertEquals(1.2, cfg.wakeSpreadRate, 1e-9);
    assertEquals(60, cfg.wakeLifetimeTicks);
    assertEquals(2, cfg.wakeSampleIntervalTicks);
    assertEquals(0.15, cfg.draftLeaderBonusPerDrafter, 1e-9);
    assertEquals(3, cfg.draftLeaderBonusMaxDrafters);
    assertEquals(true, cfg.draftParticlesEnabled);
  }

  @Test
  void draftFields_nonFiniteResetToDefaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.draftAccelerationPerTick = Double.NaN;
    cfg.draftSpeedMultiplier = Double.POSITIVE_INFINITY;
    cfg.draftPullStrength = Double.NaN;
    cfg.draftReleaseAngleDeg = Double.NaN;
    cfg.wakeBaseRadius = Double.NaN;
    cfg.wakeSpreadRate = Double.NaN;
    cfg.draftLeaderBonusPerDrafter = Double.NaN;
    cfg.validatePostLoad();
    assertEquals(0.008, cfg.draftAccelerationPerTick, 1e-9);
    assertEquals(1.15, cfg.draftSpeedMultiplier, 1e-9);
    assertEquals(0.25, cfg.draftPullStrength, 1e-9);
    assertEquals(35.0, cfg.draftReleaseAngleDeg, 1e-9);
    assertEquals(1.5, cfg.wakeBaseRadius, 1e-9);
    assertEquals(1.2, cfg.wakeSpreadRate, 1e-9);
    assertEquals(0.15, cfg.draftLeaderBonusPerDrafter, 1e-9);
  }

  @Test
  void draftFields_clampToRange() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.draftAccelerationPerTick = 99.0;
    cfg.draftSpeedMultiplier = 99.0;
    cfg.draftPullStrength = 99.0;
    cfg.draftReleaseAngleDeg = 999.0;
    cfg.wakeBaseRadius = 999.0;
    cfg.wakeSpreadRate = 999.0;
    cfg.wakeLifetimeTicks = 99999;
    cfg.wakeSampleIntervalTicks = 999;
    cfg.draftLeaderBonusPerDrafter = 99.0;
    cfg.draftLeaderBonusMaxDrafters = 999;
    cfg.validatePostLoad();
    assertEquals(1.0, cfg.draftAccelerationPerTick, 1e-9);
    assertEquals(3.0, cfg.draftSpeedMultiplier, 1e-9);
    assertEquals(1.0, cfg.draftPullStrength, 1e-9);
    assertEquals(90.0, cfg.draftReleaseAngleDeg, 1e-9);
    assertEquals(32.0, cfg.wakeBaseRadius, 1e-9);
    assertEquals(32.0, cfg.wakeSpreadRate, 1e-9);
    assertEquals(200, cfg.wakeLifetimeTicks);
    assertEquals(20, cfg.wakeSampleIntervalTicks);
    assertEquals(1.0, cfg.draftLeaderBonusPerDrafter, 1e-9);
    assertEquals(16, cfg.draftLeaderBonusMaxDrafters);

    cfg.draftSpeedMultiplier = 0.1;
    cfg.draftReleaseAngleDeg = -5.0;
    cfg.wakeBaseRadius = 0.0;
    cfg.wakeLifetimeTicks = 0;
    cfg.wakeSampleIntervalTicks = 0;
    cfg.draftLeaderBonusMaxDrafters = -1;
    cfg.validatePostLoad();
    assertEquals(1.0, cfg.draftSpeedMultiplier, 1e-9);
    assertEquals(0.0, cfg.draftReleaseAngleDeg, 1e-9);
    assertEquals(0.1, cfg.wakeBaseRadius, 1e-9);
    assertEquals(1, cfg.wakeLifetimeTicks);
    assertEquals(1, cfg.wakeSampleIntervalTicks);
    assertEquals(0, cfg.draftLeaderBonusMaxDrafters);
  }

  @Test
  void versionHandshakeFields_defaults() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    assertEquals("disable", cfg.versionEnforcement);
    assertEquals(60, cfg.handshakeTimeoutTicks);
  }

  @Test
  void validatePostLoad_handshakeTimeoutTicks_clampsToRange() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.handshakeTimeoutTicks = 1;
    cfg.validatePostLoad();
    assertEquals(20, cfg.handshakeTimeoutTicks);

    cfg.handshakeTimeoutTicks = 10000;
    cfg.validatePostLoad();
    assertEquals(600, cfg.handshakeTimeoutTicks);

    cfg.handshakeTimeoutTicks = 120;
    cfg.validatePostLoad();
    assertEquals(120, cfg.handshakeTimeoutTicks);
  }

  @Test
  void validatePostLoad_versionEnforcement_acceptsRecognisedValues() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.versionEnforcement = "off";
    cfg.validatePostLoad();
    assertEquals("off", cfg.versionEnforcement);

    cfg.versionEnforcement = "kick";
    cfg.validatePostLoad();
    assertEquals("kick", cfg.versionEnforcement);

    cfg.versionEnforcement = "disable";
    cfg.validatePostLoad();
    assertEquals("disable", cfg.versionEnforcement);
  }

  @Test
  void validatePostLoad_versionEnforcement_unrecognisedValueFallsBackToDisable() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.versionEnforcement = "bogus";
    cfg.validatePostLoad();
    assertEquals("disable", cfg.versionEnforcement);
  }

  @Test
  void validatePostLoad_versionEnforcement_nullFallsBackToDisable() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.versionEnforcement = null;
    cfg.validatePostLoad();
    assertEquals("disable", cfg.versionEnforcement);
  }

  @Test
  void copy_includesVersionHandshakeFields() {
    SlipstreamConfig original = new SlipstreamConfig();
    original.versionEnforcement = "kick";
    original.handshakeTimeoutTicks = 100;

    SlipstreamConfig copy = original.copy();
    assertEquals("kick", copy.versionEnforcement);
    assertEquals(100, copy.handshakeTimeoutTicks);

    copy.versionEnforcement = "off";
    assertEquals("kick", original.versionEnforcement);
  }

  @Test
  void copy_includesDraftFields() {
    SlipstreamConfig original = new SlipstreamConfig();
    original.draftingEnabled = false;
    original.draftAccelerationPerTick = 0.02;
    original.wakeLifetimeTicks = 40;
    original.draftLeaderBonusMaxDrafters = 1;
    original.draftParticlesEnabled = false;

    SlipstreamConfig copy = original.copy();
    assertEquals(false, copy.draftingEnabled);
    assertEquals(0.02, copy.draftAccelerationPerTick, 1e-9);
    assertEquals(40, copy.wakeLifetimeTicks);
    assertEquals(1, copy.draftLeaderBonusMaxDrafters);
    assertEquals(false, copy.draftParticlesEnabled);

    copy.draftAccelerationPerTick = 0.5;
    assertEquals(0.02, original.draftAccelerationPerTick, 1e-9);
  }
}
