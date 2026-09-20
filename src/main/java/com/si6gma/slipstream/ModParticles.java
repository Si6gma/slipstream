package com.si6gma.slipstream;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class ModParticles {

  private static SimpleParticleType WING_VORTEX;
  private static SimpleParticleType WAKE_TRAIL;
  private static SimpleParticleType DRAFT_ACTIVE;

  /** Wingtip vortices during ground effect. */
  public static SimpleParticleType wingVortex() {
    return WING_VORTEX;
  }

  /** Another glider's wake, seen from outside it. */
  public static SimpleParticleType wakeTrail() {
    return WAKE_TRAIL;
  }

  /** The wake you are currently riding, and your own draft strength. */
  public static SimpleParticleType draftActive() {
    return DRAFT_ACTIVE;
  }

  @SuppressWarnings("null")
  public static void register() {
    WING_VORTEX =
        Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "wing_vortex"),
            FabricParticleTypes.simple());
    WAKE_TRAIL =
        Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "wake_trail"),
            FabricParticleTypes.simple());
    DRAFT_ACTIVE =
        Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "draft_active"),
            FabricParticleTypes.simple());
  }
}
