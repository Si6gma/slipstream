package com.si6gma.slipstream.network;

import com.si6gma.slipstream.Slipstream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent by the Paper plugin (or a Fabric server) to push server-side config values to the Fabric
 * client, overriding the player's local settings. Uses raw double encoding so Paper can construct
 * the bytes without Fabric.
 */
public record ServerConfigPayload(
    double effectHeight,
    double acceleration,
    double maxSpeed,
    double waterSprayHeight,
    double liftStrength,
    double effectSpeedThreshold)
    implements CustomPacketPayload {

  public static final Type<ServerConfigPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "server_config"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigPayload> CODEC =
      StreamCodec.of(
          (buf, payload) -> {
            buf.writeDouble(payload.effectHeight());
            buf.writeDouble(payload.acceleration());
            buf.writeDouble(payload.maxSpeed());
            buf.writeDouble(payload.waterSprayHeight());
            buf.writeDouble(payload.liftStrength());
            buf.writeDouble(payload.effectSpeedThreshold());
          },
          buf ->
              new ServerConfigPayload(
                  buf.readDouble(),
                  buf.readDouble(),
                  buf.readDouble(),
                  buf.readDouble(),
                  buf.readDouble(),
                  buf.readDouble()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
