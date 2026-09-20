package com.si6gma.slipstream.paper;

import java.nio.charset.StandardCharsets;

/**
 * Decodes the {@code slipstream:hello} payload sent once by a Fabric client on join, so the
 * plugin can classify which version it is talking to. Kept free of any Bukkit type so it can be
 * tested without a server on the classpath.
 *
 * <p>Wire format matches {@code HelloPayload} on the Fabric side: a 4 byte big-endian int
 * protocol version, followed by the mod version encoded the way {@code FriendlyByteBuf.writeUtf}
 * writes it: a VarInt byte length, then that many UTF-8 bytes. The plugin has no Fabric dependency
 * to decode with directly, so this reimplements just that much of the format by hand.
 *
 * <p>A short, oversized, or otherwise malformed message is treated as mismatched rather than
 * thrown, matching {@code HelloPayload.decode} on the Fabric side.
 */
final class HelloCodec {

  /** A {@code modVersion} longer than this is rejected rather than trusted. */
  static final int MAX_MOD_VERSION_LENGTH = 64;

  /**
   * Returned by {@link #decode} in place of throwing when a hello fails to decode. Callers treat
   * this the same as a protocol mismatch.
   */
  static final int INVALID_PROTOCOL_VERSION = -1;

  private static final Hello INVALID = new Hello(INVALID_PROTOCOL_VERSION, "");

  /** Longest a UTF-8 encoding of {@link #MAX_MOD_VERSION_LENGTH} characters could be. */
  private static final int MAX_MOD_VERSION_BYTES = MAX_MOD_VERSION_LENGTH * 4;

  private static final int VARINT_MAX_BYTES = 5;

  private HelloCodec() {}

  record Hello(int protocolVersion, String modVersion) {}

  static Hello decode(byte[] message) {
    if (message.length < Integer.BYTES) {
      return INVALID;
    }
    int protocolVersion = readInt(message);
    int[] cursor = {Integer.BYTES};
    int length = readVarInt(message, cursor);
    if (length < 0 || length > MAX_MOD_VERSION_BYTES) {
      return INVALID;
    }
    int start = cursor[0];
    if (start + length > message.length) {
      return INVALID;
    }
    String modVersion = new String(message, start, length, StandardCharsets.UTF_8);
    if (modVersion.length() > MAX_MOD_VERSION_LENGTH) {
      return INVALID;
    }
    return new Hello(protocolVersion, modVersion);
  }

  private static int readInt(byte[] data) {
    return ((data[0] & 0xFF) << 24)
        | ((data[1] & 0xFF) << 16)
        | ((data[2] & 0xFF) << 8)
        | (data[3] & 0xFF);
  }

  /** Reads a Minecraft protocol VarInt starting at {@code cursor[0]}, advancing it past it. */
  private static int readVarInt(byte[] data, int[] cursor) {
    int value = 0;
    int shift = 0;
    int pos = cursor[0];
    for (int i = 0; i < VARINT_MAX_BYTES; i++) {
      if (pos >= data.length) {
        return -1;
      }
      byte b = data[pos++];
      value |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        cursor[0] = pos;
        return value;
      }
      shift += 7;
    }
    return -1; // VarInt never terminated within its 5 byte limit.
  }
}
