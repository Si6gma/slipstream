package com.si6gma.slipstream;

public class SlipstreamConfig {

  public double effectHeightBlocks = 20.0;
  public double accelerationPerTick = 0.005;
  public double maxSpeedBlocksPerTick = 1.5;
  public double waterSprayHeightBlocks = 5.0;
  public double liftStrength = 0.6;
  public double effectSpeedThreshold = 0.3;
  public boolean particlesEnabled = true;

  // Client only. Never carried by ServerConfigPayload, never set by ServerConfigOverride.
  public boolean soundsEnabled = true;
  public double soundVolume = 1.0;
  public boolean fovKickEnabled = true;
  public double fovKickStrength = 0.1;
  public boolean clientParticlesOnVanillaServers = true;
  public boolean remotePlayerParticles = true;

  public void validatePostLoad() {
    if (!Double.isFinite(effectHeightBlocks)) effectHeightBlocks = 20.0;
    if (!Double.isFinite(accelerationPerTick)) accelerationPerTick = 0.005;
    if (!Double.isFinite(maxSpeedBlocksPerTick)) maxSpeedBlocksPerTick = 1.5;
    if (!Double.isFinite(waterSprayHeightBlocks)) waterSprayHeightBlocks = 5.0;
    if (!Double.isFinite(liftStrength)) liftStrength = 0.6;
    if (!Double.isFinite(effectSpeedThreshold)) effectSpeedThreshold = 0.3;
    if (!Double.isFinite(soundVolume)) soundVolume = 1.0;
    if (!Double.isFinite(fovKickStrength)) fovKickStrength = 0.1;
    effectHeightBlocks = Math.max(1.0, Math.min(effectHeightBlocks, 256.0));
    accelerationPerTick = Math.max(0.0, Math.min(accelerationPerTick, 1.0));
    maxSpeedBlocksPerTick = Math.max(0.1, Math.min(maxSpeedBlocksPerTick, 20.0));
    waterSprayHeightBlocks = Math.max(1.0, Math.min(waterSprayHeightBlocks, effectHeightBlocks));
    liftStrength = Math.max(0.0, Math.min(liftStrength, 1.0));
    effectSpeedThreshold = Math.max(0.0, Math.min(effectSpeedThreshold, 1.0));
    soundVolume = Math.max(0.0, Math.min(soundVolume, 2.0));
    fovKickStrength = Math.max(0.0, Math.min(fovKickStrength, 0.5));
  }
}
