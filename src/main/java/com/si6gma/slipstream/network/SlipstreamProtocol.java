package com.si6gma.slipstream.network;

/**
 * The wire protocol version for Slipstream's network payloads. Increments only when the wire
 * format of {@link HelloPayload} or {@link ServerConfigPayload} changes, never for a bugfix or a
 * feature that does not touch networking. Two releases sharing this number interoperate.
 *
 * <p>The Paper plugin duplicates this constant, because it is a separate module with no
 * dependency on this mod's jar. Both declarations must change together.
 */
public final class SlipstreamProtocol {

  public static final int VERSION = 1;

  private SlipstreamProtocol() {}
}
