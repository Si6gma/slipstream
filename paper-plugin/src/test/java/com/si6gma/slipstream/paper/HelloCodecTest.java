package com.si6gma.slipstream.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.si6gma.slipstream.paper.HelloCodec.Hello;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HelloCodec} against hand built byte arrays in the same wire format Fabric's
 * {@code FriendlyByteBuf.writeUtf} produces (a VarInt byte length, then UTF-8 bytes), since the
 * plugin has no Fabric dependency to encode with directly.
 */
class HelloCodecTest {

  @Test
  void decode_roundTrip_preservesProtocolAndVersionString() {
    byte[] message = encode(2, "1.0.3");

    Hello decoded = HelloCodec.decode(message);

    assertEquals(2, decoded.protocolVersion());
    assertEquals("1.0.3", decoded.modVersion());
  }

  @Test
  void decode_modVersionOverSixtyFourCharacters_returnsInvalidSentinelRatherThanThrowing() {
    byte[] message = encode(1, "v".repeat(65));

    Hello decoded = HelloCodec.decode(message);

    assertEquals(HelloCodec.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
    assertEquals("", decoded.modVersion());
  }

  @Test
  void decode_truncatedBuffer_returnsInvalidSentinelRatherThanThrowing() {
    byte[] message = {1}; // not even a full int, let alone a version string

    Hello decoded = HelloCodec.decode(message);

    assertEquals(HelloCodec.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
  }

  @Test
  void decode_emptyBuffer_returnsInvalidSentinelRatherThanThrowing() {
    Hello decoded = HelloCodec.decode(new byte[0]);

    assertEquals(HelloCodec.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
  }

  @Test
  void decode_declaredLengthLongerThanRemainingBytes_returnsInvalidSentinelRatherThanThrowing() {
    // A valid int and a VarInt claiming far more bytes than actually follow.
    byte[] message = {0, 0, 0, 1, 100, 'h', 'i'};

    Hello decoded = HelloCodec.decode(message);

    assertEquals(HelloCodec.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
  }

  @Test
  void decode_runawayVarInt_returnsInvalidSentinelRatherThanThrowing() {
    // Every continuation bit set, never terminating within the message.
    byte[] message = {0, 0, 0, 1, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80};

    Hello decoded = HelloCodec.decode(message);

    assertEquals(HelloCodec.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
  }

  /** Builds a message in the same format {@code HelloPayload.encode} writes on the Fabric side. */
  private static byte[] encode(int protocolVersion, String modVersion) {
    byte[] strBytes = modVersion.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write((protocolVersion >>> 24) & 0xFF);
    bytes.write((protocolVersion >>> 16) & 0xFF);
    bytes.write((protocolVersion >>> 8) & 0xFF);
    bytes.write(protocolVersion & 0xFF);
    writeVarInt(bytes, strBytes.length);
    try {
      bytes.write(strBytes);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return bytes.toByteArray();
  }

  private static void writeVarInt(ByteArrayOutputStream out, int value) {
    while (true) {
      if ((value & ~0x7F) == 0) {
        out.write(value);
        return;
      }
      out.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
  }
}
