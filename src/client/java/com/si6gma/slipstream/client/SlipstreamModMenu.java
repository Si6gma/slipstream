package com.si6gma.slipstream.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Mod Menu entrypoint. Only loaded by Mod Menu itself, so it is safe to reference its API. */
public class SlipstreamModMenu implements ModMenuApi {

  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
      return parent -> null;
    }
    // Method reference resolves SlipstreamConfigScreen lazily, so Cloth classes are only
    // touched when cloth-config is present.
    return SlipstreamConfigScreen::create;
  }
}
