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
}
