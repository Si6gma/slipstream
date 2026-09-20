# Feel Pass Design

Date: 2026-09-20
Status: approved, awaiting implementation plan

## Goal

Make Slipstream feel like a finished mod without adding new gameplay. Four
deliverables, all client side except one small server change:

1. Sound: wind rush, water wake, and ground skim, built from vanilla sound
   events only.
2. FOV kick tied to ground effect speed.
3. Particles rendered by the client on servers that do not have the mod, for
   every gliding player in range.
4. An in-game config screen via Cloth Config and Mod Menu.

The Paper plugin is unchanged. No new particle types, no custom audio files, no
HUD, no advancements. The missing `assets/slipstream/icon.png` referenced by
`fabric.mod.json` is noted but out of scope.

## Current state

`LivingEntityMixin.travel` does everything: heightmap precheck, cached raycast,
proximity, client boost and lift, and roughly 200 lines of server particle
spawning. Nothing in the mod plays a sound or touches FOV. On a server without
the mod or plugin the client does nothing at all.

## Architecture

### GroundEffectSample

A record in `com.si6gma.slipstream`:

```
distToSurface   double
proximity       double   GroundEffectMath.proximity(...)
surfaceBlock    BlockState
surfaceY        double
isWater         boolean
hSpeed          double
travelDir       Vec3     unit horizontal velocity
right           Vec3     travelDir rotated 90 degrees
```

`GroundEffectSample` is pure data. It carries no config and no entity reference.

### Sampling through the mixin

`LivingEntityMixin` keeps its four `@Unique` cache fields and gains a public
method exposed through a duck interface `GroundEffectSampler`:

```
@Nullable GroundEffectSample slipstream$sample(SlipstreamConfig cfg);
```

The method contains the early exits that exist today (not fall flying,
underwater or in lava, horizontal speed below the floor, heightmap precheck,
raycast cache, miss or out of range) and returns `null` when any of them fires.
The travel injection calls it first and returns immediately on `null`. The
client tick handler casts any gliding `Player` to `GroundEffectSampler` and
calls the same method, so remote players share the same per entity cache and
the same 3 tick reuse rule. No second cache is introduced.

The travel injection keeps the boost and lift code exactly as it is today.

### GroundEffectParticles and ParticleSink

The particle geometry moves verbatim from the mixin into
`com.si6gma.slipstream.GroundEffectParticles` with one public method:

```
static void emit(GroundEffectSample s, SlipstreamConfig cfg, int tick,
                 RandomSource random, Vec3 pos, ParticleSink sink)
```

`ParticleSink` is an interface with two methods:

```
void single(ParticleOptions type, double x, double y, double z,
            double vx, double vy, double vz, double speed);
void burst(ParticleOptions type, double x, double y, double z, int count,
           double spreadX, double spreadY, double spreadZ, double speed);
```

`single` maps to the current `sendParticles(type, x, y, z, 0, vx, vy, vz, speed)`
calls, carrying the old trailing speed through unchanged. Vanilla clients
multiply a count 0 velocity by that speed, and the old code passes 0 for the
wing vortex and ground dust, so those currently arrive with zero velocity. That
quirk is preserved here and flagged as a separate follow-up. `burst` maps to the current `sendParticles(type, x, y, z, count, dx, dy,
dz, speed)` calls used for the water contact burst. There are exactly two burst
call sites today, so the mapping is mechanical.

Two implementations:

- `ServerParticleSink(ServerLevel)`: wraps `level.sendParticles`. The `single`
  case passes count 0 and the same trailing speed argument the current code
  passes, so wire behaviour is byte for byte identical.
- `LocalParticleSink(Level)`: lives in the main source set (`Level.addParticle`
  exists on the common class and is a no op on the server) so the mixin free
  spread logic can be unit tested. `single` calls `level.addParticle`.
  `burst` loops `count` times and adds a particle at position plus
  `random.nextGaussian() * spread` on each axis with velocity
  `random.nextGaussian() * speed` on each axis. This mirrors how the vanilla
  client expands a counted particle packet.

Per type caps and the tick modulus gates (every 2 ticks for vortex and contact,
every 3 ticks for spray, wake, mist, dust, and puffs) move with the code and
do not change.

`emit` is skipped entirely when `cfg.particlesEnabled` is false, matching
current server behaviour.

### Client particle tick

Registered in `SlipstreamClient` on `ClientTickEvents.END_CLIENT_TICK`. Runs
only when all of the following hold:

- `cfg.clientParticlesOnVanillaServers` is true.
- `ServerConfigOverride.isActive()` is false (no Fabric server or Paper plugin
  pushed config).
- `client.hasSingleplayerServer()` is false (the integrated server runs the
  server path for the host, including LAN hosts).

For each `Player` in `level.players()` that is fall flying and within 64 blocks
of the camera, it calls `slipstream$sample(cfg)` and, on a non null result,
`GroundEffectParticles.emit(...)` with the `LocalParticleSink`. Remote players
are skipped when `cfg.remotePlayerParticles` is false. The tick counter passed
to `emit` is the player's `tickCount`, same as the server path.

### LAN fix

`Slipstream.onInitialize` currently sends `ServerConfigPayload` only when
`server.isDedicatedServer()`. Change this to send to every joining player
except the singleplayer owner (`server.isSingleplayerOwner(profile)`). LAN
guests then receive the override, which enables their boost and suppresses the
client particle tick so they do not see doubled particles. Sending nothing to
the owner keeps singleplayer logs and behaviour unchanged.

### LocalGroundEffectState

A static holder in `com.si6gma.slipstream` (main source set, because the mixin
that writes it lives there; it is inert on a dedicated server):

```
static volatile double proximity;    0 when not in ground effect
static volatile double speedRatio;   hSpeed / maxSpeed clamped to [0, 1]
static volatile boolean overWater;
```

The travel injection writes these for the local player every tick it runs and
resets them to zero when it early exits or the entity is not the local player.
Sound and FOV read from here. Neither performs its own raycast.

## Sound

All sound is client side. No packets, no server changes. Every sound respects
`cfg.soundsEnabled` and scales by `cfg.soundVolume`.

### Wind rush (local player only)

`GroundEffectWindSound extends AbstractTickableSoundInstance` on
`SoundEvents.ELYTRA_FLYING`, source `PLAYERS`, looping, `relative = true` at
the origin so it follows the listener. Started by the client tick handler when
`LocalGroundEffectState.proximity > 0` and the player is fall flying, if no
instance is active. Each tick:

```
volume = 0.6 * proximity * speedRatio * cfg.soundVolume
pitch  = 1.0 + 0.4 * speedRatio
```

The instance stops itself after 10 consecutive ticks with `proximity == 0` or
when the player stops gliding. The 0.6 ceiling keeps it a layer under the
vanilla elytra loop, which already runs at up to full volume, so the combined
effect is a rising whistle rather than a second wind track.

### Water wake (every glider in range)

Every 6 ticks, for each sampled player whose sample has `isWater` and
`distToSurface <= cfg.waterSprayHeightBlocks`, play
`SoundEvents.PLAYER_SWIM` positionally at the wake point one block behind the
player on the surface:

```
waterProximity = 1 - distToSurface / waterSprayHeightBlocks
volume = 0.5 * waterProximity * min(hSpeed / maxSpeed, 1) * cfg.soundVolume
pitch  = 1.1 + random * 0.2
```

This runs from the same client tick loop as particles but is not gated on the
server lacking the mod; sound is always client side. Remote gliders are skipped
when `cfg.remotePlayerParticles` is false so the two settings stay in step.

### Ground skim (every glider in range)

Every 4 ticks, for each sampled player whose sample is not water, not air, and
has `proximity > 0.6`, play the surface block's step sound
(`surfaceBlock.getSoundType().getStepSound()`) positionally at the surface
below the player:

```
volume = 0.15 * proximity * cfg.soundVolume
pitch  = 0.8 + random * 0.2
```

## FOV kick

A MixinExtras `@ModifyReturnValue` on
`AbstractClientPlayer.getFieldOfViewModifier(boolean, float)`. Applies only
when the instance is `Minecraft.getInstance().player`,
`cfg.fovKickEnabled` is true, and `ServerConfigOverride.isBoostAllowed()` is
true. On servers without the mod there is no extra speed, so there is no kick.

A client side float `kick` is smoothed each client tick:

```
target = cfg.fovKickStrength * proximity * speedRatio
kick  += (target - kick) * 0.1
```

The modified return is `original * (1 + kick * fovEffectScale)` where
`fovEffectScale` is the float parameter vanilla already passes from the
accessibility slider. The implementation plan must confirm which of the two
parameters is the scale in 26.2 before wiring it. Default strength 0.1 gives a
10 percent widening at full proximity and max speed.

## Config

### New fields on SlipstreamConfig

All client only. The server payload and `ServerConfigOverride.apply` do not
carry them, so a server can never override them. `validatePostLoad` clamps
them.

| Field                            | Default | Range      |
| -------------------------------- | ------- | ---------- |
| `soundsEnabled`                  | `true`  |            |
| `soundVolume`                    | `1.0`   | 0.0 to 2.0 |
| `fovKickEnabled`                 | `true`  |            |
| `fovKickStrength`                | `0.1`   | 0.0 to 0.5 |
| `clientParticlesOnVanillaServers`| `true`  |            |
| `remotePlayerParticles`          | `true`  |            |

`Slipstream` gains `saveConfig()` so the screen can
persist edits and apply them live. Existing fields, file location, and the
Paper `config.yml` are untouched.

### Screen

Cloth Config `26.2.155+fabric` and Mod Menu `20.0.2`, both `modCompileOnly`
plus `modLocalRuntime`, listed under `suggests` in `fabric.mod.json`. Maven
repositories `maven.shedaniel.me` and `maven.terraformersmc.com` are added to
`build.gradle`.

`SlipstreamModMenu implements ModMenuApi` is registered under the `modmenu`
entrypoint. Mod Menu only loads that class when it is present, and the class
itself checks `FabricLoader.isModLoaded("cloth-config")` before building a
screen, returning no screen factory otherwise. The mod runs normally with
neither installed.

Categories:

- Physics: effect height, acceleration, max speed, lift strength, speed
  threshold, water spray height. Each entry carries a tooltip stating that a
  connected server's values take priority while connected.
- Visuals: particles enabled, particles on vanilla servers, remote player
  particles, FOV kick enabled, FOV kick strength.
- Audio: sounds enabled, sound volume.

Saving mutates the live config instance and writes `slipstream.json` through
`saveConfig()`, so no reload step is needed.

`assets/slipstream/lang/en_us.json` supplies every title, entry label, and
tooltip.

## Data flow summary

```
travel() tick (client, local player)
  sample -> boost/lift (unchanged) -> LocalGroundEffectState

travel() tick (server, any ServerPlayer)
  sample -> GroundEffectParticles.emit(ServerParticleSink)   unchanged output

client END_CLIENT_TICK
  wind sound start/stop            <- LocalGroundEffectState
  FOV kick smoothing               <- LocalGroundEffectState
  for each gliding player in 64 blocks:
    sample
    wake / skim sounds             always
    GroundEffectParticles.emit(LocalParticleSink)    only when server lacks mod
```

## Error handling

- `slipstream$sample` returns `null` for every early exit; callers never see a
  partial sample.
- The wind sound instance stops itself; the tick handler checks
  `SoundManager.isActive` before starting a new one so there is never more
  than one.
- Cloth Config and Mod Menu absence is handled by the mod loaded check. A
  missing lang key falls back to the raw key, which is cosmetic.
- Config clamping covers non finite and out of range values as today.

## Testing

Unit tests, `src/test`:

- `GroundEffectParticlesTest` with a recording `ParticleSink`: water branch
  emits contact bursts, spray, wake, and mist and no dust; ground branch emits
  dust and puffs and no water particles; per type caps hold at high speed;
  nothing is emitted on ticks that miss both modulus gates; nothing is emitted
  when particles are disabled.
- `FeelMathTest` for the pure curves added to `GroundEffectMath`: speed ratio
  clamps, wind volume and pitch at the corners, FOV target at zero proximity is
  zero, smoothing converges.
- `SlipstreamConfigTest` extended for the six new fields' defaults and clamps.
- `LocalParticleSink` burst expansion is tested against a seeded
  `RandomSource` for count and bounded spread.

Manual checklist before release:

- Singleplayer: unchanged particles, wind rises with proximity, wake over
  water, skim over gravel, FOV widens, screen opens from Mod Menu and edits
  persist.
- Vanilla server: particles and sound for self and a second gliding player,
  no boost, no FOV kick.
- Fabric server with the mod: server particles only, no doubling, boost and
  FOV kick active.
- LAN world: guest receives boost, no doubled particles.
- Cloth Config and Mod Menu removed: mod loads, no errors.
