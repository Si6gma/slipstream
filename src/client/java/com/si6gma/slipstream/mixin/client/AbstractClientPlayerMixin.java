package com.si6gma.slipstream.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.si6gma.slipstream.client.ClientFeelHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

  /**
   * Widens FOV by the smoothed ground effect kick. Vanilla has already applied the accessibility
   * FOV effects scale to its own modifier via lerp, so multiplying the kick by the same scale
   * keeps that slider meaningful for this effect too.
   *
   * <p>{@code @ModifyReturnValue} rather than a cancellable {@code @Inject} at RETURN. The two do
   * the same thing here, but this one composes: several mods modifying the same return value each
   * see the previous one's result, where cancellable injects race to be last and silently discard
   * each other. MixinExtras has shipped inside Fabric Loader since 0.15, so it costs no
   * dependency. The argument capture keeps {@code fovEffectScale} available.
   */
  @ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
  private float slipstream$applyFovKick(
      float original, boolean firstPerson, float fovEffectScale) {
    if ((Object) this != Minecraft.getInstance().player) return original;
    float kick = ClientFeelHandler.fovKick();
    if (kick == 0.0f) return original;
    return original * (1.0f + kick * fovEffectScale);
  }
}
