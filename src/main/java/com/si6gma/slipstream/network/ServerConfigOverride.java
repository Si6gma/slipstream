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

    /** Returns the server override if one was received, otherwise the player's local config. */
    public static SlipstreamConfig get() {
        SlipstreamConfig override = active;
        return override != null ? override : Slipstream.getConfig();
    }
}
