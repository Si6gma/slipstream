package com.si6gma.slipstream.paper;

/**
 * Duplicates {@code SlipstreamProtocol.VERSION} from the Fabric mod module, at
 * {@code src/main/java/com/si6gma/slipstream/network/SlipstreamProtocol.java}.
 *
 * <p>The plugin cannot depend on the mod jar, so this constant is kept in sync by hand. Both
 * declarations carry a comment naming the other, so a change to one is an obvious prompt to
 * change the other.
 */
final class SlipstreamProtocol {

  static final int VERSION = 2;

  private SlipstreamProtocol() {}
}
