# Slipstream

**Elytra ground effect for Fabric go faster the closer you skim.**

Inspired by real-world ground effect aerodynamics (and a little bit of HTTYD). Fly within 20 blocks of any surface and your elytra starts behaving like it should building speed, holding altitude, and kicking up whatever's below you.

---

## What it does

- **Speed boost** proximity-scaled acceleration the closer you are to the ground. Quadratic falloff, so it builds fast in the last few blocks. Hard cap so it doesn't get out of hand.
- **Lift** gentle upward force that counters gravity when skimming level. Fades out the moment you pitch up or dive, so you stay in control.
- **Block-accurate dust** flies over gravel? Gravel particles. Sand? Sand. Snow? Snowflakes. Sampled directly from whatever block is underfoot.
- **Water spray** two arcing columns off your wingtips, a V-wake trailing behind, fine mist ahead. Scales with speed and proximity.
- **Wingtip vortices** custom semi-transparent particles that spin and fade over ~a second. Purely visual but they feel right.
- **Ambient sound** wind and surface audio scales with how close you are. Water gets a high-speed splash, solid ground gets the block's own step sound.

---

## Server requirement

The speed boost only activates when the server has it installed too. This is intentional elytra movement is client-side in Minecraft, so without a server check the boost would look identical to a speed hack to any anti-cheat.

| Where you're playing            | Boost | Particles |
| ------------------------------- | ----- | --------- |
| Singleplayer                    | ✓     | ✓         |
| Fabric server (mod installed)   | ✓     | ✓         |
| Paper server (plugin installed) | ✓     | ✓         |
| Server without either           |       |           |

If you join a server that doesn't have the mod or plugin, nothing happens. No particles, no boost, no flags.

---

## Installation

**Singleplayer or Fabric server** drop `slipstream-<version>.jar` into `mods/`. Required on the server too if you want the boost enabled for players.

**Paper/Spigot server** install `slipstream-paper-<version>.jar` in `plugins/`. Handles server-side particles and pushes config to any players running the client mod. Players without the mod still see particles, they just won't get the boost.

**Dependencies:** [Fabric API](https://modrinth.com/mod/fabric-api), [Cloth Config](https://modrinth.com/mod/cloth-config). [ModMenu](https://modrinth.com/mod/modmenu) is optional but recommended for in-game config editing.

---

## Config

All values are tunable. On Fabric, the config lives at `.minecraft/config/slipstream.json` and everything is editable in-game through ModMenu. On Paper, it's `plugins/slipstream/config.yml` run `/slipstream reload` to push updated values to all online players live.

| Option             | Default | Notes                                                     |
| ------------------ | ------- | --------------------------------------------------------- |
| Effect height      | `20.0`  | Blocks above surface where the effect starts              |
| Acceleration       | `0.001` | Speed gained per tick at maximum proximity                |
| Max speed          | `3.0`   | Hard ceiling in blocks/tick (vanilla firework peaks ~1.5) |
| Water spray height | `5.0`   | How close to water before spray kicks in                  |
| Lift strength      | `0.015` | Upward force when skimming level                          |

The Paper plugin also has `override-clients` (default `true`) to push server values to connecting clients, and `disabled-worlds` to opt specific worlds out entirely.

---

## Building

```bash
git clone https://github.com/Si6gma/slipstream
cd slipstream
./gradlew build
```

- Fabric mod → `build/libs/slipstream-<version>.jar`
- Paper plugin → `paper-plugin/build/libs/slipstream-paper-<version>.jar`

---

## Compatibility

- Minecraft 26.1.2
- Fabric Loader ≥ 0.19.2 · Fabric API ≥ 0.150.0 · Cloth Config ≥ 26.1.154
- Java ≥ 25
- Paper API 26.1.2 (plugin only)

---

MIT License
