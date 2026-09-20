package com.si6gma.slipstream.network;

/**
 * Pure decision logic for what the server does about a joining player's protocol state, kept free
 * of any Minecraft or networking types so it is testable on its own. See {@link
 * com.si6gma.slipstream.Slipstream} for where the states are computed and the actions applied.
 */
public final class VersionPolicy {

  private VersionPolicy() {}

  /** Which of the four buckets a joining player falls into. */
  public enum ClientState {
    /** Never announced the {@code slipstream:hello} channel. Never acted on. */
    VANILLA,
    /** Sent a hello whose protocol equals the server's. */
    COMPATIBLE,
    /** Sent a hello whose protocol differs from the server's. */
    MISMATCHED,
    /** Announced the channel but sent no hello before the deadline. */
    LEGACY
  }

  /** The server operator's chosen response to a mismatched or legacy client. */
  public enum EnforcementPolicy {
    /** Send the payload anyway, restoring pre-handshake behaviour. */
    OFF,
    /** Withhold the payload and tell the player why. The default. */
    DISABLE,
    /** Disconnect the player with a message naming the required protocol. */
    KICK
  }

  /** What the server should do about a classified player. */
  public enum Action {
    /** Send the config payload, granting the client permission to apply forces. */
    SEND_CONFIG,
    /** Withhold the payload and send the player a chat message explaining why. */
    WITHHOLD_AND_MESSAGE,
    /** Disconnect the player. */
    KICK,
    /** Leave the player alone. */
    DO_NOTHING
  }

  /**
   * Decides the action for a classified client under a policy. A vanilla client is never acted on
   * under any policy, regardless of what the policy says to do with a mismatch.
   */
  public static Action decide(ClientState state, EnforcementPolicy policy) {
    if (state == ClientState.VANILLA) {
      return Action.DO_NOTHING;
    }
    if (state == ClientState.COMPATIBLE) {
      return Action.SEND_CONFIG;
    }
    // Mismatched and legacy share the same policy driven outcome.
    return switch (policy) {
      case OFF -> Action.SEND_CONFIG;
      case DISABLE -> Action.WITHHOLD_AND_MESSAGE;
      case KICK -> Action.KICK;
    };
  }
}
