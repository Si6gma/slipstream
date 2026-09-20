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

  // Drafting. Physics values are server pushed; draftParticlesEnabled is client only.
  public boolean draftingEnabled = true;
  public double draftAccelerationPerTick = 0.008;
  public double draftSpeedMultiplier = 1.15;
  public double draftPullStrength = 0.25;
  public double draftReleaseAngleDeg = 35.0;
  public double wakeBaseRadius = 1.5;
  public double wakeSpreadRate = 1.2;
  public int wakeLifetimeTicks = 60;
  public int wakeSampleIntervalTicks = 2;
  public double draftLeaderBonusPerDrafter = 0.15;
  public int draftLeaderBonusMaxDrafters = 3;
  public boolean draftParticlesEnabled = true;

  public void validatePostLoad() {
    if (!Double.isFinite(effectHeightBlocks)) effectHeightBlocks = 20.0;
    if (!Double.isFinite(accelerationPerTick)) accelerationPerTick = 0.005;
    if (!Double.isFinite(maxSpeedBlocksPerTick)) maxSpeedBlocksPerTick = 1.5;
    if (!Double.isFinite(waterSprayHeightBlocks)) waterSprayHeightBlocks = 5.0;
    if (!Double.isFinite(liftStrength)) liftStrength = 0.6;
    if (!Double.isFinite(effectSpeedThreshold)) effectSpeedThreshold = 0.3;
    if (!Double.isFinite(soundVolume)) soundVolume = 1.0;
    if (!Double.isFinite(fovKickStrength)) fovKickStrength = 0.1;
    if (!Double.isFinite(draftAccelerationPerTick)) draftAccelerationPerTick = 0.008;
    if (!Double.isFinite(draftSpeedMultiplier)) draftSpeedMultiplier = 1.15;
    if (!Double.isFinite(draftPullStrength)) draftPullStrength = 0.25;
    if (!Double.isFinite(draftReleaseAngleDeg)) draftReleaseAngleDeg = 35.0;
    if (!Double.isFinite(wakeBaseRadius)) wakeBaseRadius = 1.5;
    if (!Double.isFinite(wakeSpreadRate)) wakeSpreadRate = 1.2;
    if (!Double.isFinite(draftLeaderBonusPerDrafter)) draftLeaderBonusPerDrafter = 0.15;
    effectHeightBlocks = Math.max(1.0, Math.min(effectHeightBlocks, 256.0));
    accelerationPerTick = Math.max(0.0, Math.min(accelerationPerTick, 1.0));
    maxSpeedBlocksPerTick = Math.max(0.1, Math.min(maxSpeedBlocksPerTick, 20.0));
    waterSprayHeightBlocks = Math.max(1.0, Math.min(waterSprayHeightBlocks, effectHeightBlocks));
    liftStrength = Math.max(0.0, Math.min(liftStrength, 1.0));
    effectSpeedThreshold = Math.max(0.0, Math.min(effectSpeedThreshold, 1.0));
    soundVolume = Math.max(0.0, Math.min(soundVolume, 2.0));
    fovKickStrength = Math.max(0.0, Math.min(fovKickStrength, 0.5));
    draftAccelerationPerTick = Math.max(0.0, Math.min(draftAccelerationPerTick, 1.0));
    draftSpeedMultiplier = Math.max(1.0, Math.min(draftSpeedMultiplier, 3.0));
    draftPullStrength = Math.max(0.0, Math.min(draftPullStrength, 1.0));
    draftReleaseAngleDeg = Math.max(0.0, Math.min(draftReleaseAngleDeg, 90.0));
    wakeBaseRadius = Math.max(0.1, Math.min(wakeBaseRadius, 32.0));
    wakeSpreadRate = Math.max(0.0, Math.min(wakeSpreadRate, 32.0));
    wakeLifetimeTicks = Math.max(1, Math.min(wakeLifetimeTicks, 200));
    wakeSampleIntervalTicks = Math.max(1, Math.min(wakeSampleIntervalTicks, 20));
    draftLeaderBonusPerDrafter = Math.max(0.0, Math.min(draftLeaderBonusPerDrafter, 1.0));
    draftLeaderBonusMaxDrafters = Math.max(0, Math.min(draftLeaderBonusMaxDrafters, 16));
  }

  /** Returns an independent copy. Used so edits never mutate a config another thread is reading. */
  public SlipstreamConfig copy() {
    SlipstreamConfig c = new SlipstreamConfig();
    c.effectHeightBlocks = effectHeightBlocks;
    c.accelerationPerTick = accelerationPerTick;
    c.maxSpeedBlocksPerTick = maxSpeedBlocksPerTick;
    c.waterSprayHeightBlocks = waterSprayHeightBlocks;
    c.liftStrength = liftStrength;
    c.effectSpeedThreshold = effectSpeedThreshold;
    c.particlesEnabled = particlesEnabled;
    c.soundsEnabled = soundsEnabled;
    c.soundVolume = soundVolume;
    c.fovKickEnabled = fovKickEnabled;
    c.fovKickStrength = fovKickStrength;
    c.clientParticlesOnVanillaServers = clientParticlesOnVanillaServers;
    c.remotePlayerParticles = remotePlayerParticles;
    c.draftingEnabled = draftingEnabled;
    c.draftAccelerationPerTick = draftAccelerationPerTick;
    c.draftSpeedMultiplier = draftSpeedMultiplier;
    c.draftPullStrength = draftPullStrength;
    c.draftReleaseAngleDeg = draftReleaseAngleDeg;
    c.wakeBaseRadius = wakeBaseRadius;
    c.wakeSpreadRate = wakeSpreadRate;
    c.wakeLifetimeTicks = wakeLifetimeTicks;
    c.wakeSampleIntervalTicks = wakeSampleIntervalTicks;
    c.draftLeaderBonusPerDrafter = draftLeaderBonusPerDrafter;
    c.draftLeaderBonusMaxDrafters = draftLeaderBonusMaxDrafters;
    c.draftParticlesEnabled = draftParticlesEnabled;
    return c;
  }
}
