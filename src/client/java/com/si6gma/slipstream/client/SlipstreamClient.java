package com.si6gma.slipstream.client;

import com.si6gma.slipstream.ModParticles;
import com.si6gma.slipstream.client.particle.WingVortexParticle;
import com.si6gma.slipstream.network.PaperConfigPayload;
import com.si6gma.slipstream.network.ServerConfigOverride;
import com.si6gma.slipstream.network.ServerConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class SlipstreamClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(ModParticles.WING_VORTEX, WingVortexParticle.Factory::new);

        PayloadTypeRegistry.clientboundPlay().register(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PaperConfigPayload.TYPE, PaperConfigPayload.CODEC);

        // Apply server config when received from a Fabric server
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) ->
                ServerConfigOverride.apply(
                        payload.effectHeight(),
                        payload.acceleration(),
                        payload.maxSpeed(),
                        payload.waterSprayHeight(),
                        payload.liftStrength(),
                        payload.effectSpeedThreshold()
                )
        );

        // Apply server config when received from the Paper plugin (raw plugin message bridge)
        ClientPlayNetworking.registerGlobalReceiver(PaperConfigPayload.TYPE, (payload, context) -> {
                try {
                    java.io.DataInputStream in = new java.io.DataInputStream(
                            new java.io.ByteArrayInputStream(payload.data()));
                    ServerConfigOverride.apply(
                            in.readDouble(), // effectHeight
                            in.readDouble(), // acceleration
                            in.readDouble(), // maxSpeed
                            in.readDouble(), // waterSprayHeight
                            in.readDouble(), // liftStrength
                            in.readDouble()  // effectSpeedThreshold
                    );
                } catch (java.io.IOException e) {
                    // Ignore malformed Paper plugin messages
                }
        });

        // Track singleplayer state so the mixin knows whether local boost is allowed
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ServerConfigOverride.setSingleplayer(client.isSingleplayer())
        );

        // Revert to local config on disconnect (singleplayer uses local config)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                ServerConfigOverride.clear();
                ServerConfigOverride.setSingleplayer(false);
        });
    }
}
