package com.si6gma.slipstream.draft;

/**
 * The one tracker instance. Wake trails are built client side from tracked players, so there has
 * been nothing to track on the server since server side wake tracking was dropped, and the side
 * agnostic lookup that used to pick between them had only ever one answer left.
 */
public final class WakeTrackers {

  private static final WakeTracker CLIENT = new WakeTracker();

  private WakeTrackers() {}

  public static WakeTracker client() {
    return CLIENT;
  }
}
