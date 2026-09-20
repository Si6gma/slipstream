package com.si6gma.slipstream.draft;

import com.si6gma.slipstream.SlipstreamConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Wake trails for every glider on one logical side, keyed by player id. Knows nothing about levels
 * or entities: callers feed it positions, which keeps it unit testable and side agnostic.
 */
public final class WakeTracker {

  private final WakeTrail empty = new WakeTrail();

  private final Map<UUID, WakeTrail> trails = new HashMap<>();

  /** Records a sample for this glider if the configured sampling interval has elapsed. */
  public void record(UUID id, Vec3 position, double speed, int tick, SlipstreamConfig cfg) {
    WakeTrail trail = trails.computeIfAbsent(id, key -> new WakeTrail());
    if (!trail.shouldRecord(tick, cfg.wakeSampleIntervalTicks)) return;
    trail.record(position, speed, tick);
  }

  /** Drops expired samples, then forgets any glider whose trail has emptied. */
  public void prune(int nowTick, SlipstreamConfig cfg) {
    Iterator<Map.Entry<UUID, WakeTrail>> it = trails.entrySet().iterator();
    while (it.hasNext()) {
      WakeTrail trail = it.next().getValue();
      trail.pruneOlderThan(nowTick, cfg.wakeLifetimeTicks);
      if (trail.isEmpty()) it.remove();
    }
  }

  /**
   * Never null. An untracked glider yields this tracker's own empty trail, which yields no draft.
   */
  public WakeTrail trailFor(UUID id) {
    WakeTrail trail = trails.get(id);
    return trail != null ? trail : empty;
  }

  public Set<UUID> ids() {
    return trails.keySet();
  }

  public int trackedCount() {
    return trails.size();
  }

  public void clear() {
    trails.clear();
  }
}
