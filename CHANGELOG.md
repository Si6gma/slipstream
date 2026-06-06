# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.0.0] - 2026-06-06

### Added

- Proximity-scaled horizontal speed boost when gliding near surfaces—quadratic
  falloff so it builds fast in the last few blocks, with a hard speed cap
- Lift force that counters gravity when skimming level; disengages automatically
  when look pitch exceeds ±30° or speed drops below the effect threshold
- Boost gated to descending or level flight only—no climbing acceleration
- Effect fully suppressed when the player is submerged in water or lava
- 3-block buffer zone at the surface where proximity is treated as maximum
- Block-accurate ground dust particles sampled from the actual block underfoot,
  plus close-proximity `POOF` puffs
- Water spray effects when skimming over water—wingtip arcs, V-wake trail, and
  fine mist using `SPLASH` and `FALLING_WATER` particles
- Custom wingtip vortex particle (semi-transparent quad, spins and fades over ~1
  second)
- Per-entity raycast cache with O(1) heightmap pre-check to keep tick overhead
  low
- Plain JSON config file (`slipstream.json` on Fabric, `config.yml` on Paper)
  with automatic defaults and validation
- Server-side config push—Fabric servers and the Paper plugin both send config
  to connecting clients over a custom payload channel
- Client config automatically reverts to local defaults on disconnect
- Speed boost requires server confirmation to avoid anti-cheat false positives;
  no boost on servers without the mod or plugin installed
- Companion Paper plugin for server-side particles and config distribution
- Paper plugin commands `/slipstream enable`, `/slipstream disable`, and
  `/slipstream reload` (require `slipstream.admin` permission)
- Paper config options `override-clients`, `effect-enabled`, and
  `disabled-worlds` for per-world or server-wide opt-out
- Full test coverage for `GroundEffectMath` and config edge cases, plus
  Checkstyle enforcement in CI
