# Drafting Design

Date: 2026-09-20
Status: approved, awaiting implementation plan

## Goal

Put a slipstream in Slipstream. Flying behind another glider lets you ride their
wake: you gain speed, and you are gently drawn toward the centre of their wake
so you semi follow their path without ever losing control of your own flight.

Scope is the mechanic and its physics only. Rendering the wake properly is a
separate project (`drafting-rendering`), specced after this one, because the
wake this spec defines is the thing that project draws. This spec ships a
minimal particle cue for discoverability, nothing more.

Not in scope: wind, thermals, ridge lift, advancements, HUD, any change to the
Paper plugin.

## Current state

`LivingEntityMixin.travel` applies two forces to a gliding local player:
proximity-scaled forward acceleration and a bidirectional lift that drives
vertical speed toward level within a look-pitch window. Both are gated by
`ServerConfigOverride.isBoostAllowed()`, which is true only in singleplayer or
on a server running the mod or plugin, because elytra movement is client
authoritative and an unconfirmed boost looks like a speed hack.

`GroundEffectSample` and `GroundEffectSampler` already give any `LivingEntity` a
per-tick memoised surface sample. `ClientFeelHandler` already iterates gliding
players in range every client tick. Drafting reuses both.

## Architecture

### Wake trails are built client side from tracked players

Other players' positions are already replicated to every client as tracked
entities. So each client builds wake trails itself from what it already knows.
No new packets, no new payload, no new anticheat surface.

The server builds the same trails independently for its own particle emission.
Client and server trails differ slightly because of interpolation, which is
acceptable for a feel mechanic and is already true of the ground effect.

### WakeSample and WakeTrail

`WakeSample` is a record in `com.si6gma.slipstream.draft`:

```
position   Vec3    where the glider was
heading    Vec3    unit horizontal travel direction at that moment
speed      double  horizontal speed, blocks per tick
tick       int     the level tick this was recorded
```

`WakeTrail` is a fixed-capacity ring buffer of `WakeSample`, newest first, with:

```
void record(Vec3 position, Vec3 heading, double speed, int tick)
void pruneOlderThan(int tick, int maxAgeTicks)
boolean isEmpty()
List<WakeSample> samples()   // newest first, for iteration and rendering
```

Sampling runs every `wakeSampleIntervalTicks` (default 2, so ten per second) and
retains `wakeLifetimeTicks` (default 60, so three seconds). Capacity is
therefore 30 samples per glider, which is small enough that a ring buffer per
tracked player costs nothing meaningful.

A trail records only while the player is fall flying and above the existing
speed gate (`effectSpeedThreshold * maxSpeedBlocksPerTick`). A player who stops
gliding stops recording; their trail then ages out naturally rather than being
cleared, so you can still draft the last second of their flight.

### WakeTracker

`WakeTracker` owns the trails. One instance per logical side, keyed by player
UUID, holding a `WakeTrail` each.

```
void tick(Level level, SlipstreamConfig cfg)   // record and prune for every gliding player
WakeTrail trailFor(UUID id)
Collection<Map.Entry<UUID, WakeTrail>> trails()
void clear()
```

`tick` iterates `level.players()`, records a sample for each gliding player
meeting the gate, and prunes expired samples. Entries for players no longer in
the level are dropped when their trail empties.

The client instance is ticked from `ClientFeelHandler`, which already walks the
same player list each tick. The server instance is ticked once per server level
tick. Both are cleared on disconnect and on level change.

### DraftingMath

A pure-function class in `com.si6gma.slipstream.draft`, unit tested with no
Minecraft runtime. All geometry and all force curves live here.

```
static DraftQuery nearest(WakeTrail trail, Vec3 follower, int nowTick, SlipstreamConfig cfg)
static double wakeRadius(double ageSeconds, SlipstreamConfig cfg)
static double strength(double ageSeconds, double lateralOffset, double radius, SlipstreamConfig cfg)
static double boostDelta(double hSpeed, double strength, double cap, SlipstreamConfig cfg)
static double pullForce(double lateralError, double lookDivergenceDeg, double strength,
                        double pullStrength, double maxStep)
static double leaderBonus(int drafterCount, SlipstreamConfig cfg)
```

`DraftQuery` is a record carrying the result of the geometry pass:

```
Vec3 point            nearest point on the wake centreline
Vec3 toCentre         unit vector from follower toward that point
double lateralOffset  distance from follower to that point
double ageSeconds     age of that part of the wake
double strength       combined falloff, 0 to 1
Vec3 wakeHeading      the leader's heading at that point
```

### Geometry

The trail is a polyline through its sample positions. `nearest` walks
consecutive sample pairs, projects the follower onto each segment clamped to the
segment's ends, and keeps the closest. Age at that point is interpolated between
the two samples' ticks. This is O(samples) per leader, with at most 30 samples
and only leaders within range considered, so cost is trivial.

Only wake behind the leader counts. A segment is skipped when the follower is
ahead of it, meaning the vector from the segment's newer sample to the follower
has a positive dot product with that sample's heading.

### Falloff

A real wake spreads and weakens as it ages. Both curves are age driven.

```
radius(age)   = wakeBaseRadius + age * wakeSpreadRate
ageFalloff    = 1 - (age / wakeLifetimeSeconds), clamped to [0, 1]
lateralFalloff= 1 - (lateralOffset / radius(age)), clamped to [0, 1], then squared
strength      = ageFalloff * lateralFalloff
```

The lateral term is squared so the centre of the wake is meaningfully better
than its edge, matching how the existing `proximity` curve rewards precision.
`strength` is zero outside the radius, so being near a wake without being in it
does nothing.

### The two forces

Both apply only to the local player, only in `travel`, only when
`ServerConfigOverride.isBoostAllowed()` is true, and only when the player is
fall flying. On a server without the mod there is no boost and no pull, exactly
as with the ground effect today.

**Forward boost.** Applied along the follower's own horizontal heading, never
the leader's, so the boost accelerates without steering.

```
delta = strength * draftAccelerationPerTick
cap   = maxSpeedBlocksPerTick * draftSpeedMultiplier
```

`draftSpeedMultiplier` defaults to 1.15, letting a drafting flyer exceed the
normal ceiling by fifteen percent so overtaking is possible. Without it you
could only ever match the leader, which removes the point of the mechanic. The
value is server pushed with the other physics fields, so a server sets the
ceiling it is willing to allow.

When drafting stops, speed is not clipped back to the normal cap. The existing
ground effect `boostDelta` already refuses to accelerate at or above its cap, so
the excess bleeds off through normal drag rather than snapping.

**Centreline pull.** Drives lateral offset toward zero. Directly analogous to
`GroundEffectMath.liftForce`, including its no-overshoot guarantee.

```
angleFactor = 1 - (|lookDivergenceDeg| / draftReleaseAngleDeg), clamped to [0, 1]
pull        = lateralError * angleFactor * strength * draftPullStrength
result      = min(pull, maxStep)      // never past the centreline this tick
```

`lookDivergenceDeg` is the angle between the player's look direction and the
wake heading at the nearest point, both flattened to horizontal. Past
`draftReleaseAngleDeg` (default 35) the pull is zero, so looking away from the
wake frees you immediately. This deliberately mirrors the lift force's plus or
minus 30 degree look-pitch rule so the mod speaks one language about player
intent.

The pull is applied along `toCentre`. Its vertical component is scaled by
`1 - groundEffectProximity` so that near a surface the ground effect lift stays
dominant and the two systems never fight for the vertical axis. The horizontal
component is never attenuated.

**Leader bonus.** A leader with drafters behind them gains a small forward
boost, true to the real aerodynamics where a trailing body reduces the leader's
wake drag.

```
bonus = min(drafterCount, draftLeaderBonusMaxDrafters) * draftLeaderBonusPerDrafter
```

Default is 0.15 of the drafting acceleration per drafter, capped at three
drafters. The leader's own cap stays the normal `maxSpeedBlocksPerTick`; only an
actual drafter gets the raised ceiling.

Counting drafters requires knowing who is drafting whom. Each client computes
this for the local player only and the count for a leader is derived locally
from the client's own trails, which means a client applies the leader bonus to
itself when it sees others sitting in its own wake. This is consistent with
every other force in the mod being client applied under a server gate.

### Chaining

No special code. If B drafts A, B is faster, and C drafting B finds a valid
wake. The shared ceiling means a line converges on
`maxSpeedBlocksPerTick * draftSpeedMultiplier` rather than compounding, because
each follower's boost is capped independently and none of them can raise another
player's cap. A unit test asserts that a chain of five converges rather than
diverges.

### Discoverability

Minimal for this spec, since the rendering project owns the real treatment.

- Wake particles: the existing `wingVortex` particle emitted along a leader's
  trail at low density, so a wake is faintly visible before you enter it.
- Entry cue: a single positional sound when the local player's draft strength
  crosses from zero to above a threshold, using a vanilla sound event, subject
  to the existing `soundsEnabled` and `soundVolume`.

Both are client side and run regardless of whether the server has the mod, the
same as the feel pass particles.

## Config

New fields on `SlipstreamConfig`. The physics values are server pushed with the
existing six, extending `ServerConfigPayload`; the rest are client only and
follow the existing merge rule in `ServerConfigOverride.get()`.

| Field | Default | Server pushed | Notes |
| --- | --- | --- | --- |
| `draftingEnabled` | `true` | yes | Master switch for the mechanic |
| `draftAccelerationPerTick` | `0.008` | yes | Forward gain at full strength |
| `draftSpeedMultiplier` | `1.15` | yes | Ceiling multiplier while drafting |
| `draftPullStrength` | `0.25` | yes | Fraction of lateral error corrected per tick |
| `draftReleaseAngleDeg` | `35.0` | yes | Look divergence at which the pull releases |
| `wakeBaseRadius` | `1.5` | yes | Wake half width at age zero, blocks |
| `wakeSpreadRate` | `1.2` | yes | Additional half width per second of age |
| `wakeLifetimeTicks` | `60` | yes | How long a wake stays draftable |
| `wakeSampleIntervalTicks` | `2` | yes | Ticks between trail samples |
| `draftLeaderBonusPerDrafter` | `0.15` | yes | Fraction of draft acceleration |
| `draftLeaderBonusMaxDrafters` | `3` | yes | Cap on counted drafters |
| `draftParticlesEnabled` | `true` | no | Client only wake particles |

`ServerConfigPayload` currently carries six doubles. It grows to carry the new
server pushed values. The Paper plugin writes the same wire format, so the
plugin's payload builder must be extended to match or older plugins must remain
readable. **Compatibility rule: the client accepts a short payload and keeps its
local defaults for any field the server did not send.** That keeps a 1.0.x Paper
plugin working against a newer client without a lockstep release, and it is the
only change this spec makes that touches plugin compatibility.

`validatePostLoad` clamps every new numeric field, matching the existing style.

## Data flow

```
server level tick
  WakeTracker.tick(level, cfg)                  record and prune
  for each gliding ServerPlayer: wake particles along its own trail

client END_CLIENT_TICK  (ClientFeelHandler)
  WakeTracker.tick(level, cfg)                  record and prune
  wake particles (when server lacks the mod)
  entry sound for the local player

travel() tick, local player, boost allowed
  ground effect boost and lift          unchanged
  DraftingMath.nearest over each nearby leader's trail
  strongest wake wins
  forward boost along own heading, raised cap
  centreline pull, vertical part attenuated by ground effect proximity
  leader bonus if others are in my wake
```

Only the strongest wake applies. Being inside two overlapping wakes does not
stack, which removes the worst tuning risk and the obvious exploit.

## Error handling

- `nearest` returns null for an empty trail, a trail with one sample, or a
  follower outside every segment's radius. Callers treat null as not drafting.
- A trail whose samples are all expired is dropped from the tracker.
- Zero-length headings, which occur when a player's horizontal speed is at the
  floor, are never recorded because the speed gate excludes them.
- `pullForce` cannot overshoot the centreline, by the same clamp the lift force
  uses.
- A short config payload leaves unsent fields at local defaults.

## Testing

Unit tests, no Minecraft runtime needed beyond the existing bootstrap:

- `WakeTrailTest`: ring buffer wraps at capacity, newest-first ordering,
  pruning drops exactly the expired samples, empty and single-sample trails.
- `DraftingMathTest`: nearest point on a straight trail, on a curved trail, and
  clamped at segment ends; a follower ahead of the leader is excluded; radius
  grows with age; strength is one at the centre of a fresh wake and zero at the
  edge and beyond; strength decreases monotonically with both age and offset.
- `DraftingForcesTest`: boost is zero at zero strength; boost respects the
  raised cap and never exceeds it; pull drives lateral error toward zero;
  pull never overshoots; pull is zero past the release angle and scales down
  smoothly below it; leader bonus caps at the configured drafter count.
- `DraftingChainTest`: simulating five chained followers converges on the
  raised cap rather than diverging.
- `SlipstreamConfigTest`: defaults and clamps for every new field.
- `ServerConfigPayloadTest`: a short payload leaves unsent fields at defaults.

Manual checklist, which requires a working client and is the known gap:

- Two players, singleplayer LAN: follower gains speed behind the leader and is
  drawn onto their path without losing steering authority.
- Looking away from the wake releases the pull immediately.
- Follower can overtake the leader, then loses the draft once ahead.
- A chain of three converges rather than accelerating without bound.
- Vanilla server: wake particles appear, no boost and no pull.
- Near the ground, drafting does not fight the ground effect lift.

## Risks

**The pull may feel like losing control.** This is the main design risk. The
look-angle release is the mitigation and `draftPullStrength` is the dial. It
cannot be settled without flying it.

**The leader bonus is computed per client.** Two clients may briefly disagree
about how many drafters a leader has. The effect is small and self correcting,
and the alternative is a new packet, which this spec deliberately avoids.

**Payload growth touches the Paper plugin.** The short-payload rule keeps old
plugins working, but the plugin should be updated in a follow-up so servers can
tune drafting. That follow-up is out of scope here.
