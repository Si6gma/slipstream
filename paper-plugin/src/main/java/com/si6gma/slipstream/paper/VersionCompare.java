package com.si6gma.slipstream.paper;

/**
 * Release ordering for the update check. Kept free of Bukkit types on purpose: {@code paper-api}
 * is {@code compileOnly}, so anything touching it cannot be loaded in a unit test, and this is the
 * part worth testing.
 */
final class VersionCompare {

  private VersionCompare() {}

  /**
   * Whether the published version is actually newer than the running one.
   *
   * <p>This used to be an inequality, which could never be satisfied: the Paper artifact publishes
   * as {@code <version>-paper} while {@code plugin.yml} carries the bare number, so every server
   * console got an update nag on every boot forever. Suffixes are stripped from both sides and the
   * remainder compared as numbers, so a server running a build ahead of the release is not nagged
   * to downgrade either.
   *
   * <p>Anything that does not parse returns false. A boot time warning that cries wolf is worse
   * than one that occasionally stays quiet.
   */
  static boolean isNewer(String candidate, String current) {
    int[] a = parse(candidate);
    int[] b = parse(current);
    if (a == null || b == null) return false;
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      int left = i < a.length ? a[i] : 0;
      int right = i < b.length ? b[i] : 0;
      if (left != right) return left > right;
    }
    return false;
  }

  /** Dotted numbers only, with any {@code -paper} or {@code +build} tail dropped. */
  private static int[] parse(String version) {
    if (version == null) return null;
    String trimmed = version.trim();
    int cut = trimmed.indexOf('-');
    if (cut >= 0) trimmed = trimmed.substring(0, cut);
    cut = trimmed.indexOf('+');
    if (cut >= 0) trimmed = trimmed.substring(0, cut);
    if (trimmed.isEmpty()) return null;
    String[] parts = trimmed.split("\\.");
    int[] out = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        out[i] = Integer.parseInt(parts[i]);
      } catch (NumberFormatException e) {
        return null;
      }
      if (out[i] < 0) return null;
    }
    return out;
  }
}
