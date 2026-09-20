package com.si6gma.slipstream.draft;

import net.minecraft.world.level.Level;

/** The per side tracker instances. Client and server build their trails independently. */
public final class WakeTrackers {

  private static final WakeTracker CLIENT = new WakeTracker();
  private static final WakeTracker SERVER = new WakeTracker();

  private WakeTrackers() {}

  public static WakeTracker forLevel(Level level) {
    return level.isClientSide() ? CLIENT : SERVER;
  }

  public static WakeTracker client() {
    return CLIENT;
  }

  public static WakeTracker server() {
    return SERVER;
  }

  public static void clearAll() {
    CLIENT.clear();
    SERVER.clear();
  }
}
