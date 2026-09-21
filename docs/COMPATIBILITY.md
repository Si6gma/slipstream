# Compatibility

## Why an anticheat has anything to say about this

Elytra flight is client authoritative. The server accepts the position the
client reports, so Slipstream's speed boost, lift and drafting pull are all
applied by the client and then reported. To an anticheat that is
indistinguishable from a speed hack, because mechanically it is the same thing:
a client moving faster than vanilla physics allows.

This is why the client applies nothing until a server grants it permission. On a
vanilla server Slipstream is cosmetic only, and nothing below is needed.

## The permission node

The plugin sends the config payload only to players with `slipstream.use`. That
payload is what allows the client to move itself faster, so the node is the
switch for the whole effect.

```
slipstream.use    default: true
```

Default `true`, so a server that never touches permissions behaves as it always
has. If your anticheat exempts only some players, revoke it for everyone else:

```
# LuckPerms: allow only a group you have exempted
/lp group default permission set slipstream.use false
/lp group flyers permission set slipstream.use true
```

A player without it is sent the disabled config rather than nothing, so their
client switches the effects off instead of sitting in an ambiguous state.

## Exemption recipes

Exempt the same set of players you granted `slipstream.use`. Granting the node
without exempting them is what produces false flags; exempting them without the
node just means nothing happens.

Version numbers move, so treat these as the setting to look for rather than a
line to paste blindly.

### GrimAC

Grim checks prediction rather than a speed threshold, so a boost it does not
know about reads as an offset. Exempt with the bypass permission:

```
/lp group flyers permission set grim.exempt true
```

Grim's own documentation discourages blanket exemption. Prefer a narrow group.

### NoCheatPlus

NCP splits checks finely. The elytra path lives under `moving.survivalfly`:

```
/lp group flyers permission set nocheatplus.checks.moving.survivalfly true
/lp group flyers permission set nocheatplus.checks.moving.creativefly true
```

Alternatively raise the allowance in `config.yml` rather than exempting:

```yaml
checks:
  moving:
    survivalfly:
      # Elytra handling is separate from walking; the lenient setting is what
      # absorbs a client applied boost.
      lenient: true
```

### Vulcan

Vulcan has per-check bypass permissions:

```
/lp group flyers permission set vulcan.bypass.speed true
/lp group flyers permission set vulcan.bypass.flight true
```

### Matrix

Matrix uses a single bypass permission plus per-check toggles in `config.yml`:

```
/lp group flyers permission set matrix.bypass true
```

To keep the rest of Matrix active, disable only the elytra-adjacent checks:

```yaml
checks:
  fly:
    enabled: true
    # Elytra subchecks are the ones a client applied boost trips.
    elytra: false
  speed:
    enabled: true
```

## If it still flags

Run `/slipstream debug` on the client. The overlay reports whether boost is
allowed at all, which separates "the server never granted it" from "the server
granted it and the anticheat then rejected the movement". Those are opposite
problems, and the fix for one makes the other worse.

## The other direction: no anticheat at all

If you run no anticheat, none of this applies. Grant `slipstream.use` to
everyone, which is the default, and there is nothing to configure.

## Movement mods

Slipstream applies its forces from a `TAIL` injection on `LivingEntity.travel`.
Another movement mod that cancels that method at `HEAD` prevents the injection
running at all. Particles keep appearing, because they are emitted separately,
so the symptom is a visible wake with no force. There is no general fix; test
the combination before shipping it on a pack.
