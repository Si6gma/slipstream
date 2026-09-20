package com.si6gma.slipstream;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One surface sample for a gliding entity. Pure data: no config, no entity reference.
 *
 * @param distToSurface blocks between the entity and the surface hit directly below it
 * @param proximity {@link GroundEffectMath#proximity} for that distance, in [0, 1]
 * @param surfaceBlock the block state at the raycast hit
 * @param surfaceY the y coordinate of the raycast hit
 * @param isWater whether the surface block's fluid is water
 * @param hSpeed horizontal speed in blocks per tick
 * @param travelDir unit horizontal velocity
 * @param right travelDir rotated 90 degrees clockwise when viewed from above
 */
public record GroundEffectSample(
    double distToSurface,
    double proximity,
    BlockState surfaceBlock,
    double surfaceY,
    boolean isWater,
    double hSpeed,
    Vec3 travelDir,
    Vec3 right) {}
