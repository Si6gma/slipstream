package com.si6gma.slipstream;

import java.util.UUID;

/**
 * A stable colour per player, so overlapping wakes in a pack can be told apart at a glance.
 *
 * <p>Only possible because the particle sprites are authored greyscale with the shape in the alpha
 * channel: tinting multiplies, so a white sprite takes any hue, while a sprite with colour baked
 * in could only ever be darkened.
 *
 * <p>Saturation is deliberately low. These are wakes, not team markers. Enough separation to say
 * "that one is not the one I am chasing", not enough to turn the sky into a parade.
 */
public final class WakeHue {

  /** Fractional bits taken from the mixed hash. 2^24 is exactly representable in a float. */
  private static final int HUE_BITS = 24;

  private static final float SATURATION = 0.45f;
  private static final float VALUE = 1.0f;

  private WakeHue() {}

  /**
   * Hue in [0, 1) for this player, stable for as long as their id is.
   *
   * <p>The obvious version of this, multiplying the id by the golden ratio conjugate and keeping
   * the fraction, does not survive contact with a float. That trick spreads *sequential* inputs,
   * which player ids are not, and a float carries only 24 mantissa bits, so for a number the size
   * of a UUID half the product's fractional part quantises to a handful of values and most
   * players come out the same colour. A test caught exactly that.
   *
   * <p>So: mix the two halves properly with a 64 bit avalanche, then take the top 24 bits and
   * scale by 2^24, which is exact in a float and cannot lose the spread again.
   */
  public static float hueFor(UUID id) {
    if (id == null) return 0.0f;
    long z = id.getMostSignificantBits() ^ (id.getLeastSignificantBits() * 0x9E3779B97F4A7C15L);
    z ^= z >>> 33;
    z *= 0xFF51AFD7ED558CCDL;
    z ^= z >>> 33;
    z *= 0xC4CEB9FE1A85EC53L;
    z ^= z >>> 33;
    return (z >>> (64 - HUE_BITS)) / (float) (1 << HUE_BITS);
  }

  /** Red, green and blue in [0, 1] for this player's wake. */
  public static float[] rgbFor(UUID id) {
    return hsvToRgb(hueFor(id), SATURATION, VALUE);
  }

  /** Standard HSV to RGB. Kept here rather than pulled in so this class stays testable alone. */
  static float[] hsvToRgb(float hue, float saturation, float value) {
    float h = (hue - (float) Math.floor(hue)) * 6.0f;
    int sector = (int) h;
    float f = h - sector;
    float p = value * (1.0f - saturation);
    float q = value * (1.0f - saturation * f);
    float t = value * (1.0f - saturation * (1.0f - f));
    return switch (sector % 6) {
      case 0 -> new float[] {value, t, p};
      case 1 -> new float[] {q, value, p};
      case 2 -> new float[] {p, value, t};
      case 3 -> new float[] {p, q, value};
      case 4 -> new float[] {t, p, value};
      default -> new float[] {value, p, q};
    };
  }
}
