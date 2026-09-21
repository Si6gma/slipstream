package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Telling one player's wake from another's in a pack. */
class WakeHueTest {

  @Test
  void aPlayerAlwaysGetsTheSameColour() {
    // A wake that changed hue between ticks would be worse than no hue at all.
    UUID id = UUID.nameUUIDFromBytes("steve".getBytes());
    assertArrayEquals(WakeHue.rgbFor(id), WakeHue.rgbFor(id));
    assertEquals(WakeHue.hueFor(id), WakeHue.hueFor(UUID.fromString(id.toString())), 1e-9);
  }

  @Test
  void hueIsAlwaysOnTheCircle() {
    for (int i = 0; i < 2000; i++) {
      float hue = WakeHue.hueFor(new UUID(i * 2654435761L, i));
      assertTrue(hue >= 0.0f && hue < 1.0f, "hue left [0, 1): " + hue);
    }
  }

  @Test
  void huesAreSpreadAroundTheWholeCircle() {
    // What this can honestly promise. A player id is effectively random, so no per player hash
    // can guarantee that two given players differ: with eight players there are twenty eight
    // pairs, and a near collision somewhere is more likely than not. Trying to guarantee it
    // would mean assigning hues from the current player list, and then your wake would change
    // colour whenever somebody logged in, which is worse than an occasional clash.
    //
    // So the property worth holding is that hues use the whole circle rather than bunching, and
    // that is what makes most pairs in a pack distinguishable most of the time.
    boolean[] sextant = new boolean[6];
    for (int i = 0; i < 200; i++) {
      float hue = WakeHue.hueFor(UUID.nameUUIDFromBytes(("player" + i).getBytes()));
      sextant[Math.min(5, (int) (hue * 6.0f))] = true;
    }
    for (int i = 0; i < 6; i++) {
      assertTrue(sextant[i], "no player ever landed in sextant " + i + ", hues are bunching");
    }
  }

  @Test
  void twoDifferentPlayersUsuallyLookDifferent() {
    // Not a guarantee, a rate. Anything much worse than this means the hash is collapsing.
    int count = 60;
    float[] hues = new float[count];
    for (int i = 0; i < count; i++) {
      hues[i] = WakeHue.hueFor(UUID.nameUUIDFromBytes(("flyer" + i).getBytes()));
    }
    int pairs = 0;
    int tooClose = 0;
    for (int a = 0; a < count; a++) {
      for (int b = a + 1; b < count; b++) {
        float raw = Math.abs(hues[a] - hues[b]);
        float separation = Math.min(raw, 1.0f - raw);
        pairs++;
        if (separation <= 0.02f) tooClose++;
      }
    }
    assertTrue(
        tooClose * 20 < pairs,
        "too many indistinguishable pairs: " + tooClose + " of " + pairs);
  }

  @Test
  void coloursStayPaleRatherThanFullySaturated() {
    // These are wakes, not team markers. Every channel keeps some light in it, so a wake never
    // reads as a solid block of colour against the sky.
    for (int i = 0; i < 500; i++) {
      float[] rgb = WakeHue.rgbFor(UUID.nameUUIDFromBytes(("p" + i).getBytes()));
      for (float channel : rgb) {
        assertTrue(channel >= 0.0f && channel <= 1.0f, "channel out of range: " + channel);
        assertTrue(channel >= 0.5f, "channel too dark to read as air: " + channel);
      }
    }
  }

  @Test
  void aNullIdDoesNotExplode() {
    assertEquals(0.0f, WakeHue.hueFor(null), 1e-9);
    assertEquals(3, WakeHue.rgbFor(null).length);
  }

  @Test
  void hsvToRgbCoversEverySector() {
    // Six sectors, six branches, and an off by one in any of them shows up as a hue that jumps.
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 6; i++) {
      float[] rgb = WakeHue.hsvToRgb(i / 6.0f + 0.08f, 1.0f, 1.0f);
      assertEquals(1.0f, Math.max(rgb[0], Math.max(rgb[1], rgb[2])), 1e-6, "sector " + i);
      assertEquals(0.0f, Math.min(rgb[0], Math.min(rgb[1], rgb[2])), 1e-6, "sector " + i);
      seen.add(rgb[0] + "," + rgb[1] + "," + rgb[2]);
    }
    assertEquals(6, seen.size(), "two sectors produced the same colour");
  }

  @Test
  void hueWrapsRatherThanClamping() {
    assertArrayEquals(
        WakeHue.hsvToRgb(0.25f, 0.5f, 1.0f), WakeHue.hsvToRgb(1.25f, 0.5f, 1.0f), 1e-6f);
  }
}
