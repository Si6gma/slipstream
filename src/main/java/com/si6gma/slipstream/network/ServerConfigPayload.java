package com.si6gma.slipstream.network;

import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.SlipstreamConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent by the Paper plugin (or a Fabric server) to push server side config values to the Fabric
 * client, overriding the player's local settings. Uses raw primitive encoding so Paper can
 * construct the bytes without Fabric.
 *
 * <p>The drafting block is optional and trails the original six doubles. A server that predates
 * drafting simply stops writing after the sixth double, and {@link #decode} leaves {@code draft}
 * null so the client keeps its own defaults. Never reorder or resize the leading six values.
 */
public record ServerConfigPayload(
    double effectHeight,
    double acceleration,
    double maxSpeed,
    double waterSprayHeight,
    double liftStrength,
    double effectSpeedThreshold,
    DraftSettings draft)
    implements CustomPacketPayload {

  /** The drafting half of the payload. Null on the record means the server did not send it. */
  public record DraftSettings(
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
      double cameraAssistStrength) {

    public static DraftSettings from(SlipstreamConfig cfg) {
      return new DraftSettings(
          cfg.draftingEnabled,
          cfg.draftAccelerationPerTick,
          cfg.draftSpeedMultiplier,
          cfg.draftPullStrength,
          cfg.draftReleaseAngleDeg,
          cfg.wakeBaseRadius,
          cfg.wakeSpreadRate,
          cfg.wakeLifetimeTicks,
          cfg.wakeSampleIntervalTicks,
          cfg.draftLeaderBonusPerDrafter,
          cfg.draftLeaderBonusMaxDrafters,
          cfg.draftCameraAssist,
          cfg.draftCameraAssistStrength);
    }
  }

  private static final AtomicBoolean WARNED = new AtomicBoolean();

  public static final Type<ServerConfigPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "server_config"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigPayload> CODEC =
      StreamCodec.of((buf, payload) -> encode(buf, payload), buf -> decode(buf));

  /** Package visible for the codec and for tests. */
  static void encode(FriendlyByteBuf buf, ServerConfigPayload payload) {
    buf.writeDouble(payload.effectHeight());
    buf.writeDouble(payload.acceleration());
    buf.writeDouble(payload.maxSpeed());
    buf.writeDouble(payload.waterSprayHeight());
    buf.writeDouble(payload.liftStrength());
    buf.writeDouble(payload.effectSpeedThreshold());
    DraftSettings draft = payload.draft();
    if (draft == null) return;
    buf.writeBoolean(draft.enabled());
    buf.writeDouble(draft.acceleration());
    buf.writeDouble(draft.speedMultiplier());
    buf.writeDouble(draft.pullStrength());
    buf.writeDouble(draft.releaseAngleDeg());
    buf.writeDouble(draft.wakeBaseRadius());
    buf.writeDouble(draft.wakeSpreadRate());
    buf.writeInt(draft.wakeLifetimeTicks());
    buf.writeInt(draft.wakeSampleIntervalTicks());
    buf.writeDouble(draft.leaderBonusPerDrafter());
    buf.writeInt(draft.leaderBonusMaxDrafters());
    buf.writeBoolean(draft.cameraAssist());
    buf.writeDouble(draft.cameraAssistStrength());
  }

  /**
   * Marks a payload we could not read. Decoding runs inside the netty pipeline, so throwing here
   * disconnects the player with an "Internal Exception" screen; a malformed packet must not cost
   * someone their session.
   */
  public boolean isValid() {
    return !Double.isNaN(effectHeight);
  }

  private static ServerConfigPayload invalid() {
    return new ServerConfigPayload(Double.NaN, 0, 0, 0, 0, 0, null);
  }

  /** Package visible for the codec and for tests. */
  static ServerConfigPayload decode(FriendlyByteBuf buf) {
    double effectHeight;
    double acceleration;
    double maxSpeed;
    double waterSprayHeight;
    double liftStrength;
    double effectSpeedThreshold;
    try {
      effectHeight = buf.readDouble();
      acceleration = buf.readDouble();
      maxSpeed = buf.readDouble();
      waterSprayHeight = buf.readDouble();
      liftStrength = buf.readDouble();
      effectSpeedThreshold = buf.readDouble();
    } catch (RuntimeException e) {
      // Nothing here can be trusted, so the caller applies no override at all. That leaves the
      // player on their own config with no boost, which is the safe direction to fail.
      warnOnce("Unreadable Slipstream config payload, ignoring it");
      return invalid();
    }

    DraftSettings draft = null;
    // A server that predates drafting stops here. Leave draft null and keep local defaults.
    if (buf.isReadable()) {
      try {
        draft =
          new DraftSettings(
              buf.readBoolean(),
              buf.readDouble(),
              buf.readDouble(),
              buf.readDouble(),
              buf.readDouble(),
              buf.readDouble(),
              buf.readDouble(),
              buf.readInt(),
              buf.readInt(),
              buf.readDouble(),
              buf.readInt(),
              buf.readBoolean(),
              buf.readDouble());
      } catch (RuntimeException e) {
        // The physics half read cleanly, so keep it and fall back to local drafting values rather
        // than discarding a payload that is mostly fine.
        warnOnce("Unreadable drafting block in the Slipstream config payload, using local values");
        draft = null;
      }
    }
    return new ServerConfigPayload(
        effectHeight,
        acceleration,
        maxSpeed,
        waterSprayHeight,
        liftStrength,
        effectSpeedThreshold,
        draft);
  }

  /** Logged at most once per session: a broken server would otherwise spam the log every join. */
  private static void warnOnce(String message) {
    if (WARNED.compareAndSet(false, true)) {
      Slipstream.LOGGER.warn(message);
    }
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
