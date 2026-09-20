package com.si6gma.slipstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.si6gma.slipstream.network.HelloPayload;
import com.si6gma.slipstream.network.ServerConfigPayload;
import com.si6gma.slipstream.network.SlipstreamProtocol;
import com.si6gma.slipstream.network.VersionPolicy;
import com.si6gma.slipstream.network.VersionPolicy.Action;
import com.si6gma.slipstream.network.VersionPolicy.ClientState;
import com.si6gma.slipstream.network.VersionPolicy.EnforcementPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slipstream implements ModInitializer {

  public static final String MOD_ID = "slipstream";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static volatile SlipstreamConfig config;

  // Players who announced the mod's channel on join but have not yet been classified. Populated
  // on join, emptied when a hello arrives, the deadline passes, or the player disconnects.
  private static final Map<UUID, Integer> pendingHandshakes = new HashMap<>();

  @Override
  public void onInitialize() {
    config = loadConfig();
    ModParticles.register();
    PayloadTypeRegistry.clientboundPlay()
        .register(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);

    ServerPlayNetworking.registerGlobalReceiver(
        HelloPayload.TYPE, (payload, context) -> onHello(context.player(), payload));

    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          ServerPlayer player = handler.getPlayer();
          // Skip only the singleplayer owner. LAN guests still complete the handshake so they
          // get the payload and know not to render their own particles.
          if (!server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId())) {
            return;
          }
          if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.TYPE)) {
            // Vanilla: never announced our channel. Nothing sent, nothing enforced.
            return;
          }
          int deadlineTick = server.getTickCount() + getConfig().handshakeTimeoutTicks;
          pendingHandshakes.put(player.getUUID(), deadlineTick);
        });

    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> pendingHandshakes.remove(handler.getPlayer().getUUID()));

    ServerTickEvents.END_SERVER_TICK.register(Slipstream::checkHandshakeDeadlines);

    LOGGER.info("Slipstream loaded.");
  }

  private static void onHello(ServerPlayer player, HelloPayload payload) {
    // Classification happens once: a second hello, or one from a player not tracked as
    // pending (already classified), is ignored.
    if (pendingHandshakes.remove(player.getUUID()) == null) {
      return;
    }
    ClientState state =
        payload.protocolVersion() == SlipstreamProtocol.VERSION
            ? ClientState.COMPATIBLE
            : ClientState.MISMATCHED;
    classifyAndAct(player, state, payload.protocolVersion());
  }

  private static void checkHandshakeDeadlines(MinecraftServer server) {
    if (pendingHandshakes.isEmpty()) return;
    int tick = server.getTickCount();
    Iterator<Map.Entry<UUID, Integer>> it = pendingHandshakes.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Integer> entry = it.next();
      if (tick < entry.getValue()) continue;
      it.remove();
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      if (player != null) {
        classifyAndAct(player, ClientState.LEGACY, HelloPayload.INVALID_PROTOCOL_VERSION);
      }
    }
  }

  private static void classifyAndAct(ServerPlayer player, ClientState state, int clientProtocol) {
    SlipstreamConfig cfg = getConfig();
    Action action = VersionPolicy.decide(state, enforcementPolicyFrom(cfg.versionEnforcement));
    switch (action) {
      case SEND_CONFIG -> sendConfigPayload(player, cfg);
      case WITHHOLD_AND_MESSAGE ->
          player.sendSystemMessage(Component.literal(disableMessage(clientProtocol)));
      case KICK ->
          player.connection.disconnect(Component.literal(kickMessage(clientProtocol)));
      case DO_NOTHING -> {}
    }
  }

  private static EnforcementPolicy enforcementPolicyFrom(String value) {
    return switch (value) {
      case "off" -> EnforcementPolicy.OFF;
      case "kick" -> EnforcementPolicy.KICK;
      default -> EnforcementPolicy.DISABLE;
    };
  }

  /** The client's protocol is unknown when it never reported one, whether legacy or malformed. */
  private static String reasonSentence(int clientProtocol) {
    if (clientProtocol == HelloPayload.INVALID_PROTOCOL_VERSION) {
      return "Your version is too old to report its protocol.";
    }
    return "This server runs protocol "
        + SlipstreamProtocol.VERSION
        + " and your version speaks protocol "
        + clientProtocol
        + ".";
  }

  private static String disableMessage(int clientProtocol) {
    return "Slipstream effects are disabled here. "
        + reasonSentence(clientProtocol)
        + " Update Slipstream to use it on this server.";
  }

  private static String kickMessage(int clientProtocol) {
    return "Slipstream effects are disabled here. "
        + reasonSentence(clientProtocol)
        + " Update Slipstream to join this server.";
  }

  private static void sendConfigPayload(ServerPlayer player, SlipstreamConfig cfg) {
    ServerPlayNetworking.send(
        player,
        new ServerConfigPayload(
            cfg.effectHeightBlocks,
            cfg.accelerationPerTick,
            cfg.maxSpeedBlocksPerTick,
            cfg.waterSprayHeightBlocks,
            cfg.liftStrength,
            cfg.effectSpeedThreshold,
            ServerConfigPayload.DraftSettings.from(cfg)));
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
