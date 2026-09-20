package com.si6gma.slipstream.draft;

import net.minecraft.world.phys.Vec3;

/**
 * Where a follower sits relative to a leader's wake.
 *
 * @param point nearest point on the wake centreline
 * @param toCentre unit vector from the follower toward that point, or zero when already centred
 * @param lateralOffset distance from the follower to that point, in blocks
 * @param ageSeconds how long ago the leader was at that point
 * @param strength combined age and offset falloff, in [0, 1]
 * @param wakeHeading the leader's heading at that point
 */
public record DraftQuery(
    Vec3 point,
    Vec3 toCentre,
    double lateralOffset,
    double ageSeconds,
    double strength,
    Vec3 wakeHeading) {}
