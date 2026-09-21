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

  /**
   * Appearance for one role. Separating these is what stops every Slipstream effect reading as the
   * same white puff: a wake you are merely near, a wake you are riding, and your own wingtip
   * vortices should not look alike when they mean different things.
   *
   * @param red tint applied to the sprite, 0 to 1
   * @param green tint applied to the sprite, 0 to 1
   * @param blue tint applied to the sprite, 0 to 1
   * @param alpha opacity at birth, faded to zero over the particle's life
   * @param sizeBase smallest quad size in blocks
   * @param sizeSpread extra size added at random on top of sizeBase
   * @param lifeBase shortest lifetime in ticks
   * @param lifeSpread extra lifetime added at random
   * @param spin radians of roll added per tick
   * @param drag fraction of velocity retained per tick, lower is snappier
   * @param hueFromSpawnArgs read the spawn velocity as an RGB tint instead of as motion, which is
   *     how a wake gets the colour of whoever left it without a custom particle type and the
   *     codec, stream codec and registry entry that would come with one
   */
  public record Style(
      float red,
      float green,
      float blue,
      float alpha,
      float sizeBase,
      float sizeSpread,
      int lifeBase,
      int lifeSpread,
      float spin,
      float drag,
      boolean hueFromSpawnArgs) {}

  /** Wingtip vortices during ground effect: small, white, quick. The original look. */
  public static final Style GROUND_EFFECT =
      new Style(1.0f, 1.0f, 1.0f, 0.55f, 0.18f, 0.12f, 18, 8, 0.03f, 0.88f, false);

  /** Another glider's wake: broad, pale and cool, drifting. Air someone passed through. */
  public static final Style WAKE =
      new Style(0.62f, 0.78f, 1.0f, 0.32f, 0.30f, 0.18f, 26, 10, 0.012f, 0.94f, true);

  /** The wake you are riding, and your draft strength: tight, bright, fast. */
  public static final Style DRAFT =
      new Style(0.40f, 0.95f, 1.0f, 0.85f, 0.11f, 0.11f, 12, 7, 0.09f, 0.82f, false);

  private final SpriteSet sprites;
  private final Style style;

  protected WingVortexParticle(
      ClientLevel level,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      SpriteSet sprites,
      Style style) {
    super(level, x, y, z, vx, vy, vz, sprites.get(level.getRandom()));
    this.sprites = sprites;
    this.style = style;
    this.lifetime = style.lifeBase() + random.nextInt(style.lifeSpread());
    this.alpha = style.alpha();
    // A wake carries the colour of whoever left it in place of a velocity, since it never had
    // one: drawWakes paints stationary points. Anything else keeps the style's own tint.
    boolean tinted = style.hueFromSpawnArgs() && (vx != 0.0 || vy != 0.0 || vz != 0.0);
    if (tinted) {
      this.setColor((float) vx, (float) vy, (float) vz);
      this.xd = 0.0;
      this.yd = 0.0;
      this.zd = 0.0;
    } else {
      this.setColor(style.red(), style.green(), style.blue());
    }
    this.quadSize = style.sizeBase() + random.nextFloat() * style.sizeSpread();
    this.gravity = 0.0f;
    this.hasPhysics = false;
    this.roll = random.nextFloat() * (float) Math.PI * 2;
    this.oRoll = this.roll;
  }

  @Override
  public void tick() {
    this.xd *= style.drag();
    this.yd *= style.drag();
    this.zd *= style.drag();
    super.tick();
    if (this.removed) return;
    float progress = (float) this.age / this.lifetime;
    this.alpha = style.alpha() * (1.0f - progress);
    this.quadSize *= (progress < 0.3f ? 1.04f : 0.97f);
    this.oRoll = this.roll;
    this.roll += style.spin();
    this.setSpriteFromAge(sprites);
  }

  @Override
  protected Layer getLayer() {
    return Layer.TRANSLUCENT;
  }

  /** One factory per registered type, each carrying the style that type should look like. */
  public static class Factory implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet sprites;
    private final Style style;

    public Factory(SpriteSet sprites) {
      this(sprites, GROUND_EFFECT);
    }

    public Factory(SpriteSet sprites, Style style) {
      this.sprites = sprites;
      this.style = style;
    }

    @Override
    @Nullable
    public Particle createParticle(
        SimpleParticleType type,
        ClientLevel level,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz,
        RandomSource random) {
      return new WingVortexParticle(level, x, y, z, vx, vy, vz, sprites, style);
    }
  }
}
