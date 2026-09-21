package com.si6gma.slipstream.draft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * A glider's recent path, newest sample first. Fixed capacity ring buffer: recording past capacity
 * discards the oldest sample. Age based pruning keeps the trail inside the configured lifetime.
 */
public final class WakeTrail {

  /** Holds the longest useful trail: the maximum lifetime at the shortest sampling interval. */
  public static final int CAPACITY = 200;

  private final WakeSample[] buffer = new WakeSample[CAPACITY];
  private int head = -1;
  private int count;
  private int lastRecordedTick = Integer.MIN_VALUE;

  /** True when enough ticks have passed since the last sample, or nothing has been recorded. */
  public boolean shouldRecord(int tick, int intervalTicks) {
    if (count == 0) return true;
    int elapsed = tick - lastRecordedTick;
    // A negative elapsed means the tick counter moved backwards, which happens on a dimension
    // change. Record rather than stalling until the counter catches up.
    return elapsed < 0 || elapsed >= intervalTicks;
  }

  public void record(Vec3 position, double speed, int tick) {
    record(position, speed, tick, 1.0);
  }

  public void record(Vec3 position, double speed, int tick, double boost) {
    head = (head + 1) % CAPACITY;
    buffer[head] = new WakeSample(position, speed, tick, boost);
    if (count < CAPACITY) count++;
    lastRecordedTick = tick;
  }

  /**
   * Drops every sample older than maxAgeTicks relative to nowTick. A nowTick meaningfully behind
   * the newest sample means the tick counter reset, which happens on a dimension change: the whole
   * trail is then stamped in a counter that no longer exists, so it is discarded rather than aged.
   */
  public void pruneOlderThan(int nowTick, int maxAgeTicks) {
    if (count > 0 && nowTick < buffer[head].tick() - maxAgeTicks) {
      clear();
      return;
    }
    while (count > 0) {
      int oldest = oldestIndex();
      if (nowTick - buffer[oldest].tick() <= maxAgeTicks) break;
      buffer[oldest] = null;
      count--;
    }
  }

  /** Discards every sample. Used when the tick counter resets under the trail. */
  public void clear() {
    Arrays.fill(buffer, null);
    head = -1;
    count = 0;
    lastRecordedTick = Integer.MIN_VALUE;
  }

  public boolean isEmpty() {
    return count == 0;
  }

  public int size() {
    return count;
  }

  /** Samples newest first. Returns a fresh list; callers may iterate freely. */
  public List<WakeSample> samples() {
    List<WakeSample> out = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      out.add(buffer[Math.floorMod(head - i, CAPACITY)]);
    }
    return out;
  }

  private int oldestIndex() {
    return Math.floorMod(head - (count - 1), CAPACITY);
  }

  /**
   * Sample at an index counted from the newest, without allocating. Index 0 is the newest sample.
   * Callers iterate 0 to {@link #size()} exclusive.
   */
  public WakeSample sampleAt(int indexFromNewest) {
    if (indexFromNewest < 0 || indexFromNewest >= count) {
      throw new IndexOutOfBoundsException("index " + indexFromNewest + " of " + count);
    }
    return buffer[Math.floorMod(head - indexFromNewest, CAPACITY)];
  }
}
