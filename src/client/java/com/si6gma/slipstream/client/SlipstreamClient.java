package com.si6gma.slipstream.client;

import com.si6gma.slipstream.ModParticles;
import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.client.particle.WingVortexParticle;
import com.si6gma.slipstream.network.HelloPayload;
import com.si6gma.slipstream.network.ServerConfigOverride;
import com.si6gma.slipstream.network.ServerConfigPayload;
import com.si6gma.slipstream.network.SlipstreamProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.loader.api.FabricLoader;

public class SlipstreamClient implements ClientModInitializer {

  @Override
  public void onInitializeClient() {
    ParticleProviderRegistry.getInstance()
        .register(ModParticles.wingVortex(), WingVortexParticle.Factory::new);

    // Apply server config when received from either a Fabric server or the Paper
    // plugin.
    // Both send the same wire format: 6 big-endian doubles on channel
    // slipstream:server_config.
    ClientPlayNetworking.registerGlobalReceiver(
        ServerConfigPayload.TYPE,
        (payload, context) ->
            ServerConfigOverride.apply(
                payload.effectHeight(),
                payload.acceleration(),
                payload.maxSpeed(),
                payload.waterSprayHeight(),
                payload.liftStrength(),
                payload.effectSpeedThreshold(),
                payload.draft()));

    // Track singleplayer state so the mixin knows whether local boost is allowed, and tell the
    // server which protocol we speak so it can decide whether to send the config payload back.
    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          ServerConfigOverride.setSingleplayer(
              client.hasSingleplayerServer() && !client.getSingleplayerServer().isPublished());
          ClientPlayNetworking.send(new HelloPayload(SlipstreamProtocol.VERSION, modVersion()));
        });

    // Revert to local config on disconnect (singleplayer uses local config)
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          ServerConfigOverride.clear();
          ServerConfigOverride.setSingleplayer(false);
        });

    ClientFeelHandler.register();
  }

  /** Display only, sent alongside the protocol version so logs and messages name a version. */
  private static String modVersion() {
    return FabricLoader.getInstance()
        .getModContainer(Slipstream.MOD_ID)
        .map(container -> container.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");
  }
}
