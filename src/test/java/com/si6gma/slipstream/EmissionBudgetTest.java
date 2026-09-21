package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The cap that stops forty gliders drowning the packet stream and the sound mixer. */
class EmissionBudgetTest {

  @Test
  void theNearestGlidersKeepEverything() {
    for (int rank = 0; rank < EmissionBudget.FULL_DETAIL_GLIDERS; rank++) {
      assertEquals(1.0, EmissionBudget.viewerShare(rank), 1e-9, "rank " + rank);
    }
  }

  @Test
  void fartherGlidersThinRatherThanVanish() {
    // A cliff would make a wake pop in and out as two gliders trade places. Every rank past the
    // full detail band keeps something.
    for (int rank = EmissionBudget.FULL_DETAIL_GLIDERS; rank < 60; rank++) {
      double share = EmissionBudget.viewerShare(rank);
      assertTrue(share > 0.0, "rank " + rank + " went silent entirely");
      assertTrue(share < 1.0, "rank " + rank + " kept full detail");
    }
  }

  @Test
  void viewerShareNeverIncreasesWithDistance() {
    double prev = Double.MAX_VALUE;
    for (int rank = 0; rank < 60; rank++) {
      double share = EmissionBudget.viewerShare(rank);
      assertTrue(share <= prev + 1e-12, "share rose at rank " + rank);
      prev = share;
    }
  }

  @Test
  void aNegativeRankEmitsNothing() {
    assertEquals(0.0, EmissionBudget.viewerShare(-1), 1e-9);
  }

  @Test
  void aFewGlidersAreNotThinnedAtAll() {
    for (int count = 1; count <= EmissionBudget.SERVER_PARTICLE_BUDGET; count++) {
      assertEquals(1.0, EmissionBudget.fairShare(count), 1e-9, "count " + count);
    }
  }

  @Test
  void aCrowdIsThinnedSoTotalOutputStaysBounded() {
    // The whole point: total emission is count times share, which must stay at the budget however
    // many gliders turn up.
    for (int count = 1; count <= 200; count++) {
      double total = count * EmissionBudget.fairShare(count);
      assertTrue(
          total <= EmissionBudget.SERVER_PARTICLE_BUDGET + 1e-9,
          "total output " + total + " at " + count + " gliders exceeded the budget");
    }
    assertEquals(
        EmissionBudget.SERVER_PARTICLE_BUDGET,
        40 * EmissionBudget.fairShare(40),
        1e-9,
        "forty gliders should spend exactly the budget between them");
  }

  @Test
  void noGlidersSpendNothing() {
    assertEquals(0.0, EmissionBudget.fairShare(0), 1e-9);
    assertEquals(0.0, EmissionBudget.fairShare(-3), 1e-9);
  }

  @Test
  void soundsAreHardCappedRatherThanThinned() {
    // Unlike particles a sound cannot be played fractionally, and exhausting the channel pool
    // starts dropping vanilla sounds, so this one is a hard count.
    for (int rank = 0; rank < EmissionBudget.MAX_SOUNDS_PER_TICK; rank++) {
      assertTrue(EmissionBudget.allowSound(rank), "rank " + rank + " should be audible");
    }
    for (int rank = EmissionBudget.MAX_SOUNDS_PER_TICK; rank < 40; rank++) {
      assertTrue(!EmissionBudget.allowSound(rank), "rank " + rank + " should be silent");
    }
    assertTrue(!EmissionBudget.allowSound(-1));
  }
}
