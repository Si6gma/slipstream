# Slipstream Roadmap

Working state and remaining work for the `feature/drafting` branch. Written so
someone picking this up cold does not have to reconstruct the reasoning.

## Where things stand

Branch `feature/drafting`, 40 commits ahead of `main`, everything pushed.
`./gradlew build` is green and 216 tests pass across both modules.

Shipped on this branch:

- **Feel pass**: wind, water wake and block-accurate skim sounds, all from
  vanilla sound events; an FOV kick tied to ground effect speed; particles
  rendered client side on servers without the mod; a Cloth Config screen.
- **Drafting**: gliders leave a fading wake trail, and flying in one gives a
  forward boost plus a damped pull onto the wake centre line. Chains work, the
  leader gains a small bonus, and per-role particles distinguish your own
  vortices from a wake you are near and a wake you are riding.
- **Camera assist**: while drafting, the view eases toward the wake ahead so a
  turning leader can be followed. Strength is server governed; a client may
  switch it off but never raise it.
- **Version handshake**: clients report a protocol number on join, and a server
  may ignore a mismatch, withhold effects with a message, or refuse entry.
  Implemented on both Fabric and Paper. Vanilla players are never affected.

Reviewed three times: a three-lens adversarial pass on the drafting feature
(twelve findings, all fixed), and a senior mod developer critique of the whole
branch, which is the source of most of what follows.

## Decisions already made

Do not relitigate these without a reason.

- **The pull stays.** The critique argued for deleting it and keeping only the
  boost. Rejected: the homing feel is wanted.
- **Camera assist is reworked, not removed.**
- **The Paper path becomes server authoritative.** Agreed as the highest value
  structural change.
- **Physics values are server pushed; presentation stays local.** Sounds,
  volume, particles and the FOV kick are the player's own choice, partly for
  accessibility. Camera assist is the exception because it steers you, so the
  server sets its strength while the client keeps an opt out.
- **Protocol version, not mod version.** `SlipstreamProtocol.VERSION` is 2 and
  increments only when a wire format changes. It is duplicated in the Paper
  module because the plugin cannot depend on the mod jar; both copies carry a
  comment pointing at the other.
- **Short payloads stay supported.** A 1.0.x plugin sends only the original six
  doubles and the client keeps its own drafting defaults.
- **A wake sample stores no heading.** The drafting spec in
  `docs/superpowers/specs/` still shows `WakeSample` carrying one, because it
  records the design as approved rather than as built. Item 4 replaced it: the
  heading is derived from the sample positions at query time.

## Remaining work, in order

Each item is independent unless noted. The ordering is by what reaches users.

### 4. Derive wake headings from leader position history (done)

Remote wake headings came from `getDeltaMovement()` on `RemotePlayer`, which is
a multi-tick lerp toward an already-stale broadcast value, so in a hard turn the
heading still pointed down the leg the trail had already left, the
look-divergence check fired, and the pull released during exactly the turns the
mechanic exists for.

`WakeSample` no longer carries a heading at all. `DraftingMath.nearest` derives
it from the two sample positions it already uses to form the segment, so the
centre line the pull aims at and the bearing it releases against cannot
disagree. A purely vertical segment has no bearing and is skipped.

This was a prerequisite for item 5: the camera assist aims at
`point + wakeHeading * 8`, so a lagging heading aimed it off the path during
precisely the turns it was added to help with.

### 5. Rework the camera assist so it never fights the player (done)

`assistCamera` tracked `assistedYaw` only and wrote `setXRot` unconditionally,
so pitch had no yield and a player looking down was fought every tick. The
`PLAYER_STEER_DEGREES` check was also a switch rather than a handover: it stood
the assist down for one tick, then applied it at full strength on the next, so
holding a turn produced a nudge every other tick.

`CameraAssistMath` in `src/main` now owns the rule, as pure scalars so it can be
tested without a client. The assist holds an *authority* in [0, 1]. Movement on
either axis beyond the nudge it last applied counts as steering, combined as a
diagonal so a flick is not double counted, and it buys authority back in
proportion: nothing below a quarter degree, all of it at four degrees, a linear
ramp between. Authority is surrendered on the tick the hand moves and returned
at 0.04 per tick, so a release takes about a second.

Two judgement calls, neither settleable without flying:

- **One authority for both axes**, so deliberately looking down also releases
  the turn help. That is the conservative reading of never fighting the player.
  Splitting them per axis is the first thing to try if the assist feels like it
  gives up too easily.
- **The constants** (`0.25`, `4.0`, `0.04` degrees and fraction per tick) are
  reasoned guesses, not measured ones.

### 6. Add a `/slipstream debug` overlay (done, with one gap)

`/slipstream debug` toggles a HUD overlay. It is a *client* command, so it works
on a vanilla server, which is where "it doesn't work" gets reported from.

`DebugReport` in `src/main` owns the formatting as pure strings and is tested,
including that numbers use a dot on a German client. The overlay reads draft
strength from `LocalDraftState`, recorded by the same `DraftScan` the flight
code uses, so the screen cannot disagree with the forces the player felt.

**The gap.** Traffic after the hello is one way, so the client is never told the
server's protocol number, whether it is Fabric or Paper, or which policy it
applied. The overlay prints `server not reported` rather than guessing. It does
still separate vanilla from withheld, by asking whether the server declared our
channel rather than whether a config arrived, which is the distinction that
actually changes the support answer. Closing the rest needs a payload the server
sends back and a protocol bump to 3, which was not done unprompted.

**Not verified by rendering.** This builds, and the HUD and command APIs
resolve, but no one has seen it draw. One client launch settles it.

### 7. Make the Paper path server authoritative

Have the plugin apply the ground effect itself with `setVelocity` on the server
tick it already runs, so the boost fires `PlayerVelocityEvent`, which essentially
every anticheat respects. Keep the client path for Fabric servers and
singleplayer where the round trip would cost feel.

Two payoffs: the anticheat exemption problem largely inverts, and a Paper server
running Slipstream would work for players with **no mod installed at all**, which
is the biggest distribution unlock available.

### 8. Budget particles and sounds (done)

`EmissionBudget` holds the two curves, tested.

Client side, gliders in range are sorted nearest first. The closest three keep
full detail, the rest thin as a reciprocal of rank, which matters because a
cliff would make a wake pop in and out as two gliders traded places. Sounds are
a hard count of three a tick rather than a thin, since a sound cannot be played
fractionally and the failure mode is the mixer dropping *vanilla* sounds.

Server side there is no single viewer to rank by, so every glider is thinned
equally by `fairShare`, which holds total output at a fixed budget however many
turn up. A test asserts exactly that: `count * fairShare(count)` never exceeds
the budget, at any count up to two hundred.

Wakes are still *recorded* for every glider in range. Only the presentation
thins, so the budget can never change who you are able to draft.

### 9. Fix the update checker, add a compatibility page (done)

The checker now asks for project id `ESOV1nxn` and filters to the paper loader,
and the comparison is `VersionCompare.isNewer` instead of an inequality that
could never be satisfied. Suffixes and build metadata are stripped from both
sides, so `1.0.3-paper` against `1.0.3` is correctly silent, and a server ahead
of the release is not nagged to downgrade. Anything unparseable stays quiet,
because a boot warning that cries wolf is worse than one that misses a release.

`VersionCompare` is a separate Bukkit free class purely so it can be tested:
`paper-api` is `compileOnly`, so a class touching it cannot load in a unit test.

`slipstream.use` gates the config payload in `sendConfigForWorld`, the one place
a grant happens. Default `true`, so an existing server is unaffected; revoking
it sends the disabled config rather than nothing, so those clients switch the
effects off rather than sitting in an ambiguous state.

`docs/COMPATIBILITY.md` covers why an anticheat cares at all, the permission
node, and exemption recipes for Grim, NCP, Vulcan and Matrix, linked from the
README.

### 10. Cleanup batch (done)

- `WakeTrackers` is down to the one client tracker. `forLevel` was still called,
  from the mixin, but the drafting path runs only for the local player, so it
  had exactly one possible answer.
- The `SERVER_STOPPED` comment went with it.
- The hello is guarded with `canSend`, so a vanilla join no longer makes every
  proxy log an unknown channel.
- The Paper particle task read `max-speed` `3.0` and threshold `0.2` against the
  payload's `1.5` and `0.3`. Both now match the mod's own defaults.
- `drawWakes` skips your own trail. It sat directly behind the camera and read
  as exhaust smoke on a solo flight, the opposite of the cue intended.
- The README now says `/slipstream reload` is Paper only and names
  `/slipstream debug` as the Fabric command.
- `update.json` and its `updateJsonUrl` are gone, since Fabric Loader ignores
  both.

### 11. Harden the mixin

A single `@Inject(method = "travel", at = @At("TAIL"))` on `LivingEntity` with
`defaultRequire: 1` is the entire physics surface, while the jar claims 26.1
through 26.3. When Mojang next splits `travel`, users on a snapshot-tracking pack
get a hard crash at launch.

Add a headless client launch or gametest per supported version in CI. Detect and
log when another movement mod's HEAD-cancelling mixin makes the TAIL injection
never run, since particles keep appearing while forces silently stop. Switch the
FOV mixin to MixinExtras `@ModifyReturnValue`: it has shipped inside Fabric
Loader since 0.15, so the comment explaining why it was avoided is stale.

### 12. Trim the config screen (done)

The screen is down from twenty one entries to ten: three drafting toggles, five
visual, two audio. The whole physics category and every drafting number are file
only now.

The argument turned out to be stronger than "nobody tunes a release angle from a
GUI". Every value removed is server pushed, so on any server running Slipstream
editing the field did nothing at all, which reads as a broken setting rather than
an overridden one.

Camera assist keeps its toggle even though the server sets its strength, because
switching it off is an accessibility choice and someone it makes ill should not
have to find a JSON file to stop their view being moved.

The README table is split the same way: a ten row table of what the GUI shows,
then a prose list of what is file only and why.

### 13. Tint each wake by whose it is

Derive a hue from the player's identity so overlapping wakes in a pack are
distinguishable. Needs a particle type carrying colour data rather than a fixed
tint per type, so it pairs with item 16.

### 14. Firework slingshot

A leader popping a rocket leaves a brief, much stronger wake, giving followers a
real slingshot. Emergent, uses an existing vanilla verb, and rewards flying as a
group. Stamp wake samples recorded during a firework boost with a strength
multiplier and shorter lifetime, and let the geometry read it.

### 15. Drafting saves elytra durability

Reduce durability cost while drafting, scaled by strength. Gives the mechanic a
payoff on a survival server where nobody is racing. Server authoritative, so it
lands naturally alongside item 7.

### 16. Custom particle textures

Generate the mod's own atlas procedurally. The shapes needed are abstract and
radial, which procedural generation is good at, unlike character art.

- An expanding **vortex ring** that thins over its frames, reading as disturbed
  air rather than smoke. Vanilla has no sprite that does this, so it is the one
  place custom art buys something tinting cannot fake.
- A soft elongated **streak** for draft strength particles.
- A fine **haze** for old wakes.

Author greyscale with alpha so per-role tinting still applies and per-player wake
hues become possible. Ship as animated sprite sequences so `setSpriteFromAge`
keeps working.

## Build notes

- **Use JDK 25 for Gradle.** A fresh daemon may pick Java 27 and fail with
  "Unsupported class file major version 71":
  `export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home`
- The branch targets Minecraft 26.3. To build for 26.2, temporarily set
  `minecraft_version=26.2`, `loader_version=0.19.3`,
  `fabric_api_version=0.160.0+26.2`, `cloth_config_version=26.2.155`,
  `modmenu_version=20.0.2`, and the plugin's `paper-api` to
  `26.1.2.build.69-stable`. Revert all of it afterwards.
- `./gradlew :test` targets the root project; `./gradlew build` covers both.
- Commit style: imperative subject, no `Co-Authored-By` trailer, no em dashes
  anywhere in code, comments or commit messages. Checkstyle rejects unused
  imports and star imports.

## What is not verified

Everything on this branch has been flown briefly in singleplayer on Minecraft
26.2. What has **not** been tested:

- Drafting with two real players, which is the entire point of the feature.
  `DraftingTurnTest` now simulates a leader and a follower through a ninety
  degree turn against the real tracker and geometry, which is a regression guard
  and not a substitute: it cannot tell you whether the pull *feels* right, and
  it models neither latency nor interpolation. Two clients still have to fly it.
- Anything on a dedicated server, Fabric or Paper.
- The version handshake end to end against a genuinely mismatched client.
- Behaviour with more than a couple of gliders, which is what item 8 addresses.
