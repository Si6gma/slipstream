package com.si6gma.slipstream.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bridges Paper plugin messages (raw bytes on channel slipstream:server_config)
 * into Fabric's typed payload system so the client can receive them.
 */
public record PaperConfigPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<PaperConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("slipstream", "server_config"));

    public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, PaperConfigPayload> CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data()),
                    buf -> {
                        byte[] data = new byte[buf.readableBytes()];
                        buf.readBytes(data);
                        return new PaperConfigPayload(data);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
