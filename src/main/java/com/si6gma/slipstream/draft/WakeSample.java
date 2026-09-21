package com.si6gma.slipstream.draft;

import net.minecraft.world.phys.Vec3;

/**
 * One recorded point of a glider's wake.
 *
 * <p>Deliberately carries no heading. A sample's direction of travel is the direction of the
 * segment that reaches it, which {@link DraftingMath} derives from this position and the previous
 * one. Storing a separately measured heading let the two disagree: it came from the glider's
 * reported velocity, and for a remote player that is a multi tick lerp toward an already stale
 * broadcast, so through a turn the stored heading still pointed down a leg the trail had left.
 *
 * @param position where the glider was
 * @param speed horizontal speed in blocks per tick at that moment
 * @param tick the level tick this was recorded
 * @param boost wake strength multiplier, 1.0 normally and higher while the glider was under a
 *     firework, which is what makes a rocket leave a slingshot behind it
 */
public record WakeSample(Vec3 position, double speed, int tick, double boost) {}
