package com.si6gma.slipstream.mixin;

import com.si6gma.slipstream.GroundEffectMath;
import com.si6gma.slipstream.ModParticles;
import com.si6gma.slipstream.SlipstreamConfig;
import com.si6gma.slipstream.network.ServerConfigOverride;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    // Per-entity raycast cache. Instance fields are GC'd with the entity — no explicit
    // cleanup needed. Dead entities can't call travel(), so stale cache is never read.
    @Unique private BlockHitResult ege$cachedHit;
    @Unique private double ege$cacheX, ege$cacheY, ege$cacheZ;
    @Unique private int ege$cacheAge;

    @Inject(method = "travel", at = @At("TAIL"))
    private void applyGroundEffect(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isFallFlying()) return;

        Vec3 velocity = self.getDeltaMovement();
        double hSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
        if (hSpeedSq < 0.0025) return;

        double hSpeed = Math.sqrt(hSpeedSq);
        Vec3 pos = self.position();
        Vec3 travelDir = new Vec3(velocity.x / hSpeed, 0, velocity.z / hSpeed);

        // On client: returns server override if one was received, else local config.
        // On server: active is always null, so always returns local config.
        SlipstreamConfig cfg = ServerConfigOverride.get();

        // O(1) heightmap pre-check — bail before any raycast when clearly too high
        int heightmapY = self.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
        if (pos.y - heightmapY > cfg.effectHeightBlocks) return;

        // Raycast cache — reuse hit until player moves >1 block or cache is >3 ticks old
        ege$cacheAge++;
        double dx = pos.x - ege$cacheX;
        double dz = pos.z - ege$cacheZ;
        double dy = pos.y - ege$cacheY;
        if (ege$cachedHit == null || ege$cacheAge > 3 || dx * dx + dy * dy + dz * dz > 1.0) {
            ege$cachedHit = self.level().clip(new ClipContext(
                    pos, pos.add(0, -cfg.effectHeightBlocks, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, self));
            ege$cacheX = pos.x;
            ege$cacheY = pos.y;
            ege$cacheZ = pos.z;
            ege$cacheAge = 0;
        }
        BlockHitResult surfaceHit = ege$cachedHit;
        if (surfaceHit.getType() == HitResult.Type.MISS) return;

        double distToSurface = pos.y - surfaceHit.getLocation().y;
        if (distToSurface <= 0 || distToSurface >= cfg.effectHeightBlocks) return;

        double proximity = GroundEffectMath.proximity(distToSurface, cfg.effectHeightBlocks);

        if (self.level().isClientSide()) {
            // Speed boost — must be client-side (elytra is client-authoritative)
            if (!(self instanceof Player player) || !player.isLocalPlayer()) return;

            if (ServerConfigOverride.isBoostAllowed()) {
                Vec3 boosted = velocity;
                if (hSpeedSq < cfg.maxSpeedBlocksPerTick * cfg.maxSpeedBlocksPerTick) {
                    double delta = GroundEffectMath.boostDelta(hSpeed, proximity, cfg.accelerationPerTick, cfg.maxSpeedBlocksPerTick);
                    boosted = velocity.add(travelDir.scale(delta));
                    double boostedHSq = boosted.x * boosted.x + boosted.z * boosted.z;
                    if (boostedHSq > cfg.maxSpeedBlocksPerTick * cfg.maxSpeedBlocksPerTick) {
                        boosted = velocity.add(travelDir.scale(cfg.maxSpeedBlocksPerTick - hSpeed));
                    }
                }
                if (hSpeed >= cfg.effectSpeedThreshold * cfg.maxSpeedBlocksPerTick) {
                    double lift = GroundEffectMath.liftForce(boosted.y, proximity, cfg.liftStrength);
                    boosted = boosted.add(0, lift, 0);
                }
                player.setDeltaMovement(boosted);
            }

        } else {
            if (!(self instanceof ServerPlayer player)) return;

            ServerLevel level = player.level();
            Vec3 right = new Vec3(travelDir.z, 0, -travelDir.x);
            var random = player.getRandom();
            double surfaceY = surfaceHit.getLocation().y;
            int tick = player.tickCount;

            // Vortex + contact burst: every 2 ticks (lightweight, keep dense trail)
            if (tick % 2 == 0) {
                if (ModParticles.WING_VORTEX != null && hSpeed >= cfg.effectSpeedThreshold * cfg.maxSpeedBlocksPerTick) {
                    double wingOffset = 1.2;
                    double vortexOut = 0.12 * proximity;
                    level.sendParticles(ModParticles.WING_VORTEX,
                            pos.x + right.x * wingOffset, pos.y + 0.3, pos.z + right.z * wingOffset,
                            0, right.x * vortexOut - travelDir.x * 0.03, 0.01, right.z * vortexOut - travelDir.z * 0.03, 0);
                    level.sendParticles(ModParticles.WING_VORTEX,
                            pos.x - right.x * wingOffset, pos.y + 0.3, pos.z - right.z * wingOffset,
                            0, -right.x * vortexOut - travelDir.x * 0.03, 0.01, -right.z * vortexOut - travelDir.z * 0.03, 0);
                }

                BlockState surfaceBlock = level.getBlockState(surfaceHit.getBlockPos());
                boolean isWater = surfaceBlock.getFluidState().is(FluidTags.WATER);

                if (isWater && distToSurface <= cfg.waterSprayHeightBlocks) {
                    double waterProximity = 1.0 - (distToSurface / cfg.waterSprayHeightBlocks);
                    int contactCount = 2 + (int) (waterProximity * 3);
                    level.sendParticles(ParticleTypes.SPLASH,
                            pos.x + right.x, surfaceY + 0.05, pos.z + right.z,
                            contactCount, 0.2, 0.05, 0.2, 1.0);
                    level.sendParticles(ParticleTypes.SPLASH,
                            pos.x - right.x, surfaceY + 0.05, pos.z - right.z,
                            contactCount, 0.2, 0.05, 0.2, 1.0);
                }
            }

            // Heavier spray/dust loops: every 3 ticks
            if (tick % 3 != 0) return;

            BlockState surfaceBlock = level.getBlockState(surfaceHit.getBlockPos());
            boolean isWater = surfaceBlock.getFluidState().is(FluidTags.WATER);

            if (isWater && distToSurface <= cfg.waterSprayHeightBlocks) {
                double waterProximity = 1.0 - (distToSurface / cfg.waterSprayHeightBlocks);

                // Wingtip spray arcs (capped at 8/side)
                int sprayCount = 2 + (int) (waterProximity * hSpeed * 6);
                for (int i = 0; i < Math.min(sprayCount, 8); i++) {
                    double wingPos = 0.8 + random.nextDouble() * 0.7;
                    double spawnJitter = (random.nextDouble() - 0.5) * 0.3;
                    double outward = (0.3 + random.nextDouble() * 0.3) * waterProximity;
                    double forward = hSpeed * (0.08 + random.nextDouble() * 0.08);
                    double up = (0.9 + random.nextDouble() * 1.2) * waterProximity;
                    level.sendParticles(ParticleTypes.SPLASH,
                            pos.x + right.x * wingPos + travelDir.x * spawnJitter, surfaceY + 0.05,
                            pos.z + right.z * wingPos + travelDir.z * spawnJitter,
                            0, right.x * outward + travelDir.x * forward, up, right.z * outward + travelDir.z * forward, 1.0);
                    level.sendParticles(ParticleTypes.SPLASH,
                            pos.x - right.x * wingPos + travelDir.x * spawnJitter, surfaceY + 0.05,
                            pos.z - right.z * wingPos + travelDir.z * spawnJitter,
                            0, -right.x * outward + travelDir.x * forward, up, -right.z * outward + travelDir.z * forward, 1.0);
                }

                // Wake trail (capped at 5)
                int wakeCount = 1 + (int) (waterProximity * hSpeed * 3);
                for (int i = 0; i < Math.min(wakeCount, 5); i++) {
                    double trailBack = 0.3 + random.nextDouble() * 2.0;
                    double trailSide = (random.nextDouble() - 0.5) * 0.8;
                    level.sendParticles(ParticleTypes.SPLASH,
                            pos.x - travelDir.x * trailBack + right.x * trailSide, surfaceY + 0.05,
                            pos.z - travelDir.z * trailBack + right.z * trailSide,
                            0, (random.nextDouble() - 0.5) * 0.04, 0.08 + random.nextDouble() * 0.08,
                            (random.nextDouble() - 0.5) * 0.04, 1.0);
                }

                // Fine mist
                if (waterProximity > 0.5 && random.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.FALLING_WATER,
                            pos.x + travelDir.x * random.nextDouble() * 1.5 + right.x * (random.nextDouble() - 0.5) * 1.5,
                            surfaceY + 0.15 + random.nextDouble() * 0.4,
                            pos.z + travelDir.z * random.nextDouble() * 1.5 + right.z * (random.nextDouble() - 0.5) * 1.5,
                            0, travelDir.x * 0.02, 0.02, travelDir.z * 0.02, 1.0);
                }

            } else if (!isWater && !surfaceBlock.isAir()) {
                // Ground dust (capped at 4)
                int dustCount = 1 + (int) (proximity * hSpeed * 1.5);
                for (int i = 0; i < Math.min(dustCount, 4); i++) {
                    double scatterX = (random.nextDouble() - 0.5) * 2.5;
                    double scatterZ = (random.nextDouble() - 0.5) * 2.5;
                    level.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, surfaceBlock),
                            pos.x + scatterX, surfaceY + 0.1, pos.z + scatterZ,
                            0, scatterX * 0.04, 0.05 + random.nextDouble() * 0.08, scatterZ * 0.04, 0);
                }

                // POOF puffs (capped at 2, only when close)
                if (proximity > 0.3) {
                    int puffCount = 1 + (int) (proximity * hSpeed * 0.5);
                    for (int i = 0; i < Math.min(puffCount, 2); i++) {
                        level.sendParticles(ParticleTypes.POOF,
                                pos.x - travelDir.x * (0.5 + random.nextDouble() * 1.5) + right.x * (random.nextDouble() - 0.5),
                                surfaceY + 0.2 + random.nextDouble() * 0.3,
                                pos.z - travelDir.z * (0.5 + random.nextDouble() * 1.5) + right.z * (random.nextDouble() - 0.5),
                                0, (random.nextDouble() - 0.5) * 0.02,
                                0.03 + random.nextDouble() * 0.03,
                                (random.nextDouble() - 0.5) * 0.02, 1.0);
                    }
                }
            }
        }
    }
}
