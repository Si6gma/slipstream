package com.si6gma.slipstream;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class ModParticles {

    public static SimpleParticleType WING_VORTEX;

    public static void register() {
        WING_VORTEX = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(Slipstream.MOD_ID, "wing_vortex"),
            FabricParticleTypes.simple()
        );
    }
}
