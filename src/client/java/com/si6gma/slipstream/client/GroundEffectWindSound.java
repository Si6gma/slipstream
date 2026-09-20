package com.si6gma.slipstream.client;

import com.si6gma.slipstream.GroundEffectMath;
import com.si6gma.slipstream.LocalGroundEffectState;
import com.si6gma.slipstream.SlipstreamConfig;
import com.si6gma.slipstream.network.ServerConfigOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Looping wind layer on the vanilla elytra sound, scaled by ground effect proximity and speed and
 * pitched up toward max speed. Mirrors {@code ElytraOnPlayerSoundInstance}: follows the player
 * and stops itself when the player stops gliding or leaves ground effect for half a second.
 */
public class GroundEffectWindSound extends AbstractTickableSoundInstance {

  private static final int SILENT_TICKS_BEFORE_STOP = 10;

  private final LocalPlayer player;
  private int silentTicks;

  public GroundEffectWindSound(LocalPlayer player) {
    super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
    this.player = player;
    this.looping = true;
    this.delay = 0;
    this.volume = 0.0f;
  }

  @Override
  public void tick() {
    SlipstreamConfig cfg = ServerConfigOverride.get();
    if (player.isRemoved()
        || Minecraft.getInstance().player != player
        || !player.isFallFlying()
        || !cfg.soundsEnabled) {
      stop();
      return;
    }
    double proximity = LocalGroundEffectState.proximity();
    if (proximity <= 0.0) {
      if (++silentTicks >= SILENT_TICKS_BEFORE_STOP) {
        stop();
        return;
      }
    } else {
      silentTicks = 0;
    }
    double speedRatio = LocalGroundEffectState.speedRatio();
    this.x = player.getX();
    this.y = player.getY();
    this.z = player.getZ();
    this.volume = (float) GroundEffectMath.windVolume(proximity, speedRatio, cfg.soundVolume);
    this.pitch = (float) GroundEffectMath.windPitch(speedRatio);
  }
}
