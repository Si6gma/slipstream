package com.si6gma.slipstream;

/**
 * Duck interface implemented by {@code LivingEntityMixin}. Cast any {@code LivingEntity} to this
 * to obtain its cached ground effect sample.
 */
public interface GroundEffectSampler {

  /**
   * Returns the entity's current sample, or {@code null} when it is not gliding, is submerged, is
   * too slow, or is too far above any surface. Reuses a per entity raycast cache.
   */
  GroundEffectSample slipstream$sample(SlipstreamConfig cfg);
}
