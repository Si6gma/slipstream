package com.si6gma.slipstream.paper;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GroundEffectTask extends BukkitRunnable implements Listener {

    private final SlipstreamPlugin plugin;
    private double effectHeight;
    private double waterSprayHeight;
    private double effectSpeedThreshold;
    private double maxSpeed;
    private double acceleration;
    private double liftStrength;
    private Set<String> disabledWorlds;
    private final Random random = new Random();

    // Only iterate actively-gliding players instead of all online players
    private final Set<UUID> glidingPlayers = Collections.synchronizedSet(new HashSet<>());

    // Per-player raycast cache — avoids a full DDA traversal every tick
    private final Map<UUID, CachedHit> hitCache = new HashMap<>();

    public GroundEffectTask(SlipstreamPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        effectHeight = plugin.getConfig().getDouble("effect-height", 20.0);
        waterSprayHeight = plugin.getConfig().getDouble("water-spray-height", 5.0);
        effectSpeedThreshold = plugin.getConfig().getDouble("effect-speed-threshold", 0.2);
        maxSpeed = plugin.getConfig().getDouble("max-speed", 3.0);
        disabledWorlds = new HashSet<>(plugin.getConfig().getStringList("disabled-worlds"));
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.isGliding()) {
            glidingPlayers.add(player.getUniqueId());
        } else {
            glidingPlayers.remove(player.getUniqueId());
            hitCache.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        glidingPlayers.remove(id);
        hitCache.remove(id);
    }

    @Override
    public void run() {
        for (UUID id : glidingPlayers.toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline() && player.isGliding()) {
                processPlayer(player);
            } else {
                glidingPlayers.remove(id);
                hitCache.remove(id);
            }
        }
    }

    private void processPlayer(Player player) {
        if (disabledWorlds.contains(player.getWorld().getName())) return;
        Vector vel = player.getVelocity();
        double hSpeedSq = vel.getX() * vel.getX() + vel.getZ() * vel.getZ();
        if (hSpeedSq < 0.0025) return;

        double hSpeed = Math.sqrt(hSpeedSq);
        Location pos = player.getLocation();
        World world = pos.getWorld();

        // O(1) heightmap pre-check before doing any raycast
        int heightmapY = world.getHighestBlockAt(pos.getBlockX(), pos.getBlockZ()).getY();
        if (pos.getY() - heightmapY > effectHeight) return;

        // Raycast cache: reuse hit if player hasn't moved > 1 block and cache < 3 ticks old
        UUID id = player.getUniqueId();
        CachedHit cached = hitCache.get(id);
        long tick = plugin.getServer().getCurrentTick();

        if (cached == null
                || tick - cached.tick() > 3
                || cached.pos().distanceSquared(pos.toVector()) > 1.0) {
            RayTraceResult result = world.rayTraceBlocks(
                    pos, new Vector(0, -1, 0), effectHeight,
                    FluidCollisionMode.ALWAYS, true);
            cached = new CachedHit(result, pos.toVector(), tick);
            hitCache.put(id, cached);
        }

        if (cached.result() == null || cached.result().getHitBlock() == null) return;

        Location hitLoc = Objects.requireNonNull(cached.result().getHitPosition()).toLocation(world);
        double distToSurface = pos.getY() - hitLoc.getY();
        if (distToSurface <= 0 || distToSurface >= effectHeight) return;

        double proximity = GroundEffectMath.proximity(distToSurface, effectHeight);
        double surfaceY = hitLoc.getY();

        // Normalised travel + right vectors
        double tx = vel.getX() / hSpeed;
        double tz = vel.getZ() / hSpeed;
        double rx = tz;
        double rz = -tx;

        int playerTick = player.getTicksLived();
        Block hitBlock = cached.result().getHitBlock();
        BlockData hitData = hitBlock.getBlockData();
        boolean isWater = hitBlock.getType() == Material.WATER
                || hitBlock.getType() == Material.BUBBLE_COLUMN
                || hitBlock.getType() == Material.KELP
                || hitBlock.getType() == Material.KELP_PLANT
                || hitBlock.getType() == Material.SEAGRASS
                || hitBlock.getType() == Material.TALL_SEAGRASS
                || (hitData instanceof Waterlogged wl && wl.isWaterlogged());

        boolean aboveThreshold = hSpeed >= effectSpeedThreshold * maxSpeed;

        // Vortex + contact burst: every 2 ticks
        if (playerTick % 2 == 0) {
            if (aboveThreshold) {
                double wingOffset = 1.2;
                double vortexOut = 0.12 * proximity;
                world.spawnParticle(Particle.CLOUD,
                        new Location(world, pos.getX() + rx * wingOffset, pos.getY() + 0.3, pos.getZ() + rz * wingOffset),
                        0, rx * vortexOut - tx * 0.03, 0.01, rz * vortexOut - tz * 0.03, 0);
                world.spawnParticle(Particle.CLOUD,
                        new Location(world, pos.getX() - rx * wingOffset, pos.getY() + 0.3, pos.getZ() - rz * wingOffset),
                        0, -rx * vortexOut - tx * 0.03, 0.01, -rz * vortexOut - tz * 0.03, 0);
            }

            if (isWater && distToSurface <= waterSprayHeight) {
                double waterProx = 1.0 - (distToSurface / waterSprayHeight);
                int contactCount = 2 + (int) (waterProx * 3);
                world.spawnParticle(Particle.SPLASH,
                        new Location(world, pos.getX() + rx, surfaceY + 0.05, pos.getZ() + rz),
                        contactCount, 0.2, 0.05, 0.2, 1.0);
                world.spawnParticle(Particle.SPLASH,
                        new Location(world, pos.getX() - rx, surfaceY + 0.05, pos.getZ() - rz),
                        contactCount, 0.2, 0.05, 0.2, 1.0);
            }
        }

        // Heavy loops: every 3 ticks
        if (playerTick % 3 != 0) return;

        if (isWater && distToSurface <= waterSprayHeight) {
            double waterProx = 1.0 - (distToSurface / waterSprayHeight);

            // Wingtip spray arcs
            int sprayCount = 2 + (int) (waterProx * hSpeed * 6);
            for (int i = 0; i < Math.min(sprayCount, 8); i++) {
                double wingPos = 0.8 + random.nextDouble() * 0.7;
                double jitter = (random.nextDouble() - 0.5) * 0.3;
                double outward = (0.3 + random.nextDouble() * 0.3) * waterProx;
                double forward = hSpeed * (0.08 + random.nextDouble() * 0.08);
                double up = (0.9 + random.nextDouble() * 1.2) * waterProx;
                world.spawnParticle(Particle.SPLASH,
                        new Location(world, pos.getX() + rx * wingPos + tx * jitter, surfaceY + 0.05, pos.getZ() + rz * wingPos + tz * jitter),
                        0, rx * outward + tx * forward, up, rz * outward + tz * forward, 1.0);
                world.spawnParticle(Particle.SPLASH,
                        new Location(world, pos.getX() - rx * wingPos + tx * jitter, surfaceY + 0.05, pos.getZ() - rz * wingPos + tz * jitter),
                        0, -rx * outward + tx * forward, up, -rz * outward + tz * forward, 1.0);
            }

            // Wake trail
            int wakeCount = 1 + (int) (waterProx * hSpeed * 3);
            for (int i = 0; i < Math.min(wakeCount, 5); i++) {
                double back = 0.3 + random.nextDouble() * 2.0;
                double side = (random.nextDouble() - 0.5) * 0.8;
                world.spawnParticle(Particle.SPLASH,
                        new Location(world, pos.getX() - tx * back + rx * side, surfaceY + 0.05, pos.getZ() - tz * back + rz * side),
                        0, (random.nextDouble() - 0.5) * 0.04, 0.08 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.04, 1.0);
            }

            // Fine mist
            if (waterProx > 0.5 && random.nextInt(3) == 0) {
                world.spawnParticle(Particle.FALLING_WATER,
                        new Location(world,
                                pos.getX() + tx * random.nextDouble() * 1.5 + rx * (random.nextDouble() - 0.5) * 1.5,
                                surfaceY + 0.15 + random.nextDouble() * 0.4,
                                pos.getZ() + tz * random.nextDouble() * 1.5 + rz * (random.nextDouble() - 0.5) * 1.5),
                        0, tx * 0.02, 0.02, tz * 0.02, 1.0);
            }

        } else if (!isWater) {
            // Block-accurate surface dust
            int dustCount = 1 + (int) (proximity * hSpeed * 1.5);
            for (int i = 0; i < Math.min(dustCount, 4); i++) {
                double sx = (random.nextDouble() - 0.5) * 2.5;
                double sz = (random.nextDouble() - 0.5) * 2.5;
                world.spawnParticle(Particle.BLOCK,
                        new Location(world, pos.getX() + sx, surfaceY + 0.1, pos.getZ() + sz),
                        0, sx * 0.04, 0.05 + random.nextDouble() * 0.08, sz * 0.04, 0, hitData);
            }

            // POOF puffs
            if (proximity > 0.3) {
                int puffCount = 1 + (int) (proximity * hSpeed * 0.5);
                for (int i = 0; i < Math.min(puffCount, 2); i++) {
                    double back = 0.5 + random.nextDouble() * 1.5;
                    double side = (random.nextDouble() - 0.5);
                    world.spawnParticle(Particle.POOF,
                            new Location(world, pos.getX() - tx * back + rx * side, surfaceY + 0.2 + random.nextDouble() * 0.3, pos.getZ() - tz * back + rz * side),
                            0, (random.nextDouble() - 0.5) * 0.02, 0.03 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02, 1.0);
                }
            }
        }
    }

    public void cleanup() {
        hitCache.clear();
        glidingPlayers.clear();
    }

    private record CachedHit(RayTraceResult result, Vector pos, long tick) {}
}
