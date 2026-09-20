package com.si6gma.slipstream.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.si6gma.slipstream.SlipstreamConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ServerConfigPayloadTest {

  /** Writes only the six original doubles, exactly as a 1.0.x Paper plugin does. */
  private static FriendlyByteBuf legacyBuffer() {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeDouble(20.0);
    buf.writeDouble(0.005);
    buf.writeDouble(1.5);
    buf.writeDouble(5.0);
    buf.writeDouble(0.6);
    buf.writeDouble(0.3);
    return buf;
  }

  @Test
  void decode_legacyPayload_leavesDraftSettingsAbsent() {
    ServerConfigPayload payload = ServerConfigPayload.decode(legacyBuffer());
    assertEquals(20.0, payload.effectHeight(), 1e-9);
    assertEquals(0.3, payload.effectSpeedThreshold(), 1e-9);
    assertNull(payload.draft(), "an older server sends no drafting block");
  }

  @Test
  void roundTrip_withDraftSettings_preservesEveryField() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.draftingEnabled = false;
    cfg.draftAccelerationPerTick = 0.02;
    cfg.draftSpeedMultiplier = 1.4;
    cfg.draftPullStrength = 0.9;
    cfg.draftReleaseAngleDeg = 50.0;
    cfg.wakeBaseRadius = 2.5;
    cfg.wakeSpreadRate = 0.4;
    cfg.wakeLifetimeTicks = 80;
    cfg.wakeSampleIntervalTicks = 3;
    cfg.draftLeaderBonusPerDrafter = 0.3;
    cfg.draftLeaderBonusMaxDrafters = 2;

    ServerConfigPayload original =
        new ServerConfigPayload(
            20.0, 0.005, 1.5, 5.0, 0.6, 0.3, ServerConfigPayload.DraftSettings.from(cfg));

    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    ServerConfigPayload.encode(buf, original);
    ServerConfigPayload decoded = ServerConfigPayload.decode(buf);

    assertNotNull(decoded.draft());
    assertEquals(false, decoded.draft().enabled());
    assertEquals(0.02, decoded.draft().acceleration(), 1e-9);
    assertEquals(1.4, decoded.draft().speedMultiplier(), 1e-9);
    assertEquals(0.9, decoded.draft().pullStrength(), 1e-9);
    assertEquals(50.0, decoded.draft().releaseAngleDeg(), 1e-9);
    assertEquals(2.5, decoded.draft().wakeBaseRadius(), 1e-9);
    assertEquals(0.4, decoded.draft().wakeSpreadRate(), 1e-9);
    assertEquals(80, decoded.draft().wakeLifetimeTicks());
    assertEquals(3, decoded.draft().wakeSampleIntervalTicks());
    assertEquals(0.3, decoded.draft().leaderBonusPerDrafter(), 1e-9);
    assertEquals(2, decoded.draft().leaderBonusMaxDrafters());
  }

  @Test
  void decode_truncatedDraftingBlock_keepsPhysicsAndDropsDrafting() {
    // A plugin that writes a partial drafting block, or a proxy that truncates, must not throw
    // inside the netty decoder: that disconnects the player with an Internal Exception screen.
    FriendlyByteBuf buf = legacyBuffer();
    buf.writeBoolean(true);
    buf.writeDouble(0.02); // and then the message stops mid block
    ServerConfigPayload payload = ServerConfigPayload.decode(buf);
    assertTrue(payload.isValid(), "the physics half read cleanly and must be kept");
    assertEquals(20.0, payload.effectHeight(), 1e-9);
    assertNull(payload.draft(), "an unreadable drafting block falls back to local values");
  }

  @Test
  void decode_truncatedPhysics_isMarkedInvalid() {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeDouble(20.0);
    buf.writeDouble(0.005); // stops less than six doubles in
    ServerConfigPayload payload = ServerConfigPayload.decode(buf);
    assertFalse(payload.isValid(), "nothing trustworthy was read, so no override may be applied");
  }

  @Test
  void decode_emptyBuffer_isMarkedInvalidRatherThanThrowing() {
    ServerConfigPayload payload =
        ServerConfigPayload.decode(new FriendlyByteBuf(Unpooled.buffer()));
    assertFalse(payload.isValid());
  }

  @Test
  void decode_wellFormedPayload_isValid() {
    assertTrue(ServerConfigPayload.decode(legacyBuffer()).isValid());
  }
}
