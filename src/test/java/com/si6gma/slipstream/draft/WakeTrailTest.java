package com.si6gma.slipstream.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WakeTrailTest {

  private static final Vec3 EAST = new Vec3(1, 0, 0);

  private static WakeTrail filled(int count, int startTick, int step) {
    WakeTrail trail = new WakeTrail();
    for (int i = 0; i < count; i++) {
      trail.record(new Vec3(i, 70, 0), EAST, 1.0, startTick + i * step);
    }
    return trail;
  }

  @Test
  void newTrail_isEmpty() {
    WakeTrail trail = new WakeTrail();
    assertTrue(trail.isEmpty());
    assertEquals(0, trail.size());
    assertTrue(trail.samples().isEmpty());
  }

  @Test
  void samples_areNewestFirst() {
    WakeTrail trail = filled(3, 100, 2);
    List<WakeSample> s = trail.samples();
    assertEquals(3, s.size());
    assertEquals(104, s.get(0).tick());
    assertEquals(102, s.get(1).tick());
    assertEquals(100, s.get(2).tick());
  }

  @Test
  void ringBuffer_dropsOldestBeyondCapacity() {
    WakeTrail trail = filled(WakeTrail.CAPACITY + 5, 0, 1);
    assertEquals(WakeTrail.CAPACITY, trail.size());
    List<WakeSample> s = trail.samples();
    assertEquals(WakeTrail.CAPACITY + 4, s.get(0).tick());
    assertEquals(5, s.get(s.size() - 1).tick());
  }

  @Test
  void shouldRecord_honoursInterval() {
    WakeTrail trail = new WakeTrail();
    assertTrue(trail.shouldRecord(100, 2), "an empty trail always records");
    trail.record(Vec3.ZERO, EAST, 1.0, 100);
    assertFalse(trail.shouldRecord(101, 2));
    assertTrue(trail.shouldRecord(102, 2));
    assertTrue(trail.shouldRecord(150, 2));
  }

  @Test
  void shouldRecord_afterTickCounterResets() {
    // A dimension change can move tickCount backwards. Record rather than stall forever.
    WakeTrail trail = new WakeTrail();
    trail.record(Vec3.ZERO, EAST, 1.0, 5000);
    assertTrue(trail.shouldRecord(10, 2));
  }

  @Test
  void pruneOlderThan_dropsExactlyTheExpired() {
    WakeTrail trail = filled(5, 100, 2); // ticks 100,102,104,106,108
    trail.pruneOlderThan(110, 6); // keep tick >= 104
    List<WakeSample> s = trail.samples();
    assertEquals(3, s.size());
    assertEquals(108, s.get(0).tick());
    assertEquals(104, s.get(2).tick());
  }

  @Test
  void pruneOlderThan_canEmptyTheTrail() {
    WakeTrail trail = filled(3, 100, 2);
    trail.pruneOlderThan(500, 10);
    assertTrue(trail.isEmpty());
  }
}
