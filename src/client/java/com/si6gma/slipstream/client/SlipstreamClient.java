package com.si6gma.slipstream.client;

import com.si6gma.slipstream.ModParticles;
import com.si6gma.slipstream.client.particle.WingVortexParticle;
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

        // Apply server config when received from Paper plugin or Fabric server
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

        // Revert to local config on disconnect (singleplayer uses local config)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ServerConfigOverride.clear()
        );
    }
}
