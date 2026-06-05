package com.si6gma.slipstream.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class WingVortexParticle extends SingleQuadParticle {

    private final SpriteSet sprites;

    protected WingVortexParticle(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.lifetime = 18 + random.nextInt(8);
        this.alpha = 0.55f;
        this.quadSize = 0.18f + random.nextFloat() * 0.12f;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * (float) Math.PI * 2;
        this.oRoll = this.roll;
    }

    @Override
    public void tick() {
        this.xd *= 0.88f;
        this.yd *= 0.88f;
        this.zd *= 0.88f;
        super.tick();
        if (this.removed) return;
        float progress = (float) this.age / this.lifetime;
        this.alpha = 0.55f * (1.0f  progress);
        this.quadSize *= (progress < 0.3f ? 1.04f : 0.97f);
        this.oRoll = this.roll;
        this.roll += 0.03f;
        this.setSpriteFromAge(sprites);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        @Nullable
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z,
                                        double vx, double vy, double vz,
                                        RandomSource random) {
            return new WingVortexParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
