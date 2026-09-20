# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Wind rush sound layer that scales with ground effect proximity and speed and
  pitches up toward max speed
- Positional water wake and block step skim sounds, for yourself and other
  gliders in range
- FOV kick that widens the view as ground effect speed builds; honours the
  vanilla FOV Effects slider
- Particles now render locally on servers without the mod or plugin, for every
  gliding player in range
- In game config screen via Mod Menu and Cloth Config (both optional)
- Client only config: `soundsEnabled`, `soundVolume`, `fovKickEnabled`,
  `fovKickStrength`, `clientParticlesOnVanillaServers`,
  `remotePlayerParticles`

### Fixed

- LAN guests now receive the server config, so they get the boost and do not
  see doubled particles

### Changed

- Particle geometry moved out of the mixin into `GroundEffectParticles` behind
  a `ParticleSink`, with unit tests

## [1.0.3] - 2026-09-19

### Changed

- Updated for Minecraft 26.3 (Fabric Loader 0.19.5, Fabric API 0.161.0, Paper API 26.3)
- Mod now declares support for Minecraft 26.1 through 26.3 instead of a single version

## [1.0.2] - 2026-06-26

### Changed

- Updated for Minecraft 26.2 (Fabric Loader 0.19.3, Fabric API 0.153.0)

## [1.0.0] - 2026-06-06

### Added

- Proximity scaled horizontal speed boost when gliding near surfaces; quadratic
  falloff so it builds fast in the last few blocks, with a hard speed cap
- Lift force that counters gravity when skimming level; disengages automatically
  when look pitch exceeds ±30° or speed drops below the effect threshold
- Boost gated to descending or level flight only; no climbing acceleration
- Effect fully suppressed when the player is submerged in water or lava
- 3-block buffer zone at the surface where proximity is treated as maximum
- Block accurate ground dust particles sampled from the actual block underfoot,
  plus close proximity `POOF` puffs
- Water spray effects when skimming over water: wingtip arcs, V-wake trail, and
  fine mist using `SPLASH` and `FALLING_WATER` particles
- Custom wingtip vortex particle (semitransparent quad, spins and fades over ~1
  second)
- Per entity raycast cache with O(1) heightmap precheck to keep tick overhead
  low
- Plain JSON config file (`slipstream.json` on Fabric, `config.yml` on Paper)
  with automatic defaults and validation
- Server side config push: Fabric servers and the Paper plugin both send config
  to connecting clients over a custom payload channel
- Client config automatically reverts to local defaults on disconnect
- Speed boost requires server confirmation to avoid anticheat false positives;
  no boost on servers without the mod or plugin installed
- Companion Paper plugin for server side particles and config distribution
- Paper plugin commands `/slipstream enable`, `/slipstream disable`, and
  `/slipstream reload` (require `slipstream.admin` permission)
- Paper config options `override-clients`, `effect-enabled`, and
  `disabled-worlds` for per world or server wide opt-out
- Full test coverage for `GroundEffectMath` and config edge cases, plus
  Checkstyle enforcement in CI
