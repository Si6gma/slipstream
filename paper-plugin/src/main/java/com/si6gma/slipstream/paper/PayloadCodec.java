package com.si6gma.slipstream.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Writes the config payload the Fabric client decodes. Kept free of any Bukkit type so the wire
 * format can be tested without a server on the classpath.
 *
 * <p>Field order must match {@code ServerConfigPayload.decode()} on the Fabric side. The six
 * leading doubles never move: a client that predates drafting stops reading after them, which is
 * what lets an older mod keep working against a newer plugin.
 */
final class PayloadCodec {

  /** 6 leading doubles (48 bytes) plus the drafting block (78 bytes). */
  static final int PAYLOAD_BYTES = 126;

  private PayloadCodec() {}

  /**
   * The drafting half of the payload. Types are load bearing: three of these go on the wire as
   * ints and the rest as doubles, and writing the wrong width silently corrupts every field after
   * it with no error at either end.
   */
  record DraftValues(
      boolean enabled,
      double acceleration,
      double speedMultiplier,
      double pullStrength,
      double releaseAngleDeg,
      double wakeBaseRadius,
      double wakeSpreadRate,
      int wakeLifetimeTicks,
      int wakeSampleIntervalTicks,
      double leaderBonusPerDrafter,
      int leaderBonusMaxDrafters,
      boolean cameraAssist,
      double cameraAssistStrength) {}

  /**
   * Drafting switched off. The enabled flag alone stops every drafting force on the client; the
   * numbers are neutral values so nothing downstream can reinterpret them.
   */
  static DraftValues draftDisabled() {
    return new DraftValues(false, 0.0, 1.0, 0.0, 0.0, 1.5, 1.2, 60, 2, 0.0, 0, false, 0.0);
  }

  static byte[] serialize(
      double effectHeight,
      double acceleration,
      double maxSpeed,
      double waterSprayHeight,
      double liftStrength,
      double speedThreshold,
      DraftValues draft)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(PAYLOAD_BYTES);
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeDouble(effectHeight);
    out.writeDouble(acceleration);
    out.writeDouble(maxSpeed);
    out.writeDouble(waterSprayHeight);
    out.writeDouble(liftStrength);
    out.writeDouble(speedThreshold);
    out.writeBoolean(draft.enabled());
    out.writeDouble(draft.acceleration());
    out.writeDouble(draft.speedMultiplier());
    out.writeDouble(draft.pullStrength());
    out.writeDouble(draft.releaseAngleDeg());
    out.writeDouble(draft.wakeBaseRadius());
    out.writeDouble(draft.wakeSpreadRate());
    out.writeInt(draft.wakeLifetimeTicks());
    out.writeInt(draft.wakeSampleIntervalTicks());
    out.writeDouble(draft.leaderBonusPerDrafter());
    out.writeInt(draft.leaderBonusMaxDrafters());
    out.writeBoolean(draft.cameraAssist());
    out.writeDouble(draft.cameraAssistStrength());
    return bytes.toByteArray();
  }
}
