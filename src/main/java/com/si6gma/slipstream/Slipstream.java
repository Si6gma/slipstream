package com.si6gma.slipstream;

import com.si6gma.slipstream.network.ServerConfigPayload;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slipstream implements ModInitializer {

  public static final String MOD_ID = "slipstream";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitialize() {
    AutoConfig.register(SlipstreamConfig.class, GsonConfigSerializer::new);
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
    return AutoConfig.getConfigHolder(SlipstreamConfig.class).getConfig();
  }
}
