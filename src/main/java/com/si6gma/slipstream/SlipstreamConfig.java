package com.si6gma.slipstream;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "slipstream")
public class SlipstreamConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public double effectHeightBlocks = 20.0;

    @ConfigEntry.Gui.Tooltip
    public double accelerationPerTick = 0.01;

    @ConfigEntry.Gui.Tooltip
    public double maxSpeedBlocksPerTick = 3.0;

    @ConfigEntry.Gui.Tooltip
    public double waterSprayHeightBlocks = 5.0;

    @ConfigEntry.Gui.Tooltip
    public double liftStrength = 0.015;

    @ConfigEntry.Gui.Tooltip
    public double effectSpeedThreshold = 0.5;

    @Override
    public void validatePostLoad() throws ValidationException {
        effectHeightBlocks = Math.max(1.0, Math.min(effectHeightBlocks, 256.0));
        accelerationPerTick = Math.max(0.0, Math.min(accelerationPerTick, 1.0));
        maxSpeedBlocksPerTick = Math.max(0.1, Math.min(maxSpeedBlocksPerTick, 20.0));
        waterSprayHeightBlocks = Math.max(1.0, Math.min(waterSprayHeightBlocks, effectHeightBlocks));
        liftStrength = Math.max(0.0, Math.min(liftStrength, 1.0));
        effectSpeedThreshold = Math.max(0.0, Math.min(effectSpeedThreshold, 1.0));
    }
}
