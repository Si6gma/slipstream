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
- **Camera assist** while drafting, your view eases toward the wake ahead of
  you, so following someone through a turn works without constant correction.
  Move your own view and it yields immediately.
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

A server running Slipstream also decides which versions may use it. Your client
reports the network protocol it speaks when you join, and a server can either
ignore a mismatch, let you play with the effects switched off, or refuse the
connection. The default is to switch the effects off rather than turn anyone
away. Players without the mod at all are never affected by this.

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
`plugins/slipstream/config.yml`.

On Paper, `/slipstream reload` pushes updated values to all online players live.
Fabric has no reload command: edit the config in game through Mod Menu, where
changes apply immediately, or restart. The only Fabric command is
`/slipstream debug`, a client side overlay for diagnosing a server that is not
granting effects.

### In game

With [Mod Menu](https://modrinth.com/mod/modmenu) and
[Cloth Config](https://modrinth.com/mod/cloth-config), these are editable under
Mods > Slipstream. They are the presentation choices and the two switches a
player actually flips, and no server can override any of them.

| Option                       | Default | Notes                                              |
| ---------------------------- | ------- | -------------------------------------------------- |
| Drafting                     | `true`  | Ride other players' wakes                          |
| Camera assist                | `true`  | Ease your view toward the wake. Always yours to refuse |
| Wake particles               | `true`  | Draw other players' wakes                          |
| Particles enabled            | `true`  | All ground effect particles                        |
| Particles on vanilla servers | `true`  | Render locally when the server lacks the mod       |
| Remote player particles      | `true`  | Effects for other gliders you can see              |
| FOV kick enabled             | `true`  | Widen FOV with ground effect speed                 |
| FOV kick strength            | `0.1`   | 0 to 0.5, fraction of FOV added at max effect      |
| Sounds enabled               | `true`  | Wind, wake and skim sounds                         |
| Sound volume                 | `1.0`   | 0 to 2, multiplies all Slipstream sounds           |

### File only

Everything else is tuning rather than preference, and lives only in the config
file. On a server that runs Slipstream these are pushed by the server anyway, so
a GUI field for them would do nothing and read as broken.

Physics: effect height, acceleration, max speed, lift strength, speed threshold,
water spray height.

Drafting: draft acceleration, draft speed multiplier, pull strength, pull release
angle, camera assist strength, wake base radius, wake spread rate, wake lifetime,
wake sample interval, leader bonus per drafter and its cap.

Server side: version enforcement (`off`, `disable` or `kick`) and handshake
timeout. The Paper plugin adds `override-clients` and `disabled-worlds`, and the
`slipstream.use` permission node described in
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

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

Running an anticheat, or wondering why the boost does nothing on your server?
See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for the `slipstream.use`
permission node and exemption recipes for Grim, NCP, Vulcan and Matrix.

- Minecraft 26.1 through 26.3
- Fabric Loader ≥ 0.19.5 · Fabric API ≥ 0.145.4
- Java ≥ 25
- Paper API 26.3 (plugin only)

---

MIT License
