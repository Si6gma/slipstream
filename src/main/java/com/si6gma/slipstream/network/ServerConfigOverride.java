package com.si6gma.slipstream.network;

import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.SlipstreamConfig;

/**
 * Holds a serverpushed config that overrides the player's local settings.
 * Only ever populated on the client side (via packet from Paper plugin or Fabric server).
 * Cleared on disconnect so singleplayer always reverts to local config.
 */
public final class ServerConfigOverride {

    private static volatile SlipstreamConfig active = null;
    private static volatile boolean singleplayer = false;

    public static void apply(double effectHeight, double acceleration,
                             double maxSpeed, double waterSprayHeight,
                             double liftStrength, double effectSpeedThreshold) {
        SlipstreamConfig cfg = new SlipstreamConfig();
        cfg.effectHeightBlocks = effectHeight;
        cfg.accelerationPerTick = acceleration;
        cfg.maxSpeedBlocksPerTick = maxSpeed;
        cfg.waterSprayHeightBlocks = waterSprayHeight;
        cfg.liftStrength = liftStrength;
        cfg.effectSpeedThreshold = effectSpeedThreshold;
        try {
            cfg.validatePostLoad();
        } catch (SlipstreamConfig.ValidationException e) {
            Slipstream.LOGGER.warn("Server config validation failed: {}", e.getMessage());
        }
        active = cfg;
        Slipstream.LOGGER.info(
                "Server config applied  effectHeight={}, maxSpeed={}", effectHeight, maxSpeed);
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

    /** Returns the server override if one was received, otherwise the player's local config. */
    public static SlipstreamConfig get() {
        SlipstreamConfig override = active;
        return override != null ? override : Slipstream.getConfig();
    }
}
