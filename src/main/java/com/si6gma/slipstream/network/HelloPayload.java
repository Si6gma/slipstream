package com.si6gma.slipstream.network;

import com.si6gma.slipstream.Slipstream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent once by the Fabric client on join, on channel {@code slipstream:hello}, so the server can
 * classify which version it is talking to. Nothing else ever sends it, and the server never
 * replies on this channel; the config payload sent in response travels on its own channel.
 *
 * <p>{@code modVersion} is display only, for logs and player-facing messages, and is never
 * compared. It uses the standard length-prefixed UTF encoding so the Paper plugin can read it
 * without any Fabric dependency.
 */
public record HelloPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {

  /** A {@code modVersion} longer than this is rejected rather than trusted. */
  public static final int MAX_MOD_VERSION_LENGTH = 64;

  /**
   * Returned by {@link #decode} in place of throwing when a hello fails to decode: an oversized
   * {@code modVersion} or a truncated buffer. Callers treat this the same as a protocol mismatch.
   */
  public static final int INVALID_PROTOCOL_VERSION = -1;

  public static final Type<HelloPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "hello"));

  public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC =
      StreamCodec.of((buf, payload) -> encode(buf, payload), buf -> decode(buf));

  /** Package visible for the codec and for tests. */
  static void encode(FriendlyByteBuf buf, HelloPayload payload) {
    buf.writeInt(payload.protocolVersion());
    buf.writeUtf(payload.modVersion(), MAX_MOD_VERSION_LENGTH);
  }

  /**
   * Package visible for the codec and for tests. A malformed handshake, whether truncated or
   * carrying an oversized {@code modVersion}, is not a reason to drop the connection with a stack
   * trace: this returns {@link #INVALID_PROTOCOL_VERSION} instead of throwing.
   */
  static HelloPayload decode(FriendlyByteBuf buf) {
    try {
      int protocolVersion = buf.readInt();
      String modVersion = buf.readUtf(MAX_MOD_VERSION_LENGTH);
      return new HelloPayload(protocolVersion, modVersion);
    } catch (RuntimeException e) {
      return new HelloPayload(INVALID_PROTOCOL_VERSION, "");
    }
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
