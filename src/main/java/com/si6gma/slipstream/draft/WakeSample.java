package com.si6gma.slipstream.draft;

import net.minecraft.world.phys.Vec3;

/**
 * One recorded point of a glider's wake.
 *
 * @param position where the glider was
 * @param heading unit horizontal travel direction at that moment
 * @param speed horizontal speed in blocks per tick at that moment
 * @param tick the level tick this was recorded
 */
public record WakeSample(Vec3 position, Vec3 heading, double speed, int tick) {}
