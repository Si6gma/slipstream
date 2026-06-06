package com.si6gma.slipstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.si6gma.slipstream.network.ServerConfigPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slipstream implements ModInitializer {

  public static final String MOD_ID = "slipstream";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static SlipstreamConfig config;

  @Override
  public void onInitialize() {
    config = loadConfig();
    ModParticles.register();

    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          if (!server.isDedicatedServer()) return;
          SlipstreamConfig cfg = getConfig();
          ServerPlayNetworking.send(
              handler.getPlayer(),
              new ServerConfigPayload(
                  cfg.effectHeightBlocks,
                  cfg.accelerationPerTick,
                  cfg.maxSpeedBlocksPerTick,
                  cfg.waterSprayHeightBlocks,
                  cfg.liftStrength,
                  cfg.effectSpeedThreshold));
        });

    LOGGER.info("Slipstream loaded.");
  }

  public static SlipstreamConfig getConfig() {
    return config;
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
