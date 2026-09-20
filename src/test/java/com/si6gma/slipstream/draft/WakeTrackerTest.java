package com.si6gma.slipstream.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.si6gma.slipstream.SlipstreamConfig;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WakeTrackerTest {

  private static final UUID A = UUID.nameUUIDFromBytes("a".getBytes());
  private static final UUID B = UUID.nameUUIDFromBytes("b".getBytes());
  @Test
  void trailFor_unknownId_returnsEmptyTrailNotNull() {
    WakeTracker tracker = new WakeTracker();
    WakeTrail trail = tracker.trailFor(A);
    assertNotNull(trail);
    assertTrue(trail.isEmpty());
    assertEquals(0, tracker.trackedCount(), "a lookup must not create a tracked entry");
  }

  @Test
  void record_honoursSampleInterval() {
    WakeTracker tracker = new WakeTracker();
    SlipstreamConfig cfg = new SlipstreamConfig(); // interval 2
    tracker.record(A, new Vec3(0, 70, 0), 1.0, 100, cfg);
    tracker.record(A, new Vec3(1, 70, 0), 1.0, 101, cfg);
    tracker.record(A, new Vec3(2, 70, 0), 1.0, 102, cfg);
    assertEquals(2, tracker.trailFor(A).size());
  }

  @Test
  void record_keepsPlayersSeparate() {
    WakeTracker tracker = new WakeTracker();
    SlipstreamConfig cfg = new SlipstreamConfig();
    tracker.record(A, new Vec3(0, 70, 0), 1.0, 100, cfg);
    tracker.record(B, new Vec3(0, 70, 9), 1.0, 100, cfg);
    assertEquals(2, tracker.trackedCount());
    assertEquals(0.0, tracker.trailFor(A).samples().get(0).position().z, 1e-9);
    assertEquals(9.0, tracker.trailFor(B).samples().get(0).position().z, 1e-9);
  }

  @Test
  void prune_dropsExpiredSamplesAndThenTheEntry() {
    WakeTracker tracker = new WakeTracker();
    SlipstreamConfig cfg = new SlipstreamConfig(); // lifetime 60 ticks
    tracker.record(A, new Vec3(0, 70, 0), 1.0, 100, cfg);
    tracker.record(A, new Vec3(2, 70, 0), 1.0, 102, cfg);
    assertEquals(1, tracker.trackedCount());

    tracker.prune(140, cfg); // 140-102 = 38, still inside the 60 tick lifetime
    assertEquals(1, tracker.trackedCount());

    tracker.prune(200, cfg); // everything is now older than the lifetime
    assertEquals(0, tracker.trackedCount(), "an emptied trail stops being tracked");
    assertTrue(tracker.trailFor(A).isEmpty());
  }

  @Test
  void clear_dropsEverything() {
    WakeTracker tracker = new WakeTracker();
    SlipstreamConfig cfg = new SlipstreamConfig();
    tracker.record(A, Vec3.ZERO, 1.0, 100, cfg);
    tracker.record(B, Vec3.ZERO, 1.0, 100, cfg);
    tracker.clear();
    assertEquals(0, tracker.trackedCount());
  }
}
