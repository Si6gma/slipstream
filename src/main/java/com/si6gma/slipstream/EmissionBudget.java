package com.si6gma.slipstream;

/**
 * How much of a glider's particle and sound output actually gets emitted.
 *
 * <p>Nothing capped emission before. One glider over water sends up to twenty two particle packets
 * every three ticks plus vortices and bursts every two, and the server broadcasts each of them to
 * every tracking player, so forty gliders is hundreds of packets a tick fanned out forty ways.
 * That is a bandwidth and packet count problem rather than a CPU one, which is why the answer is
 * to send less rather than to compute it faster.
 *
 * <p>Sounds are worse than that. The client started a positional sound per visible glider every
 * four ticks, and Minecraft's sound channel pool is finite: past it the mixer starts dropping
 * sounds, and the ones it drops are not necessarily ours. A mod that silences vanilla footsteps in
 * a crowd is a worse bug than one that is quiet at a distance.
 */
public final class EmissionBudget {

  /** Gliders nearest the viewer that keep full detail. */
  public static final int FULL_DETAIL_GLIDERS = 3;

  /** Sounds Slipstream may start in one client tick, across every glider in view. */
  public static final int MAX_SOUNDS_PER_TICK = 3;

  /** Gliders' worth of particles the server is willing to broadcast in one tick. */
  public static final int SERVER_PARTICLE_BUDGET = 6;

  private EmissionBudget() {}

  /**
   * Share of normal particle output for the glider at this distance rank, 0 being the nearest.
   *
   * <p>Falls away as a reciprocal rather than a cliff, so a pack thins smoothly as it spreads out
   * instead of a glider's wake popping in and out as it trades places with another.
   */
  public static double viewerShare(int rank) {
    if (rank < 0) return 0.0;
    if (rank < FULL_DETAIL_GLIDERS) return 1.0;
    return 1.0 / (rank - FULL_DETAIL_GLIDERS + 2);
  }

  /**
   * Share every glider gets when the server is broadcasting for this many at once.
   *
   * <p>Deliberately equal rather than ranked. The server has no single viewer to measure distance
   * from, and cutting an arbitrary subset entirely would make some players' wakes flicker as the
   * set changed. Thinning everyone equally degrades gracefully and keeps the packet count bounded.
   */
  public static double fairShare(int gliderCount) {
    if (gliderCount <= 0) return 0.0;
    return Math.min(1.0, (double) SERVER_PARTICLE_BUDGET / gliderCount);
  }

  /** Whether the glider at this distance rank may start a sound this tick. */
  public static boolean allowSound(int rank) {
    return rank >= 0 && rank < MAX_SOUNDS_PER_TICK;
  }
}
