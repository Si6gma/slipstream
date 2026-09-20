package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.si6gma.slipstream.network.ServerConfigOverride;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServerConfigOverrideTest {

  @AfterEach
  void reset() {
    ServerConfigOverride.clear();
    ServerConfigOverride.setLocalConfigForTests(null);
  }

  @Test
  void get_withoutOverride_returnsLocalConfig() {
    SlipstreamConfig local = new SlipstreamConfig();
    ServerConfigOverride.setLocalConfigForTests(local);
    assertTrue(ServerConfigOverride.get() == local);
  }

  @Test
  void get_withOverride_usesServerPhysicsAndLocalClientFields() {
    SlipstreamConfig local = new SlipstreamConfig();
    local.soundsEnabled = false;
    local.soundVolume = 0.25;
    local.fovKickEnabled = false;
    local.fovKickStrength = 0.3;
    local.clientParticlesOnVanillaServers = false;
    local.remotePlayerParticles = false;
    local.particlesEnabled = false;
    ServerConfigOverride.setLocalConfigForTests(local);

    ServerConfigOverride.apply(40.0, 0.01, 2.0, 8.0, 0.9, 0.5);
    SlipstreamConfig merged = ServerConfigOverride.get();

    assertEquals(40.0, merged.effectHeightBlocks, 1e-9);
    assertEquals(0.01, merged.accelerationPerTick, 1e-9);
    assertEquals(2.0, merged.maxSpeedBlocksPerTick, 1e-9);
    assertEquals(8.0, merged.waterSprayHeightBlocks, 1e-9);
    assertEquals(0.9, merged.liftStrength, 1e-9);
    assertEquals(0.5, merged.effectSpeedThreshold, 1e-9);

    assertFalse(merged.soundsEnabled);
    assertEquals(0.25, merged.soundVolume, 1e-9);
    assertFalse(merged.fovKickEnabled);
    assertEquals(0.3, merged.fovKickStrength, 1e-9);
    assertFalse(merged.clientParticlesOnVanillaServers);
    assertFalse(merged.remotePlayerParticles);
    assertFalse(merged.particlesEnabled);
  }

  @Test
  void get_withOverride_reflectsLaterLocalEdits() {
    SlipstreamConfig local = new SlipstreamConfig();
    ServerConfigOverride.setLocalConfigForTests(local);
    ServerConfigOverride.apply(40.0, 0.01, 2.0, 8.0, 0.9, 0.5);
    assertTrue(ServerConfigOverride.get().soundsEnabled);
    local.soundsEnabled = false;
    assertFalse(ServerConfigOverride.get().soundsEnabled, "screen edits must apply live");
  }
}
