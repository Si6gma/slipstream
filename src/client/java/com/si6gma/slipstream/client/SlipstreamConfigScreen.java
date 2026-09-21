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

    // Only presentation and the few choices a player actually makes live here. Every physics
    // value is server pushed, so a field for it would do nothing on a server that has the mod
    // and would read as broken; the config file stays the place to tune singleplayer.

    ConfigCategory drafting =
        builder.getOrCreateCategory(Component.translatable("category.slipstream.drafting"));
    drafting.addEntry(
        e.startBooleanToggle(label("draftingEnabled"), cfg.draftingEnabled)
            .setDefaultValue(defaults.draftingEnabled)
            .setSaveConsumer(v -> cfg.draftingEnabled = v)
            .setTooltip(serverNote)
            .build());
    // Kept in the GUI where the rest of the camera assist tuning is not: a server sets its
    // strength, but switching it off is an accessibility choice, and someone it makes ill should
    // not have to find a JSON file to stop their view being moved.
    drafting.addEntry(
        e.startBooleanToggle(label("draftCameraAssist"), cfg.draftCameraAssist)
            .setDefaultValue(defaults.draftCameraAssist)
            .setSaveConsumer(v -> cfg.draftCameraAssist = v)
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
