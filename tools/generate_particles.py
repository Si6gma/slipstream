#!/usr/bin/env python3
"""Generate Slipstream's particle sprites.

Run from the repository root:

    python3 tools/generate_particles.py

Writes into src/main/resources/assets/slipstream/textures/particle/. Deterministic, so rerunning
it produces byte identical files and leaves the working tree unchanged.

Why generate rather than draw. The shapes needed here are abstract and radial: a ring, a streak, a
haze. That is the one case procedural generation is genuinely good at, where hand drawing would be
slower and less consistent frame to frame. It would be the wrong tool for character art.

Every sprite is white RGB with the shape carried entirely in the alpha channel. That is what keeps
the existing per role tinting working, since tinting multiplies, and it is what makes a per player
wake hue possible at all: a sprite with colour baked in could only ever be darkened.

Only the standard library is used, so there is no Pillow to install before anyone can rebuild the
atlas.
"""

import math
import os
import struct
import zlib

SIZE = 16
OUT = os.path.join(
    "src", "main", "resources", "assets", "slipstream", "textures", "particle"
)


def write_png(path, pixels):
    """pixels is a SIZE x SIZE list of rows of alpha values in 0..255."""
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0, none
        for alpha in row:
            raw += bytes((255, 255, 255, alpha))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)  # 8 bit RGBA
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as handle:
        handle.write(png)
    print("wrote", path)


def blank():
    return [[0] * SIZE for _ in range(SIZE)]


def clamp_byte(value):
    return max(0, min(255, int(round(value))))


def vortex_ring(frame, frames):
    """An expanding ring that thins as it grows.

    This is the sprite custom art actually buys something for. Vanilla has no ring, so a vortex
    had to be faked with smoke, which reads as exhaust rather than as disturbed air.
    """
    t = frame / (frames - 1)
    centre = (SIZE - 1) / 2.0
    radius = (0.12 + 0.80 * t) * centre
    # Thins as it expands, so the ring reads as spreading rather than as a growing disc.
    thickness = (0.42 - 0.26 * t) * centre
    peak = 255.0 * (1.0 - t) ** 1.4

    pixels = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            dx = x - centre
            dy = y - centre
            distance = math.sqrt(dx * dx + dy * dy)
            falloff = (distance - radius) / max(thickness, 1e-6)
            pixels[y][x] = clamp_byte(peak * math.exp(-falloff * falloff))
    return pixels


def streak(frame, frames):
    """A soft elongated smear for draft strength, swept along the direction of travel."""
    t = frame / (frames - 1)
    centre = (SIZE - 1) / 2.0
    along = (0.55 + 0.30 * t) * centre
    across = (0.30 - 0.16 * t) * centre
    peak = 255.0 * (1.0 - t) ** 1.2

    pixels = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x - centre) / max(along, 1e-6)
            dy = (y - centre) / max(across, 1e-6)
            pixels[y][x] = clamp_byte(peak * math.exp(-(dx * dx + dy * dy)))
    return pixels


def haze(frame, frames):
    """A fine, wide, very faint blob for old wake. Nearly nothing, on purpose."""
    t = frame / (frames - 1)
    centre = (SIZE - 1) / 2.0
    spread = (0.45 + 0.40 * t) * centre
    peak = 150.0 * (1.0 - t) ** 0.9

    pixels = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x - centre) / max(spread, 1e-6)
            dy = (y - centre) / max(spread, 1e-6)
            pixels[y][x] = clamp_byte(peak * math.exp(-(dx * dx + dy * dy)))
    return pixels


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, maker, frames in (
        ("vortex_ring", vortex_ring, 8),
        ("streak", streak, 4),
        ("haze", haze, 4),
    ):
        for frame in range(frames):
            write_png(os.path.join(OUT, "%s_%d.png" % (name, frame)), maker(frame, frames))


if __name__ == "__main__":
    main()
