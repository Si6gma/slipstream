package com.si6gma.slipstream.client;

import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.SlipstreamConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the Cloth Config screen. Edits the live config in place and saves on close. */
public final class SlipstreamConfigScreen {

  private SlipstreamConfigScreen() {}

  public static Screen create(Screen parent) {
    SlipstreamConfig cfg = Slipstream.getConfig();
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

    builder.setSavingRunnable(
        () -> {
          cfg.validatePostLoad();
          Slipstream.saveConfig();
        });
    return builder.build();
  }

  private static Component label(String field) {
    return Component.translatable("option.slipstream." + field);
  }
}
