package com.si6gma.slipstream.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The server's own read on whether you are drafting, which is what buys the durability saving. */
class DraftEstimateTest {

  private static final double SQUARELY_BEHIND = 1.0;

  @Test
  void sittingCloseBehindSomeoneIsAStrongDraft() {
    double near = DraftEstimate.strength(2.0, SQUARELY_BEHIND, SQUARELY_BEHIND);
    double far = DraftEstimate.strength(10.0, SQUARELY_BEHIND, SQUARELY_BEHIND);
    assertTrue(near > far, "closer should draft harder");
    assertTrue(near > 0.0 && near <= 1.0);
  }

  @Test
  void outOfRangeIsNoDraft() {
    assertEquals(0.0, DraftEstimate.strength(DraftEstimate.RANGE, 1.0, 1.0), 1e-9);
    assertEquals(0.0, DraftEstimate.strength(50.0, 1.0, 1.0), 1e-9);
    assertEquals(0.0, DraftEstimate.strength(0.0, 1.0, 1.0), 1e-9);
  }

  @Test
  void someoneOffToTheSideIsNotAheadOfYou() {
    // Abeam is not drafting, however close they are.
    assertEquals(0.0, DraftEstimate.strength(3.0, 0.0, 1.0), 1e-9);
    assertEquals(0.0, DraftEstimate.strength(3.0, -1.0, 1.0), 1e-9);
  }

  @Test
  void someoneFlyingTheOtherWayIsNotALeader() {
    // Passing head on must not pay out. Without this, two players crossing paths both bank a
    // saving for the moment they overlap.
    assertEquals(0.0, DraftEstimate.strength(3.0, 1.0, -1.0), 1e-9);
    assertEquals(0.0, DraftEstimate.strength(3.0, 1.0, 0.0), 1e-9);
  }

  @Test
  void notDraftingNeverSkipsDamage() {
    assertFalse(DraftEstimate.skipDamage(0.0, 0.0));
    assertFalse(DraftEstimate.skipDamage(-1.0, 0.0));
  }

  @Test
  void aPerfectDraftStillWearsTheElytraOut() {
    // Capped deliberately: drafting should reward you, not retire the durability mechanic.
    assertFalse(
        DraftEstimate.skipDamage(1.0, DraftEstimate.MAX_SAVE + 0.01),
        "a roll above the cap must still cost durability");
    assertTrue(DraftEstimate.skipDamage(1.0, DraftEstimate.MAX_SAVE - 0.01));
  }

  @Test
  void savingScalesWithStrength() {
    // Half the draft, half the rolls skipped.
    double roll = DraftEstimate.MAX_SAVE * 0.4;
    assertTrue(DraftEstimate.skipDamage(1.0, roll));
    assertTrue(DraftEstimate.skipDamage(0.5, roll));
    assertFalse(DraftEstimate.skipDamage(0.1, roll));
  }

  @Test
  void strengthAboveOneIsClampedRatherThanCompounding() {
    assertFalse(DraftEstimate.skipDamage(99.0, DraftEstimate.MAX_SAVE + 0.01));
  }
}
