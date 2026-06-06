# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.0.0] - 2026-06-03

### Added

- Quadratic proximity-scaled speed boost when gliding within `effectHeight`
  blocks of any surface
- Lift force that counters gravity when skimming level fades on ascent and steep
  dive
- `effectSpeedThreshold` setting to gate lift and wingtip vortex particles by
  minimum speed
- Block-accurate surface particles gravel, sand, snow, and more sampled from the
  block underfoot
- Water spray wingtip arcs, V-wake trail, and fine mist, scaling with speed and
  proximity
- Custom wingtip vortex particle (semi-transparent quad, spins and fades over ~1
  second)
- Proximity-scaled ambient sound elytra wind, high-speed water splash, block
  step audio
- Per-entity raycast cache with O(1) heightmap pre-check to keep tick overhead
  low
- Cloth Config integration with full in-game editing via ModMenu
- Companion Paper plugin for server-side particles on Paper/Spigot servers
- Server config push server overrides client settings on join, reverts
  automatically on disconnect
- Speed boost requires server confirmation (Fabric mod or Paper plugin installed
  server-side) to avoid anti-cheat false positives
- Fabric server support mod installed server-side pushes config to connecting
  clients automatically
- `/slipstream reload` command (Paper, requires `slipstream.admin`) to
  hot-reload config and push to all online players
- `disabled-worlds` config option (Paper) to opt specific worlds out of the
  effect
