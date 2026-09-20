package com.si6gma.slipstream.mixin.client;

import com.si6gma.slipstream.client.ClientFeelHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

  /**
   * Widens FOV by the smoothed ground effect kick. Vanilla has already applied the accessibility
   * FOV effects scale to its own modifier via lerp; multiplying the kick by the same scale keeps
   * the slider meaningful for this effect too. Plain Inject at RETURN so the target's parameters
   * can be captured without depending on MixinExtras.
   */
  @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
  private void slipstream$applyFovKick(
      boolean firstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
    if ((Object) this != Minecraft.getInstance().player) return;
    float kick = ClientFeelHandler.fovKick();
    if (kick == 0.0f) return;
    cir.setReturnValue(cir.getReturnValueF() * (1.0f + kick * fovEffectScale));
  }
}
