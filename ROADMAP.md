# Slipstream Roadmap

Working state and remaining work for the `feature/drafting` branch. Written so
someone picking this up cold does not have to reconstruct the reasoning.

## Where things stand

Branch `feature/drafting`, well ahead of `main` and everything pushed. For the
exact count run `git rev-list --count origin/main..HEAD` rather than trusting a
number written here, which goes stale on the next commit.
`./gradlew build` is green and 255 tests pass across both modules.

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

## The work, in order

Items 4 to 16 are all addressed. Numbering is kept as it was so the cross
references between items still mean something.

Three carry a caveat in their own section, and they are the three worth reading
before anything else: item 7 changes how Paper applies physics and has not been
flown, item 11 closes two thirds of the mixin risk and names the third, and item
16 ships sprites nobody has looked at.

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

### 7. Make the Paper path server authoritative (done, unflown)

`GroundEffectTask` already ran per tick with proximity, distance and speed in
hand; it just never applied force. It now calls `setVelocity` with the same two
curves the Fabric client uses, mirrored into the plugin's `GroundEffectMath` and
pinned by tests on both sides. `server-authoritative` defaults to true.

**The double-apply problem, solved without a protocol bump.** A modded client
would otherwise apply the ground effect *and* receive the server's. The existing
wire format already carries acceleration and lift strength, so under server
authority the plugin sends both as zero: `liftForce` returns zero immediately at
strength 0, including its antigravity term, and `boostDelta` multiplies by
acceleration. Everything else is sent unchanged, so a modded client keeps
drafting, camera assist and particles. No protocol 3, no lockstep release.

Drafting stays client applied either way. Moving it server side would need wake
trails on the server, which were deliberately dropped.

**Not flown.** The trade is a round trip: the client no longer predicts the
boost, so at real latency it may feel less immediate than the client path, and
whether it rubber bands is exactly the kind of thing that cannot be settled from
a test. The config key exists so an operator can go back.

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

### 11. Harden the mixin (two thirds done)

**Silent failure now speaks.** The injection leaves a heartbeat and the client
tick notices when it stops, logging one line naming the likely cause. This
repairs nothing. It turns an invisible incompatibility, where particles keep
appearing because they come from a different path while no force is applied at
all, into a bug report that can actually be answered. Detection needed no second
injection point, which would have added to the very surface being de-risked.

**The FOV mixin is `@ModifyReturnValue` now.** The comment explaining why
MixinExtras was avoided was stale; it has shipped inside Fabric Loader since
0.15. Beyond removing the stale note it composes properly: several mods
modifying one return value each see the previous result, where cancellable
injects race to be last and silently discard each other.

**CI builds the matrix**, verified locally against 26.2 as well as 26.3. It
covers only those two, because they are the two whose dependency sets are known
good; `supported_minecraft_versions` also claims 26.1.x, and guessing their
Fabric API and Cloth versions would ship a permanently red job.

**Still open: the runtime half.** A matrix build catches API drift and a mixin
target that no longer resolves at compile time. It cannot catch a mixin that
compiles and then fails to apply, because mixins apply at runtime. That needs a
headless launch or a gametest per version, and it is the part of this item that
actually guards against the launch crash.

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

### 13. Tint each wake by whose it is (done)

`WakeHue` derives a stable pale colour per player id. The wake you are *riding*
keeps its own bright look, because telling "the one I am in" from "everyone
else's" matters more than whose it is.

**No new particle type was needed.** A wake particle is painted at a stationary
point, so its three velocity arguments were always zero and now carry the tint
instead, flagged per style by `hueFromSpawnArgs`. That avoided a custom
`ParticleOptions` with its codec, stream codec and registry entry.

**What it cannot promise.** Player ids are effectively random, so no per player
hash can guarantee two given players differ. Guaranteeing it would mean handing
out hues from the current player list, and then your wake would change colour
whenever somebody logged in, which is worse than an occasional clash. The tests
assert what is actually true instead: hues use the whole circle, and near collisions
stay rare.

A first attempt multiplied the id by the golden ratio conjugate and kept the
fraction. That trick spreads *sequential* inputs, which ids are not, and a float
has 24 mantissa bits, so the fractional part quantised and most players came out
the same colour. The test caught it; the fix is a 64 bit avalanche mix and the
top 24 bits.

### 14. Firework slingshot (done)

`WakeSample` carries a boost stamp, `DraftQuery` reports it, and `boostDelta`
spends it. A wake left under a rocket accelerates a follower 2.5 times as hard
and ages out at 40% of the normal lifetime, so the slingshot is a window you
have to be in position for rather than a fast lane lying around.

Two deliberate limits. The boost is spent on forward acceleration and never on
the pull, because being yanked sideways harder is not a reward. And the draft
ceiling still holds: a slingshot gets you to overtake speed much faster, it does
not take you past a limit the server set, or `draftSpeedMultiplier` would mean
nothing.

Detection is proximity, not an accessor mixin. A rocket boosting someone rides
along with them, so anything within two blocks of a glider is theirs. Reaching
into `FireworkRocketEntity.attachedToEntity` would have meant another mixin
against a private vanilla field, which item 11 is about having less of.

### 15. Drafting saves elytra durability (done, Paper only)

Paper, through `PlayerItemDamageEvent`. Vanilla takes one durability point every
twenty ticks from inside `updateFallFlying`, so a proportion of those hits is
skipped rather than a fraction of a point shaved, which averages to the same
thing over a flight. Capped at 75%, so an elytra never becomes immortal.

**The strength is the server's own estimate, never the client's.** `DraftEstimate`
asks whether another glider is close ahead and flying the same way. It is an
approximation, because the server has no wake trails, and that is the right call
rather than a compromise: the moment a client-reported draft strength buys a
resource saving, a client simply always claims a perfect draft and never wears
out an elytra again.

**Fabric server not covered.** The Bukkit event has no Fabric equivalent, and the
alternative is redirecting the vanilla `hurtAndBreak` call inside
`updateFallFlying`, which is precisely the fragile injection item 11 exists to
reduce. Worth doing after item 11, not before it.

### 16. Custom particle textures (done, unseen)

`tools/generate_particles.py` writes sixteen 16x16 sprites into
`assets/slipstream/textures/particle/`: an expanding vortex ring over eight
frames, a streak over four, a haze over four. Standard library only, no Pillow,
and deterministic, so rerunning it leaves the tree unchanged.

All three are white RGB with the shape entirely in the alpha channel, which is
what keeps per-role tinting working and what made item 13 possible at all: a
sprite with colour baked in could only ever be darkened.

Wired up as `wing_vortex` to the ring, `wake_trail` to the haze, `draft_active`
to the streak, still as sprite sequences so `setSpriteFromAge` keeps working.

**Nobody has seen these.** The PNGs are verified to be valid RGBA and the ring
is verified ring shaped by reading its alpha back, but whether they *look* right
in a world is unknown. They are the most likely thing on this branch to need a
second pass, and the generator is a few constants to adjust rather than a
redraw.

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
26.2, and nothing added since has been flown at all. The suite is 255 tests and
every force curve, budget, policy and format in it is covered, but a test cannot
tell you whether a slipstream feels like a slipstream.

What has **not** been tested:

- Drafting with two real players, which is the entire point of the feature.
  `DraftingTurnTest` now simulates a leader and a follower through a ninety
  degree turn against the real tracker and geometry, which is a regression guard
  and not a substitute: it cannot tell you whether the pull *feels* right, and
  it models neither latency nor interpolation. Two clients still have to fly it.
- Anything on a dedicated server, Fabric or Paper. This now matters more than it
  did: under item 7 the Paper plugin moves players itself with `setVelocity`,
  which is a different feel and a different anticheat story from the client
  applying it, and `server-authoritative` defaults to true.
- The debug overlay and the new particle sprites have never been rendered. Both
  compile and their data is verified, which is not the same thing.
- The durability saving, which needs two players on a Paper server to observe.
- The version handshake end to end against a genuinely mismatched client.
- Behaviour with more than a couple of gliders, which is what item 8 addresses.
