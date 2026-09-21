package com.si6gma.slipstream.client;

import com.si6gma.slipstream.DebugReport;
import com.si6gma.slipstream.LocalDraftState;
import com.si6gma.slipstream.LocalGroundEffectState;
import com.si6gma.slipstream.SlipstreamConfig;
import com.si6gma.slipstream.draft.DraftingMath;
import com.si6gma.slipstream.network.HelloPayload;
import com.si6gma.slipstream.network.ServerConfigOverride;
import com.si6gma.slipstream.network.SlipstreamProtocol;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code /slipstream debug} overlay: one screen that answers both support reports this mod
 * will ever get. Off by default, toggled by the command, and never persisted, because it exists to
 * be turned on for one screenshot.
 */
public final class SlipstreamDebugHud {

  private static final Identifier ELEMENT =
      Identifier.fromNamespaceAndPath("slipstream", "debug_overlay");

  private static final int LEFT = 4;
  private static final int TOP = 4;
  private static final int LINE_HEIGHT = 10;
  private static final int COLOUR = 0xFFFFFFFF;

  private static boolean enabled;

  private SlipstreamDebugHud() {}

  public static boolean enabled() {
    return enabled;
  }

  /** Returns the new state, so the command can report it without reading the flag again. */
  public static boolean toggle() {
    enabled = !enabled;
    return enabled;
  }

  public static void register() {
    HudElementRegistry.addLast(
        ELEMENT,
        (extractor, tickCounter) -> {
          if (!enabled) return;
          Minecraft client = Minecraft.getInstance();
          LocalPlayer local = client.player;
          if (local == null) return;
          List<String> lines = report(local);
          int y = TOP;
          for (String line : lines) {
            extractor.text(client.font, line, LEFT, y, COLOUR);
            y += LINE_HEIGHT;
          }
        });
  }

  private static List<String> report(LocalPlayer local) {
    SlipstreamConfig cfg = ServerConfigOverride.get();
    Vec3 velocity = local.getDeltaMovement();
    double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    double draftStrength = LocalDraftState.strength();
    // The cap actually in force: a drafter is allowed past the normal ceiling, so showing the
    // normal one would make a legitimate boost look like a speed hack.
    double cap = draftStrength > 0.0 ? DraftingMath.draftCap(cfg) : cfg.maxSpeedBlocksPerTick;

    return DebugReport.lines(
        serverState(),
        SlipstreamProtocol.VERSION,
        ServerConfigOverride.isBoostAllowed(),
        local.isFallFlying(),
        LocalGroundEffectState.distToSurface(),
        LocalGroundEffectState.proximity(),
        hSpeed,
        cap,
        draftStrength,
        LocalDraftState.leaderName());
  }

  /**
   * Whether the server has Slipstream is read from the channel it declared, not from whether a
   * config arrived, so a server that has the mod but withheld the payload stays distinguishable
   * from a vanilla one. That is the difference between "this server does not run Slipstream" and
   * "this server refused your version", which are opposite answers to the same complaint.
   */
  private static DebugReport.ServerState serverState() {
    if (Minecraft.getInstance().hasSingleplayerServer()) {
      return DebugReport.ServerState.SINGLEPLAYER;
    }
    if (ServerConfigOverride.isActive()) return DebugReport.ServerState.ACTIVE;
    return ClientPlayNetworking.canSend(HelloPayload.TYPE)
        ? DebugReport.ServerState.WITHHELD
        : DebugReport.ServerState.VANILLA;
  }
}
