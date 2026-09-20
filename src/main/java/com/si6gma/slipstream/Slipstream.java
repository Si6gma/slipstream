package com.si6gma.slipstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.si6gma.slipstream.network.ServerConfigPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slipstream implements ModInitializer {

  public static final String MOD_ID = "slipstream";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static volatile SlipstreamConfig config;

  @Override
  public void onInitialize() {
    config = loadConfig();
    ModParticles.register();
    PayloadTypeRegistry.clientboundPlay()
        .register(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);

    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          // Skip only the singleplayer owner. LAN guests need the payload so they get the
          // boost and know not to render their own particles.
          if (!server.isDedicatedServer()
              && server.isSingleplayerOwner(handler.getPlayer().nameAndId())) {
            return;
          }
          SlipstreamConfig cfg = getConfig();
          ServerPlayNetworking.send(
              handler.getPlayer(),
              new ServerConfigPayload(
                  cfg.effectHeightBlocks,
                  cfg.accelerationPerTick,
                  cfg.maxSpeedBlocksPerTick,
                  cfg.waterSprayHeightBlocks,
                  cfg.liftStrength,
                  cfg.effectSpeedThreshold,
                  ServerConfigPayload.DraftSettings.from(cfg)));
        });

    LOGGER.info("Slipstream loaded.");
  }

  public static SlipstreamConfig getConfig() {
    return config;
  }

  /**
   * Replaces the live config with a validated snapshot and persists it. Publishing through the
   * volatile field means readers on the server thread always see a fully formed config, never a
   * half applied one.
   */
  public static void applyConfig(SlipstreamConfig updated) {
    updated.validatePostLoad();
    config = updated;
    saveConfig();
  }

  /** Writes the current in-memory config to slipstream.json. Called by the config screen. */
  public static void saveConfig() {
    Path configPath = FabricLoader.getInstance().getConfigDir().resolve("slipstream.json");
    try {
      Files.writeString(configPath, GSON.toJson(config));
    } catch (IOException e) {
      LOGGER.warn("Failed to write slipstream.json", e);
    }
  }

  private static SlipstreamConfig loadConfig() {
    Path configPath = FabricLoader.getInstance().getConfigDir().resolve("slipstream.json");
    if (Files.exists(configPath)) {
      try {
        SlipstreamConfig loaded =
            GSON.fromJson(Files.readString(configPath), SlipstreamConfig.class);
        if (loaded != null) {
          loaded.validatePostLoad();
          return loaded;
        }
      } catch (IOException e) {
        LOGGER.error("Failed to read slipstream.json, using defaults", e);
      } catch (JsonSyntaxException e) {
        LOGGER.error("slipstream.json contains invalid JSON, using defaults", e);
      }
    }
    SlipstreamConfig defaults = new SlipstreamConfig();
    try {
      Files.writeString(configPath, GSON.toJson(defaults));
    } catch (IOException e) {
      LOGGER.warn("Failed to write default slipstream.json", e);
    }
    return defaults;
  }
}
