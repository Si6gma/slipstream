package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GroundEffectParticlesTest {

  /** Records every call so tests can count by particle type. */
  static final class RecordingSink implements ParticleSink {
    final List<ParticleOptions> singles = new ArrayList<>();
    final List<double[]> singlePositions = new ArrayList<>();
    final List<ParticleOptions> bursts = new ArrayList<>();
    int burstParticles;

    @Override
    public void single(
        ParticleOptions type,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz,
        double speed) {
      singles.add(type);
      singlePositions.add(new double[] {x, y, z});
    }

    @Override
    public void burst(
        ParticleOptions type,
        double x,
        double y,
        double z,
        int count,
        double sx,
        double sy,
        double sz,
        double speed) {
      bursts.add(type);
      burstParticles += count;
    }

    long count(ParticleOptions type) {
      return singles.stream().filter(t -> t == type).count();
    }

    long countType(net.minecraft.core.particles.ParticleType<?> type) {
      return singles.stream().filter(t -> t.getType() == type).count();
    }
  }

  private static final Vec3 POS = new Vec3(0.5, 70.0, 0.5);
  private static final Vec3 DIR = new Vec3(1, 0, 0);
  private static final Vec3 RIGHT = new Vec3(0, 0, -1);

  @BeforeAll
  static void bootstrap() throws ReflectiveOperationException {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    if (ModParticles.wingVortex() == null) {
      // Bootstrap.bootStrap() freezes BuiltInRegistries as part of vanilla's own static init,
      // before any mod can register. In the real game Fabric's loader unfreezes registries for
      // mod init and refreezes afterward; a plain JUnit run never goes through that path, so
      // force the same registry state here to let this test register the particle type.
      Field frozen = MappedRegistry.class.getDeclaredField("frozen");
      frozen.setAccessible(true);
      frozen.setBoolean(BuiltInRegistries.PARTICLE_TYPE, false);
      ModParticles.register();
    }
  }

  private static GroundEffectSample water(double dist, double hSpeed) {
    return new GroundEffectSample(
        dist,
        GroundEffectMath.proximity(dist, 20.0),
        Blocks.WATER.defaultBlockState(),
        POS.y - dist,
        true,
        hSpeed,
        DIR,
        RIGHT);
  }

  private static GroundEffectSample ground(double dist, double hSpeed) {
    return new GroundEffectSample(
        dist,
        GroundEffectMath.proximity(dist, 20.0),
        Blocks.GRAVEL.defaultBlockState(),
        POS.y - dist,
        false,
        hSpeed,
        DIR,
        RIGHT);
  }

  /** Runs emit for ticks 0..5 so every modulus gate fires at least once. */
  private static RecordingSink run(GroundEffectSample s, SlipstreamConfig cfg) {
    RecordingSink sink = new RecordingSink();
    RandomSource random = RandomSource.create(7L);
    for (int tick = 0; tick < 6; tick++) {
      GroundEffectParticles.emit(s, cfg, tick, random, POS, sink);
    }
    return sink;
  }

  @Test
  void water_emitsSprayWakeAndContactButNoDust() {
    RecordingSink sink = run(water(1.0, 1.5), new SlipstreamConfig());
    assertTrue(sink.count(ParticleTypes.SPLASH) > 0, "spray and wake use SPLASH");
    assertTrue(sink.bursts.contains(ParticleTypes.SPLASH), "contact burst uses SPLASH");
    assertEquals(0, sink.countType(ParticleTypes.BLOCK), "no dust over water");
    assertEquals(0, sink.count(ParticleTypes.POOF), "no puffs over water");
  }

  @Test
  void water_emitsMistOverTime() {
    RecordingSink sink = new RecordingSink();
    RandomSource random = RandomSource.create(7L);
    GroundEffectSample s = water(1.0, 1.5);
    SlipstreamConfig cfg = new SlipstreamConfig();
    for (int tick = 0; tick < 30; tick++) {
      GroundEffectParticles.emit(s, cfg, tick, random, POS, sink);
    }
    assertTrue(sink.count(ParticleTypes.FALLING_WATER) > 0, "mist emits over extended time");
  }

  @Test
  void ground_emitsDustAndPuffsButNoWater() {
    RecordingSink sink = run(ground(1.0, 1.5), new SlipstreamConfig());
    assertTrue(sink.countType(ParticleTypes.BLOCK) > 0, "dust uses BLOCK");
    assertTrue(sink.count(ParticleTypes.POOF) > 0, "close proximity puffs");
    assertEquals(0, sink.count(ParticleTypes.SPLASH));
    assertEquals(0, sink.count(ParticleTypes.FALLING_WATER));
    assertTrue(sink.bursts.isEmpty(), "no contact bursts over ground");
  }

  @Test
  void ground_farAway_noPuffs() {
    // proximity at 15 blocks is well under the 0.3 puff threshold
    RecordingSink sink = run(ground(15.0, 1.5), new SlipstreamConfig());
    assertEquals(0, sink.count(ParticleTypes.POOF));
    assertTrue(sink.countType(ParticleTypes.BLOCK) > 0, "dust still emits far away");
  }

  @Test
  void vortex_spawnsMirroredWingtipPairAboveThePlayer() {
    RecordingSink sink = new RecordingSink();
    GroundEffectParticles.emit(
        ground(1.0, 1.5), new SlipstreamConfig(), 0, RandomSource.create(3L), POS, sink);

    var vortex = ModParticles.wingVortex();
    assertTrue(vortex != null, "wing vortex particle type must be registered for this test");
    assertEquals(2, sink.count(vortex), "one vortex per wingtip");

    List<double[]> tips = new ArrayList<>();
    for (int i = 0; i < sink.singles.size(); i++) {
      if (sink.singles.get(i) == vortex) tips.add(sink.singlePositions.get(i));
    }
    assertEquals(2, tips.size());
    // RIGHT is (0,0,-1), so the wingtips straddle the player on the z axis, 1.2 blocks out.
    assertEquals(POS.y + 0.3, tips.get(0)[1], 1e-9);
    assertEquals(POS.y + 0.3, tips.get(1)[1], 1e-9);
    assertEquals(POS.z - 1.2, tips.get(0)[2], 1e-9);
    assertEquals(POS.z + 1.2, tips.get(1)[2], 1e-9);
  }

  @Test
  void caps_holdAtExtremeSpeed() {
    // One tick that hits both the 2 tick and 3 tick gates: tick 0.
    RecordingSink sink = new RecordingSink();
    GroundEffectParticles.emit(
        water(0.5, 20.0), new SlipstreamConfig(), 0, RandomSource.create(1L), POS, sink);
    // spray: 8 per side capped (16), wake: 5 capped, so 21 splash singles total.
    assertEquals(21, sink.count(ParticleTypes.SPLASH), "splash singles capped at 16 + 5");
    // Mist only spawns when random.nextInt(3) == 0; this seed does not roll it at tick 0.
    assertEquals(0, sink.count(ParticleTypes.FALLING_WATER), "mist did not roll for this seed");
    // contact burst at 0.5 blocks: waterProximity 0.9, so 2 + (int)(0.9 * 3) = 4 per side
    assertEquals(8, sink.burstParticles);

    RecordingSink groundSink = new RecordingSink();
    GroundEffectParticles.emit(
        ground(0.5, 20.0), new SlipstreamConfig(), 0, RandomSource.create(1L), POS, groundSink);
    assertEquals(4, groundSink.countType(ParticleTypes.BLOCK), "dust capped at 4");
    assertEquals(2, groundSink.count(ParticleTypes.POOF), "puffs capped at 2");
  }

  @Test
  void oddTick_emitsNothing() {
    // tick 1 misses both the % 2 and % 3 gates
    RecordingSink sink = new RecordingSink();
    GroundEffectParticles.emit(
        water(0.5, 1.5), new SlipstreamConfig(), 1, RandomSource.create(1L), POS, sink);
    assertTrue(sink.singles.isEmpty());
    assertTrue(sink.bursts.isEmpty());
  }

  @Test
  void particlesDisabled_emitsNothing() {
    SlipstreamConfig cfg = new SlipstreamConfig();
    cfg.particlesEnabled = false;
    RecordingSink sink = run(water(0.5, 1.5), cfg);
    assertTrue(sink.singles.isEmpty());
    assertTrue(sink.bursts.isEmpty());
  }

  @Test
  void water_aboveSprayHeight_noWaterParticles() {
    // 6 blocks up over water: above the 5 block spray height, and the ground branch is skipped
    // because the surface is water, so nothing at all is emitted.
    RecordingSink sink = run(water(6.0, 1.5), new SlipstreamConfig());
    assertEquals(0, sink.count(ParticleTypes.SPLASH));
    assertEquals(0, sink.countType(ParticleTypes.BLOCK));
    assertFalse(sink.bursts.contains(ParticleTypes.SPLASH));
  }
}
