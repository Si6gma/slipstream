package com.si6gma.slipstream.network;

import com.si6gma.slipstream.Slipstream;
import com.si6gma.slipstream.SlipstreamConfig;
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
      int leaderBonusMaxDrafters) {

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
          cfg.draftLeaderBonusMaxDrafters);
    }
  }

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
  }

  /** Package visible for the codec and for tests. */
  static ServerConfigPayload decode(FriendlyByteBuf buf) {
    double effectHeight = buf.readDouble();
    double acceleration = buf.readDouble();
    double maxSpeed = buf.readDouble();
    double waterSprayHeight = buf.readDouble();
    double liftStrength = buf.readDouble();
    double effectSpeedThreshold = buf.readDouble();
    DraftSettings draft = null;
    // A server that predates drafting stops here. Leave draft null and keep local defaults.
    if (buf.isReadable()) {
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
              buf.readInt());
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

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
