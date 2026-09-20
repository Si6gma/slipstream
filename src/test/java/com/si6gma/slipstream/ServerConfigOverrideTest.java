package com.si6gma.slipstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.si6gma.slipstream.network.ServerConfigOverride;
import com.si6gma.slipstream.network.ServerConfigPayload;
import org.junit.jupiter.api.AfterEach;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServerConfigOverrideTest {

  @AfterEach
  void reset() {
    ServerConfigOverride.clear();
    ServerConfigOverride.setLocalConfigForTests(null);
    ServerConfigOverride.setSingleplayer(false);
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

    ServerConfigOverride.apply(40.0, 0.01, 2.0, 8.0, 0.9, 0.5, null);
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
    ServerConfigOverride.apply(40.0, 0.01, 2.0, 8.0, 0.9, 0.5, null);
    assertTrue(ServerConfigOverride.get().soundsEnabled);
    local.soundsEnabled = false;
    assertFalse(ServerConfigOverride.get().soundsEnabled, "screen edits must apply live");
  }

  @Test
  void isBoostAllowed_withNoOverrideAndNotSingleplayer_isFalse() {
    assertFalse(ServerConfigOverride.isBoostAllowed());
  }

  @Test
  void isBoostAllowed_inSingleplayer_isTrue() {
    ServerConfigOverride.setSingleplayer(true);
    assertTrue(ServerConfigOverride.isBoostAllowed());
  }

  @Test
  void isBoostAllowed_afterServerOverride_isTrue() {
    ServerConfigOverride.apply(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, null);
    assertTrue(ServerConfigOverride.isBoostAllowed());
  }

  @Test
  void isBoostAllowed_afterDisconnect_isFalseAgain() {
    ServerConfigOverride.apply(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, null);
    ServerConfigOverride.clear();
    assertFalse(ServerConfigOverride.isBoostAllowed());
  }

  @Test
  void get_everyConfigFieldIsExplicitlyClassified() {
    // A hand written field-by-field merge is exactly the shape that lets a new field slip through
    // and silently fall back to compiled defaults, which is how camera assist broke. Adding a
    // field without deciding who owns it now fails here instead of in the air.
    Set<String> serverOwned =
        Set.of(
            "effectHeightBlocks", "accelerationPerTick", "maxSpeedBlocksPerTick",
            "waterSprayHeightBlocks", "liftStrength", "effectSpeedThreshold",
            "draftingEnabled", "draftAccelerationPerTick", "draftSpeedMultiplier",
            "draftPullStrength", "draftReleaseAngleDeg", "wakeBaseRadius", "wakeSpreadRate",
            "wakeLifetimeTicks", "wakeSampleIntervalTicks", "draftLeaderBonusPerDrafter",
            "draftLeaderBonusMaxDrafters", "draftCameraAssistStrength", "draftCameraAssist");
    Set<String> clientOwned =
        Set.of(
            "particlesEnabled", "soundsEnabled", "soundVolume", "fovKickEnabled",
            "fovKickStrength", "clientParticlesOnVanillaServers", "remotePlayerParticles",
            "draftParticlesEnabled");
    Set<String> serverLocalOnly = Set.of("versionEnforcement", "handshakeTimeoutTicks");

    for (Field field : SlipstreamConfig.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) continue;
      String name = field.getName();
      assertTrue(
          serverOwned.contains(name) || clientOwned.contains(name)
              || serverLocalOnly.contains(name),
          "config field '" + name + "' is not classified. Decide who owns it and make sure get()"
              + " copies it from the right side.");
    }
  }

  @Test
  void get_cameraAssistTakesStrengthFromServerAndRespectsTheLocalOptOut() {
    SlipstreamConfig local = new SlipstreamConfig();
    local.draftCameraAssist = true;
    local.draftCameraAssistStrength = 0.05;
    ServerConfigOverride.setLocalConfigForTests(local);
    ServerConfigOverride.apply(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, draftSettings(true, 0.42));

    assertEquals(0.42, ServerConfigOverride.get().draftCameraAssistStrength, 1e-9);
    assertTrue(ServerConfigOverride.get().draftCameraAssist);

    local.draftCameraAssist = false;
    assertFalse(ServerConfigOverride.get().draftCameraAssist);
  }

  @Test
  void get_cameraAssistOffOnTheServerCannotBeTurnedBackOnLocally() {
    SlipstreamConfig local = new SlipstreamConfig();
    local.draftCameraAssist = true;
    ServerConfigOverride.setLocalConfigForTests(local);
    ServerConfigOverride.apply(20.0, 0.005, 1.5, 5.0, 0.6, 0.3, draftSettings(false, 0.5));
    assertFalse(ServerConfigOverride.get().draftCameraAssist);
  }

  private static ServerConfigPayload.DraftSettings draftSettings(
      boolean cameraAssist, double cameraAssistStrength) {
    SlipstreamConfig source = new SlipstreamConfig();
    source.draftCameraAssist = cameraAssist;
    source.draftCameraAssistStrength = cameraAssistStrength;
    return ServerConfigPayload.DraftSettings.from(source);
  }
}
