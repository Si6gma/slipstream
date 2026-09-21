package com.si6gma.slipstream;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lines the debug overlay shows. Pure formatting, so the screen a support report is built from
 * can be tested without a client.
 *
 * <p>Every number here answers one of the two reports this mod will ever get: "it doesn't work",
 * answered by the server state and the boost line, and "something moved me", answered by the draft
 * line naming whose wake you were in.
 */
public final class DebugReport {

  /**
   * What the client can work out about the server it is on.
   *
   * <p>Fabric and Paper are deliberately not separated, and a withheld state cannot say why.
   * Traffic after the hello is one way, so the client is never told the server's protocol number,
   * which implementation it is, or which policy it applied. Saying so on the overlay beats
   * guessing at it.
   */
  public enum ServerState {
    SINGLEPLAYER("singleplayer"),
    VANILLA("vanilla, no Slipstream"),
    ACTIVE("Slipstream, effects granted"),
    WITHHELD("Slipstream, effects withheld");

    private final String label;

    ServerState(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  public static final String TITLE = "Slipstream debug";

  private DebugReport() {}

  /**
   * One screen of state.
   *
   * @param distToSurface blocks to the surface below, negative when there is none in range
   * @param cap the speed ceiling actually in force this tick, drafting or not
   * @param draftStrength 0 when not drafting
   * @param leaderName whose wake it is, null when not drafting
   */
  public static List<String> lines(
      ServerState state,
      int clientProtocol,
      boolean boostAllowed,
      boolean gliding,
      double distToSurface,
      double proximity,
      double hSpeed,
      double cap,
      double draftStrength,
      String leaderName) {
    List<String> out = new ArrayList<>();
    out.add(TITLE);
    out.add("Server: " + state.label());
    out.add("Protocol: client " + clientProtocol + ", server not reported");
    out.add("Boost allowed: " + yesNo(boostAllowed));

    if (!gliding) {
      out.add("Not gliding");
      return out;
    }

    out.add(
        distToSurface < 0.0
            ? "Surface: none in range"
            : "Surface: " + num(distToSurface) + " below, proximity " + num(proximity));
    out.add("Speed: " + num(hSpeed) + " / " + num(cap) + " b/t" + percentOfCap(hSpeed, cap));
    out.add(
        draftStrength > 0.0 && leaderName != null
            ? "Draft: " + num(draftStrength) + " behind " + leaderName
            : "Draft: none");
    return out;
  }

  private static String percentOfCap(double hSpeed, double cap) {
    if (cap <= 0.0) return "";
    return " (" + Math.round(hSpeed / cap * 100.0) + "%)";
  }

  private static String yesNo(boolean value) {
    return value ? "yes" : "no";
  }

  /** Always a dot, never whatever separator the player's locale prefers. */
  private static String num(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
