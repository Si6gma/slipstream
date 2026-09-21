package com.si6gma.slipstream.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The boot time update nag, which used to fire on every start of every server forever. */
class VersionCompareTest {

  @Test
  void theSameReleaseIsNotAnUpdate() {
    assertFalse(VersionCompare.isNewer("1.0.3", "1.0.3"));
  }

  @Test
  void theSameReleaseWearingThePaperSuffixIsNotAnUpdate() {
    // This is the bug. Modrinth publishes the Paper artifact as <version>-paper while plugin.yml
    // carries the bare number, so an equality check could never match and every console got a
    // nag on every boot.
    assertFalse(VersionCompare.isNewer("1.0.3-paper", "1.0.3"));
  }

  @Test
  void aGenuinelyNewerReleaseIsAnUpdate() {
    assertTrue(VersionCompare.isNewer("1.0.4", "1.0.3"));
    assertTrue(VersionCompare.isNewer("1.1.0", "1.0.3"));
    assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"));
    assertTrue(VersionCompare.isNewer("1.0.4-paper", "1.0.3"));
  }

  @Test
  void anOlderReleaseIsNotAnUpdate() {
    // A server running a build ahead of the release must not be nagged to downgrade.
    assertFalse(VersionCompare.isNewer("1.0.2", "1.0.3"));
    assertFalse(VersionCompare.isNewer("0.9.9", "1.0.0"));
  }

  @Test
  void missingTrailingPartsCountAsZero() {
    assertFalse(VersionCompare.isNewer("1.0", "1.0.0"));
    assertTrue(VersionCompare.isNewer("1.0.1", "1.0"));
    assertFalse(VersionCompare.isNewer("1.0", "1.0.1"));
  }

  @Test
  void buildMetadataIsIgnored() {
    assertFalse(VersionCompare.isNewer("1.0.3+26.3", "1.0.3"));
    assertTrue(VersionCompare.isNewer("1.0.4+26.3", "1.0.3"));
  }

  @Test
  void anythingUnparseableStaysQuiet() {
    // A warning that cries wolf is worse than one that occasionally says nothing.
    assertFalse(VersionCompare.isNewer("not-a-version", "1.0.3"));
    assertFalse(VersionCompare.isNewer("1.0.3", "garbage"));
    assertFalse(VersionCompare.isNewer(null, "1.0.3"));
    assertFalse(VersionCompare.isNewer("1.0.3", null));
    assertFalse(VersionCompare.isNewer("", "1.0.3"));
    assertFalse(VersionCompare.isNewer("1.0.-3", "1.0.3"));
  }
}
