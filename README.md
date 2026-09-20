# Slipstream

**Elytra ground effect for Fabric go faster the closer you skim.**

Inspired by real world ground effect aerodynamics (and a little bit of HTTYD).
Fly within 20 blocks of any surface and your elytra starts behaving like it
should: building speed, holding altitude, and kicking up whatever's below you.

---

## What it does

- **Speed boost** proximity scaled acceleration the closer you are to the
  ground. Quadratic falloff, so it builds fast in the last few blocks. Hard cap
  so it doesn't get out of hand.
- **Lift** gentle upward force that counters gravity when skimming level. Fades
  out the moment you pitch up or dive, so you stay in control.
- **Block accurate dust** flies over gravel? Gravel particles. Sand? Sand. Snow?
  Snowflakes. Sampled directly from whatever block is underfoot.
- **Water spray** two arcing columns off your wingtips, a V-wake trailing
  behind, fine mist ahead. Scales with speed and proximity.
- **Wingtip vortices** custom semitransparent particles that spin and fade over
  ~a second. Purely visual but they feel right.
- **Sound** a wind layer that swells and rises in pitch as you skim, a wet wake
  over water, and the actual step sound of whatever block you're skimming.
  All vanilla sound events, so resource packs remap them for free.
- **FOV kick** a subtle widening as ground effect speed builds. Respects the
  vanilla FOV Effects accessibility slider.
- **Config screen** via Mod Menu and Cloth Config (both optional).
- **Drafting** fly into another glider's wake and you gain speed and get drawn
  gently onto their line, enough to hold formation without ever losing your own
  steering. Look away and the pull releases instantly. Wakes fade over a few
  seconds, so you can still catch a line someone flew a moment ago.

---

## Server requirement

The speed boost only activates when the server has it installed too. This is
intentional. Elytra movement is client side in Minecraft, so without a server
check the boost would look identical to a speed hack to any anticheat.

| Where you're playing            | Boost | Particles |
| ------------------------------- | ----- | --------- |
| Singleplayer                    | ✓     | ✓         |
| Fabric server (mod installed)   | ✓     | ✓         |
| Paper server (plugin installed) | ✓     | ✓         |
| Server without either           |       | ✓ (local) |

If you join a server that doesn't have the mod or plugin, you still get the
particles and sounds (rendered locally, for you and any other gliders you can
see), but no boost, no lift, and no FOV kick. Nothing that could look like a
speed hack ever runs without the server's say so.

---

## Installation

**Singleplayer or Fabric server** drop `slipstream-<version>.jar` into `mods/`.
Required on the server too if you want the boost enabled for players.

**Paper/Spigot server** install `slipstream-paper-<version>.jar` in `plugins/`.
Handles server side particles and pushes config to any players running the
client mod. Players without the mod still see particles, they just won't get the
boost.

**Dependencies:** [Fabric API](https://modrinth.com/mod/fabric-api).

---

## Config

All values are tunable. On Fabric, the config lives at
`.minecraft/config/slipstream.json`. On Paper, it's
`plugins/slipstream/config.yml`. Run `/slipstream reload` to push updated values
to all online players live.

| Option             | Default | Notes                                                     |
| ------------------ | ------- | --------------------------------------------------------- |
| Effect height      | `20.0`  | Blocks above surface where the effect starts              |
| Acceleration       | `0.005` | Speed gained per tick at maximum proximity                |
| Max speed          | `1.5`   | Hard ceiling in blocks/tick (vanilla firework peaks ~1.5) |
| Water spray height | `5.0`   | How close to water before spray kicks in                  |
| Lift strength      | `0.6`   | Upward force when skimming level                          |
| Particles enabled  | `true`  | Set to `false` to disable all ground effect particles     |
| Sounds enabled     | `true`  | Wind, wake, and skim sounds (client only)                 |
| Sound volume       | `1.0`   | 0 to 2, multiplies all Slipstream sounds (client only)   |
| FOV kick enabled   | `true`  | Widen FOV with ground effect speed (client only)          |
| FOV kick strength  | `0.1`   | 0 to 0.5, fraction of FOV added at max effect (client)    |
| Particles on vanilla servers | `true` | Render particles locally when the server lacks the mod |
| Remote player particles | `true` | Particles and sounds for other gliders you can see  |
| Drafting           | `true`  | Ride other players' wakes (server pushed)                 |
| Draft acceleration | `0.008` | Speed gained per tick at the centre of a fresh wake        |
| Draft speed mult.  | `1.15`  | Ceiling while drafting, as a multiple of max speed         |
| Pull strength      | `0.25`  | Fraction of your offset from the wake corrected per tick   |
| Pull release angle | `35.0`  | Look this far from the wake and the pull lets go           |
| Wake particles     | `true`  | Draw other players' wakes (client only)                    |

The Paper plugin also has `override-clients` (default `true`) to push server
values to connecting clients, and `disabled-worlds` to opt specific worlds out
entirely.

Client only options are never overridden by a server. With
[Mod Menu](https://modrinth.com/mod/modmenu) and
[Cloth Config](https://modrinth.com/mod/cloth-config) installed, every option
is editable in game under Mods > Slipstream.

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

- Minecraft 26.1 through 26.3
- Fabric Loader ≥ 0.19.5 · Fabric API ≥ 0.145.4
- Java ≥ 25
- Paper API 26.3 (plugin only)

---

MIT License
