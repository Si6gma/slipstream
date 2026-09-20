package com.si6gma.slipstream.client;

import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.SlipstreamConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the Cloth Config screen. Edits a draft config and publishes it atomically on save. */
public final class SlipstreamConfigScreen {

  private SlipstreamConfigScreen() {}

  public static Screen create(Screen parent) {
    // Edit a private draft, then publish it atomically on save. Never mutate the live config:
    // the server thread reads it every tick.
    SlipstreamConfig cfg = Slipstream.getConfig().copy();
    SlipstreamConfig defaults = new SlipstreamConfig();
    ConfigBuilder builder =
        ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("title.slipstream.config"));
    ConfigEntryBuilder e = builder.entryBuilder();
    Component serverNote = Component.translatable("tooltip.slipstream.serverOverride");

    ConfigCategory physics =
        builder.getOrCreateCategory(Component.translatable("category.slipstream.physics"));
    physics.addEntry(
        e.startDoubleField(label("effectHeightBlocks"), cfg.effectHeightBlocks)
            .setDefaultValue(defaults.effectHeightBlocks)
            .setMin(1.0)
            .setMax(256.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.effectHeightBlocks = v)
            .build());
    physics.addEntry(
        e.startDoubleField(label("accelerationPerTick"), cfg.accelerationPerTick)
            .setDefaultValue(defaults.accelerationPerTick)
            .setMin(0.0)
            .setMax(1.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.accelerationPerTick = v)
            .build());
    physics.addEntry(
        e.startDoubleField(label("maxSpeedBlocksPerTick"), cfg.maxSpeedBlocksPerTick)
            .setDefaultValue(defaults.maxSpeedBlocksPerTick)
            .setMin(0.1)
            .setMax(20.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.maxSpeedBlocksPerTick = v)
            .build());
    physics.addEntry(
        e.startDoubleField(label("liftStrength"), cfg.liftStrength)
            .setDefaultValue(defaults.liftStrength)
            .setMin(0.0)
            .setMax(1.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.liftStrength = v)
            .build());
    physics.addEntry(
        e.startDoubleField(label("effectSpeedThreshold"), cfg.effectSpeedThreshold)
            .setDefaultValue(defaults.effectSpeedThreshold)
            .setMin(0.0)
            .setMax(1.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.effectSpeedThreshold = v)
            .build());
    physics.addEntry(
        e.startDoubleField(label("waterSprayHeightBlocks"), cfg.waterSprayHeightBlocks)
            .setDefaultValue(defaults.waterSprayHeightBlocks)
            .setMin(1.0)
            .setMax(256.0)
            .setTooltip(
                serverNote,
                Component.translatable("tooltip.slipstream.sprayCappedByEffectHeight"))
            .setSaveConsumer(v -> cfg.waterSprayHeightBlocks = v)
            .build());

    ConfigCategory drafting =
        builder.getOrCreateCategory(Component.translatable("category.slipstream.drafting"));
    drafting.addEntry(
        e.startBooleanToggle(label("draftingEnabled"), cfg.draftingEnabled)
            .setDefaultValue(defaults.draftingEnabled)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.draftingEnabled = v)
            .build());
    drafting.addEntry(
        e.startDoubleField(label("draftAccelerationPerTick"), cfg.draftAccelerationPerTick)
            .setDefaultValue(defaults.draftAccelerationPerTick)
            .setMin(0.0)
            .setMax(1.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.draftAccelerationPerTick = v)
            .build());
    drafting.addEntry(
        e.startDoubleField(label("draftSpeedMultiplier"), cfg.draftSpeedMultiplier)
            .setDefaultValue(defaults.draftSpeedMultiplier)
            .setMin(1.0)
            .setMax(3.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.draftSpeedMultiplier = v)
            .build());
    drafting.addEntry(
        e.startDoubleField(label("draftPullStrength"), cfg.draftPullStrength)
            .setDefaultValue(defaults.draftPullStrength)
            .setMin(0.0)
            .setMax(1.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.draftPullStrength = v)
            .build());
    drafting.addEntry(
        e.startDoubleField(label("draftReleaseAngleDeg"), cfg.draftReleaseAngleDeg)
            .setDefaultValue(defaults.draftReleaseAngleDeg)
            .setMin(0.0)
            .setMax(90.0)
            .setTooltip(serverNote)
            .setSaveConsumer(v -> cfg.draftReleaseAngleDeg = v)
            .build());
    drafting.addEntry(
        e.startBooleanToggle(label("draftCameraAssist"), cfg.draftCameraAssist)
            .setDefaultValue(defaults.draftCameraAssist)
            .setSaveConsumer(v -> cfg.draftCameraAssist = v)
            .build());
    drafting.addEntry(
        e.startDoubleField(label("draftCameraAssistStrength"), cfg.draftCameraAssistStrength)
            .setDefaultValue(defaults.draftCameraAssistStrength)
            .setMin(0.0)
            .setMax(0.5)
            .setSaveConsumer(v -> cfg.draftCameraAssistStrength = v)
            .build());
    drafting.addEntry(
        e.startBooleanToggle(label("draftParticlesEnabled"), cfg.draftParticlesEnabled)
            .setDefaultValue(defaults.draftParticlesEnabled)
            .setSaveConsumer(v -> cfg.draftParticlesEnabled = v)
            .build());

    ConfigCategory visuals =
        builder.getOrCreateCategory(Component.translatable("category.slipstream.visuals"));
    visuals.addEntry(
        e.startBooleanToggle(label("particlesEnabled"), cfg.particlesEnabled)
            .setDefaultValue(defaults.particlesEnabled)
            .setSaveConsumer(v -> cfg.particlesEnabled = v)
            .build());
    visuals.addEntry(
        e.startBooleanToggle(
                label("clientParticlesOnVanillaServers"), cfg.clientParticlesOnVanillaServers)
            .setDefaultValue(defaults.clientParticlesOnVanillaServers)
            .setSaveConsumer(v -> cfg.clientParticlesOnVanillaServers = v)
            .build());
    visuals.addEntry(
        e.startBooleanToggle(label("remotePlayerParticles"), cfg.remotePlayerParticles)
            .setDefaultValue(defaults.remotePlayerParticles)
            .setSaveConsumer(v -> cfg.remotePlayerParticles = v)
            .build());
    visuals.addEntry(
        e.startBooleanToggle(label("fovKickEnabled"), cfg.fovKickEnabled)
            .setDefaultValue(defaults.fovKickEnabled)
            .setSaveConsumer(v -> cfg.fovKickEnabled = v)
            .build());
    visuals.addEntry(
        e.startDoubleField(label("fovKickStrength"), cfg.fovKickStrength)
            .setDefaultValue(defaults.fovKickStrength)
            .setMin(0.0)
            .setMax(0.5)
            .setSaveConsumer(v -> cfg.fovKickStrength = v)
            .build());

    ConfigCategory audio =
        builder.getOrCreateCategory(Component.translatable("category.slipstream.audio"));
    audio.addEntry(
        e.startBooleanToggle(label("soundsEnabled"), cfg.soundsEnabled)
            .setDefaultValue(defaults.soundsEnabled)
            .setSaveConsumer(v -> cfg.soundsEnabled = v)
            .build());
    audio.addEntry(
        e.startDoubleField(label("soundVolume"), cfg.soundVolume)
            .setDefaultValue(defaults.soundVolume)
            .setMin(0.0)
            .setMax(2.0)
            .setSaveConsumer(v -> cfg.soundVolume = v)
            .build());

    builder.setSavingRunnable(() -> Slipstream.applyConfig(cfg));
    return builder.build();
  }

  private static Component label(String field) {
    return Component.translatable("option.slipstream." + field);
  }
}
