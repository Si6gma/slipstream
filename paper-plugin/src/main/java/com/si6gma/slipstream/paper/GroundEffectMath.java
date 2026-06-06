package com.si6gma.slipstream.paper;

/** Mirror of the Fabric-side GroundEffectMath — keep formulas in sync. */
final class GroundEffectMath {

  private GroundEffectMath() {}

  /**
   * Quadratic proximity falloff: 1.0 within the first 3 blocks of the surface, then falls to 0.0
   * at effectHeight.
   */
  static double proximity(double distToSurface, double effectHeight) {
    double buffer = Math.min(3.0, effectHeight - 1.0);
    double adjusted = Math.max(0.0, distToSurface - buffer);
    double range = effectHeight - buffer;
    if (range <= 0) return 1.0;
    double linear = 1.0 - (adjusted / range);
    return linear * linear;
  }
}
