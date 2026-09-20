package com.si6gma.slipstream.network;

import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.SlipstreamConfig;

/**
 * Holds a server pushed config that overrides the player's local settings. Only ever populated on
 * the client side (via packet from Paper plugin or Fabric server). Cleared on disconnect so
 * singleplayer always reverts to local config.
 */
public final class ServerConfigOverride {

  private static volatile SlipstreamConfig active = null;
  private static volatile boolean singleplayer = false;
  private static volatile SlipstreamConfig localForTests = null;

  private ServerConfigOverride() {}

  public static void apply(
      double effectHeight,
      double acceleration,
      double maxSpeed,
      double waterSprayHeight,
      double liftStrength,
      double effectSpeedThreshold,
      ServerConfigPayload.DraftSettings draft) {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.effectHeightBlocks = effectHeight;
    cfg.accelerationPerTick = acceleration;
    cfg.maxSpeedBlocksPerTick = maxSpeed;
    cfg.waterSprayHeightBlocks = waterSprayHeight;
    cfg.liftStrength = liftStrength;
    cfg.effectSpeedThreshold = effectSpeedThreshold;
    if (draft != null) {
      cfg.draftingEnabled = draft.enabled();
      cfg.draftAccelerationPerTick = draft.acceleration();
      cfg.draftSpeedMultiplier = draft.speedMultiplier();
      cfg.draftPullStrength = draft.pullStrength();
      cfg.draftReleaseAngleDeg = draft.releaseAngleDeg();
      cfg.wakeBaseRadius = draft.wakeBaseRadius();
      cfg.wakeSpreadRate = draft.wakeSpreadRate();
      cfg.wakeLifetimeTicks = draft.wakeLifetimeTicks();
      cfg.wakeSampleIntervalTicks = draft.wakeSampleIntervalTicks();
      cfg.draftLeaderBonusPerDrafter = draft.leaderBonusPerDrafter();
      cfg.draftLeaderBonusMaxDrafters = draft.leaderBonusMaxDrafters();
    }
    cfg.validatePostLoad();
    active = cfg;
    Slipstream.LOGGER.info(
        "Server config applied: effectHeight={}, maxSpeed={}", effectHeight, maxSpeed);
  }

  public static void clear() {
    if (active != null) {
      active = null;
      Slipstream.LOGGER.info("Server config cleared, reverting to local config.");
    }
  }

  public static boolean isActive() {
    return active != null;
  }

  public static void setSingleplayer(boolean value) {
    singleplayer = value;
  }

  /** True if local boost is allowed (singleplayer or server has the mod). */
  public static boolean isBoostAllowed() {
    return singleplayer || active != null;
  }

  /** Test hook: substitute the local config without touching the config file. */
  public static void setLocalConfigForTests(SlipstreamConfig cfg) {
    localForTests = cfg;
  }

  private static SlipstreamConfig local() {
    SlipstreamConfig test = localForTests;
    return test != null ? test : Slipstream.getConfig();
  }

  /**
   * Returns the effective config. Without a server override this is the local config itself.
   * With one, the physics fields (including the drafting physics fields) come from the server
   * and every other field, including the client-only {@code draftParticlesEnabled}, is copied
   * fresh from the local config, so a server can never change client-only preferences and
   * config screen edits take effect immediately.
   */
  public static SlipstreamConfig get() {
    SlipstreamConfig override = active;
    SlipstreamConfig local = local();
    if (override == null) return local;
    SlipstreamConfig merged = new SlipstreamConfig();
    merged.effectHeightBlocks = override.effectHeightBlocks;
    merged.accelerationPerTick = override.accelerationPerTick;
    merged.maxSpeedBlocksPerTick = override.maxSpeedBlocksPerTick;
    merged.waterSprayHeightBlocks = override.waterSprayHeightBlocks;
    merged.liftStrength = override.liftStrength;
    merged.effectSpeedThreshold = override.effectSpeedThreshold;
    merged.draftingEnabled = override.draftingEnabled;
    merged.draftAccelerationPerTick = override.draftAccelerationPerTick;
    merged.draftSpeedMultiplier = override.draftSpeedMultiplier;
    merged.draftPullStrength = override.draftPullStrength;
    merged.draftReleaseAngleDeg = override.draftReleaseAngleDeg;
    merged.wakeBaseRadius = override.wakeBaseRadius;
    merged.wakeSpreadRate = override.wakeSpreadRate;
    merged.wakeLifetimeTicks = override.wakeLifetimeTicks;
    merged.wakeSampleIntervalTicks = override.wakeSampleIntervalTicks;
    merged.draftLeaderBonusPerDrafter = override.draftLeaderBonusPerDrafter;
    merged.draftLeaderBonusMaxDrafters = override.draftLeaderBonusMaxDrafters;
    merged.draftParticlesEnabled = local.draftParticlesEnabled;
    merged.particlesEnabled = local.particlesEnabled;
    merged.soundsEnabled = local.soundsEnabled;
    merged.soundVolume = local.soundVolume;
    merged.fovKickEnabled = local.fovKickEnabled;
    merged.fovKickStrength = local.fovKickStrength;
    merged.clientParticlesOnVanillaServers = local.clientParticlesOnVanillaServers;
    merged.remotePlayerParticles = local.remotePlayerParticles;
    return merged;
  }
}
