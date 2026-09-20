# Version Handshake and Paper Drafting Settings Design

Date: 2026-09-20
Status: approved, awaiting implementation plan

## Goal

Let the server decide which Slipstream versions may use the mod on it. A client
whose protocol does not match the server's either has its effects disabled or is
refused entry, by the server's choice. A client with no mod at all is unaffected
and keeps joining freely.

Not in scope: changing any physics, changing the config payload's existing wire
format, or any client-side UI beyond the message a disabled player receives.

## Current state

The server pushes `ServerConfigPayload` to each joining client on the channel
`slipstream:server_config`. That payload does double duty: it carries tunable
values, and its arrival is the permission signal that lets the client apply the
speed boost, lift and drafting. Without it the client applies no forces at all,
because elytra movement is client authoritative and unconfirmed speed reads as a
speed hack to anticheat.

Communication is one way. The client never tells the server anything, so the
server cannot know which version a client runs. It can, however, already see
whether a client has the mod: Fabric clients announce the custom channels they
can receive, and the Paper plugin already reads this through
`player.getListeningPluginChannels().contains(CHANNEL)`.

`Slipstream.onInitialize` currently sends the payload unconditionally on join to
every player except the singleplayer owner.

## Architecture

### Protocol version, not mod version

A single integer, `SlipstreamProtocol.VERSION`, starting at `1`. It increments
only when the wire format of either payload changes, never for a bugfix or a
feature that does not touch networking. Two releases sharing a protocol number
interoperate.

The mod version string travels alongside it, but only so logs and player-facing
messages can name something a human recognises. It is never compared.

The Paper plugin duplicates the constant, because it is a separate module with
no dependency on the mod jar. Both declarations carry a comment naming the
other, so a change to one is an obvious prompt to change the other.

### The handshake

A new serverbound payload on channel `slipstream:hello`:

```
int    protocolVersion
String modVersion        // display only, max 64 chars
```

The Fabric client sends it once, on `ClientPlayConnectionEvents.JOIN`. Nothing
else ever sends it, and the server never replies on that channel.

`String` uses the standard length-prefixed UTF encoding both Fabric and Paper
can read, so the plugin stays free of any Fabric dependency. The server rejects
a `modVersion` longer than 64 characters rather than trusting client length.

### Server classification

Every joining player is in exactly one of four states.

| State | How it is reached | Result |
| --- | --- | --- |
| Vanilla | Never announced our channel | Nothing sent, nothing enforced, joins normally |
| Compatible | Sent a hello whose protocol equals the server's | Config payload sent, effects work |
| Mismatched | Sent a hello whose protocol differs | Policy applies |
| Legacy | Announced our channel but sent no hello before the deadline | Policy applies |

The legacy state is the only way a release that predates this feature can be
recognised, since those versions have no hello to send. They are deliberately
treated as mismatched rather than trusted.

### The config payload becomes a reply

`Slipstream.onInitialize` stops sending the payload on join. Instead the server
sends it when a valid hello arrives. This is what makes disabling free: a client
that never completes the handshake simply never receives permission, with no
timer and no special case.

The singleplayer owner keeps its existing exemption and is never classified.

### The deadline

A player still in neither the vanilla nor compatible state after
`handshakeTimeoutTicks` (default `60`, three seconds) is classified as legacy
and the policy applies. The check runs on the server tick, over a small map of
pending players populated at join and emptied when a player is classified or
disconnects.

Three seconds is comfortably longer than a hello needs, since the client sends
it on its own join event, and short enough that a kicked player is not left
wondering.

### Policies

Server config picks one. It applies identically to mismatched and legacy.

- `off`: send the payload anyway. Restores today's behaviour exactly, and is the
  escape hatch for an operator who does not want any gating.
- `disable` (default): do not send the payload, and send the player one chat
  message explaining why. They keep playing; Slipstream does nothing for them.
- `kick`: disconnect with a message naming the required protocol.

`disable` is the default because Slipstream is a vanilla-friendly mod and
refusing entry over an optional cosmetic-plus-movement mod is a heavier
consequence than most servers want.

### Paper parity

The plugin gains the same behaviour with Bukkit equivalents:

- `registerIncomingPluginChannel(this, "slipstream:hello", listener)` to receive
  the handshake
- `getListeningPluginChannels()`, already used, to detect the mod
- A scheduled task for the deadline, matching the plugin's existing task style
- `player.kickPlayer(message)` for the kick policy

The plugin already sends the config payload on join and on dimension change.
Those sends become conditional on the player being classified compatible, and
the plugin tracks classification per player for the session.

## Config

New server-side fields. The Fabric server reads them from `slipstream.json`; the
client ignores them. Paper reads the equivalents from `config.yml`.

| Field | Default | Notes |
| --- | --- | --- |
| `versionEnforcement` | `"disable"` | One of `off`, `disable`, `kick` |
| `handshakeTimeoutTicks` | `60` | Deadline for a hello before classifying as legacy |

`validatePostLoad` clamps the timeout to between 20 and 600 ticks and falls back
to `disable` for an unrecognised enforcement string, logging the bad value.

Neither field is carried by `ServerConfigPayload`. They govern the server's own
behaviour and mean nothing on a client.

## Messages

Both are plain text with no formatting, and name the protocol numbers so a
player can act on them.

Disable:

```
Slipstream effects are disabled here. This server runs protocol 2 and your
version speaks protocol 1. Update Slipstream to use it on this server.
```

Legacy variant, where the client's protocol is unknown:

```
Slipstream effects are disabled here. Your version is too old to report its
protocol. Update Slipstream to use it on this server.
```

Kick uses the same two sentences, with the second replaced by "Update Slipstream
to join this server."

## Error handling

- A hello that fails to decode, or whose `modVersion` exceeds 64 characters, is
  treated as mismatched rather than throwing. A malformed handshake is not a
  reason to drop a connection with a stack trace.
- A second hello from the same player in one session is ignored. Classification
  happens once.
- A player disconnecting before the deadline is removed from the pending map.
- An unrecognised `versionEnforcement` value falls back to `disable` and logs
  once at startup.
- The Paper plugin's byte reading is bounds checked; a short or oversized array
  is treated as mismatched.

## Testing

Unit tests, no Minecraft runtime beyond the existing bootstrap:

- `HelloPayloadTest`: round trip preserves protocol and version string; a string
  over 64 characters is rejected; a truncated buffer is rejected rather than
  throwing.
- `VersionPolicyTest`, over a pure function `decide(state, policy)` extracted so
  it is testable without a server: every combination of the four states and
  three policies yields the right action, and vanilla is never acted on under
  any policy.
- `SlipstreamConfigTest`: defaults and clamps for the two new fields, including
  the fallback for an unrecognised enforcement string.

Manual checklist, requiring a working client and server:

- Matching client and server: effects work, nothing is logged.
- Server protocol bumped, client not updated: under `disable` the player joins,
  gets the message, and has no boost; under `kick` they are refused with the
  message; under `off` everything works as before.
- Vanilla client: joins freely under every policy, receives no message.
- A 1.0.3 client against a server with this feature: classified legacy after
  three seconds and handled by the policy.
- Singleplayer and LAN host: the owner is never classified and keeps working.

## Paper plugin: drafting settings

The drafting project deliberately left the Paper plugin untouched, so a Paper
server still sends only the original six doubles and every client falls back to
its own local drafting values. For a feature that changes movement speed that is
the wrong side of the line: a server able to cap ground effect speed should be
able to cap drafting speed too. This spec closes that gap in the same pass,
since both changes touch the plugin's join path and its payload writer.

The plugin gains eleven config keys mirroring the mod's own fields, and appends
them to the payload in exactly this order, which is the order the client's
decoder already expects:

| Order | Key | Type | Default |
| --- | --- | --- | --- |
| 1 | `drafting-enabled` | boolean | `true` |
| 2 | `draft-acceleration` | double | `0.008` |
| 3 | `draft-speed-multiplier` | double | `1.15` |
| 4 | `draft-pull-strength` | double | `0.25` |
| 5 | `draft-release-angle` | double | `35.0` |
| 6 | `wake-base-radius` | double | `1.5` |
| 7 | `wake-spread-rate` | double | `1.2` |
| 8 | `wake-lifetime-ticks` | int | `60` |
| 9 | `wake-sample-interval-ticks` | int | `2` |
| 10 | `draft-leader-bonus-per-drafter` | double | `0.15` |
| 11 | `draft-leader-bonus-max-drafters` | int | `3` |

Types matter: entries 8, 9 and 11 are written as ints and the rest as doubles,
with entry 1 as a single boolean byte. Writing a double where the client reads
an int silently corrupts every field after it, so the plugin's writer and the
mod's `ServerConfigPayload.encode` must stay byte for byte aligned. A round trip
test on the mod side already pins the decoder; the plugin gains its own test
asserting the exact byte length of a full payload.

The existing six values keep their positions and their meanings. The drafting
block is appended, so a client that predates drafting simply stops reading after
the sixth double, exactly as the short-payload rule allows in reverse.

`config.yml` gains a commented block for these keys in the style of the existing
file, and the plugin writes defaults for any key missing from an upgraded
server's config rather than failing to start.

## Risks

**Exact matching forces simultaneous updates.** A server operator bumping the
protocol locks out every player until they update. This is the correct tradeoff
while the payload carries physics values, but it is a real operational cost. It
is mitigated by the protocol number being deliberately slow to change, and by
`off` existing for operators who would rather not gate at all.

**The constant is duplicated across modules.** The Paper plugin cannot depend on
the mod jar, so the protocol number lives in two files. The comments pointing at
each other are the only guard, and a mismatch would present as every client
being classified mismatched. A release checklist item is warranted.

**Legacy classification depends on channel announcement.** If a future Fabric
version changes how clients announce receivable channels, legacy clients would
fall into the vanilla bucket and simply keep working without effects, which
fails safe rather than dangerous.
