package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The one screenshot a support report is built from. */
class DebugReportTest {

  private static List<String> drafting() {
    return DebugReport.lines(
        DebugReport.ServerState.ACTIVE, 2, true, true, 3.25, 0.74, 1.31, 1.725, 0.63, "Steve");
  }

  private static String joined(List<String> lines) {
    return String.join("\n", lines);
  }

  @Test
  void alwaysLeadsWithTheTitle() {
    assertEquals(DebugReport.TITLE, drafting().get(0));
  }

  @Test
  void namesTheServerStateAndWhetherBoostIsAllowed() {
    String text = joined(drafting());
    assertTrue(text.contains("Slipstream, effects granted"), text);
    assertTrue(text.contains("Boost allowed: yes"), text);
  }

  @Test
  void aVanillaServerSaysSoRatherThanLookingBroken() {
    // "It doesn't work" is answered here: on a vanilla server nothing is granted, by design.
    String text =
        joined(
            DebugReport.lines(
                DebugReport.ServerState.VANILLA, 2, false, true, 2.0, 0.5, 1.0, 1.5, 0.0, null));
    assertTrue(text.contains("vanilla, no Slipstream"), text);
    assertTrue(text.contains("Boost allowed: no"), text);
  }

  @Test
  void withheldIsDistinctFromVanilla() {
    // A server that has Slipstream but refused us is a different support answer entirely.
    String text =
        joined(
            DebugReport.lines(
                DebugReport.ServerState.WITHHELD, 2, false, true, 2.0, 0.5, 1.0, 1.5, 0.0, null));
    assertTrue(text.contains("effects withheld"), text);
  }

  @Test
  void reportsTheClientProtocolAndAdmitsItDoesNotKnowTheServers() {
    // The client is never told the server's number, so the overlay says so instead of inventing
    // one. This is the line to revisit if the handshake ever reports back.
    String text = joined(drafting());
    assertTrue(text.contains("client 2"), text);
    assertTrue(text.contains("server not reported"), text);
  }

  @Test
  void namesTheLeaderWhoseWakeYouAreIn() {
    // "Something moved me" is answered here.
    String text = joined(drafting());
    assertTrue(text.contains("Draft: 0.63 behind Steve"), text);
  }

  @Test
  void saysNoneRatherThanNamingNobody() {
    String text =
        joined(
            DebugReport.lines(
                DebugReport.ServerState.ACTIVE, 2, true, true, 3.0, 0.5, 1.0, 1.5, 0.0, null));
    assertTrue(text.contains("Draft: none"), text);
  }

  @Test
  void showsSpeedAgainstTheCapInForce() {
    String text = joined(drafting());
    assertTrue(text.contains("1.31 / 1.73 b/t"), text);
    assertTrue(text.contains("76%"), text);
  }

  @Test
  void aMissingSurfaceIsNotReportedAsZeroBlocksAway() {
    String text =
        joined(
            DebugReport.lines(
                DebugReport.ServerState.ACTIVE, 2, true, true, -1.0, 0.0, 1.0, 1.5, 0.0, null));
    assertTrue(text.contains("Surface: none in range"), text);
  }

  @Test
  void stopsEarlyWhenNotGliding() {
    List<String> lines =
        DebugReport.lines(
            DebugReport.ServerState.SINGLEPLAYER, 2, true, false, 0.0, 0.0, 0.0, 1.5, 0.0, null);
    assertTrue(joined(lines).contains("Not gliding"), joined(lines));
    assertTrue(
        lines.stream().noneMatch(line -> line.startsWith("Speed:")),
        "speed against a cap means nothing when not gliding");
  }

  @Test
  void aZeroCapDoesNotProduceAnInfinitePercentage() {
    String text =
        joined(
            DebugReport.lines(
                DebugReport.ServerState.ACTIVE, 2, true, true, 1.0, 0.5, 1.0, 0.0, 0.0, null));
    assertTrue(text.contains("Speed:"), text);
    assertTrue(!text.contains("Infinity") && !text.contains("NaN"), text);
  }

  @Test
  void numbersUseADotWhateverThePlayersLocaleIs() {
    // A German client must not render "0,63" into a bug report a reader then misreads.
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      String text = joined(drafting());
      assertTrue(text.contains("0.63"), text);
      assertTrue(!text.contains("0,63"), text);
    } finally {
      Locale.setDefault(original);
    }
  }
}
