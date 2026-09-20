package com.si6gma.slipstream.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class HelloPayloadTest {

  @Test
  void roundTrip_preservesProtocolAndVersionString() {
    HelloPayload original = new HelloPayload(SlipstreamProtocol.VERSION, "1.0.3");

    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    HelloPayload.encode(buf, original);
    HelloPayload decoded = HelloPayload.decode(buf);

    assertEquals(SlipstreamProtocol.VERSION, decoded.protocolVersion());
    assertEquals("1.0.3", decoded.modVersion());
  }

  @Test
  void decode_modVersionOverSixtyFourCharacters_returnsInvalidSentinelRatherThanThrowing() {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeInt(1);
    // Written without HelloPayload's 64 char cap, as a client ignoring the limit would.
    buf.writeUtf("v".repeat(65));

    HelloPayload decoded = assertDoesNotThrow(() -> HelloPayload.decode(buf));

    assertEquals(HelloPayload.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
    assertEquals("", decoded.modVersion());
  }

  @Test
  void decode_truncatedBuffer_returnsInvalidSentinelRatherThanThrowing() {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(1); // not even a full int, let alone a version string

    HelloPayload decoded = assertDoesNotThrow(() -> HelloPayload.decode(buf));

    assertEquals(HelloPayload.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
  }

  @Test
  void decode_emptyBuffer_returnsInvalidSentinelRatherThanThrowing() {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

    HelloPayload decoded = assertDoesNotThrow(() -> HelloPayload.decode(buf));

    assertEquals(HelloPayload.INVALID_PROTOCOL_VERSION, decoded.protocolVersion());
  }
}
