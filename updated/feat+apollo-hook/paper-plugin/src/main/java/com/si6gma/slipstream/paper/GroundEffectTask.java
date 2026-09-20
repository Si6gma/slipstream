/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.FluidCollisionMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Waterlogged
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityToggleGlideEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.util.RayTraceResult
 *  org.bukkit.util.Vector
 */
package com.si6gma.slipstream.paper;

import com.si6gma.slipstream.paper.GroundEffectMath;
import com.si6gma.slipstream.paper.SlipstreamPlugin;
import com.si6gma.slipstream.paper.apollo.ClientFeedback;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class GroundEffectTask
extends BukkitRunnable
implements Listener {
    private final SlipstreamPlugin plugin;
    private final ClientFeedback feedback;
    private double effectHeight;
    private double waterSprayHeight;
    private double effectSpeedThreshold;
    private double maxSpeed;
    private boolean particlesEnabled;
    private Set<String> disabledWorlds;
    private final Random random = new Random();
    private final Set<UUID> glidingPlayers = Collections.synchronizedSet(new HashSet());
    private final Map<UUID, CachedHit> hitCache = new HashMap<UUID, CachedHit>();
    private final Set<UUID> feedbackActive = new HashSet<UUID>();

    public GroundEffectTask(SlipstreamPlugin plugin, ClientFeedback feedback) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.reload();
    }

    public void reload() {
        this.effectHeight = this.plugin.getConfig().getDouble("effect-height", 20.0);
        this.waterSprayHeight = this.plugin.getConfig().getDouble("water-spray-height", 5.0);
        this.effectSpeedThreshold = this.plugin.getConfig().getDouble("effect-speed-threshold", 0.2);
        this.maxSpeed = this.plugin.getConfig().getDouble("max-speed", 3.0);
        this.particlesEnabled = this.plugin.getConfig().getBoolean("particles-enabled", true);
        this.disabledWorlds = new HashSet<String>(this.plugin.getConfig().getStringList("disabled-worlds"));
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (e.isGliding()) {
            this.glidingPlayers.add(player.getUniqueId());
        } else {
            this.glidingPlayers.remove(player.getUniqueId());
            this.hitCache.remove(player.getUniqueId());
            this.endFeedback(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        this.glidingPlayers.remove(id);
        this.hitCache.remove(id);
        this.feedbackActive.remove(id);
        this.feedback.playerGone(id);
    }

    private void endFeedback(UUID playerId) {
        if (this.feedbackActive.remove(playerId)) {
            this.feedback.effectEnded(playerId);
        }
    }

    private void endAllFeedback() {
        if (this.feedbackActive.isEmpty()) {
            return;
        }
        for (UUID id : this.feedbackActive.toArray(new UUID[0])) {
            this.endFeedback(id);
        }
    }

    public void run() {
        if (!this.plugin.isEffectEnabled()) {
            this.endAllFeedback();
            return;
        }
        for (UUID id : this.glidingPlayers.toArray(new UUID[this.glidingPlayers.size()])) {
            Player player = Bukkit.getPlayer((UUID)id);
            if (player != null && player.isOnline() && player.isGliding()) {
                this.processPlayer(player);
                continue;
            }
            this.glidingPlayers.remove(id);
            this.hitCache.remove(id);
            this.endFeedback(id);
        }
    }

    private void processPlayer(Player player) {
        double waterProx;
        boolean aboveThreshold;
        Waterlogged wl;
        double tz;
        if (!this.particlesEnabled && !this.feedback.isActive()) {
            return;
        }
        if (this.disabledWorlds.contains(player.getWorld().getName()) || player.isUnderWater() || player.isInLava()) {
            this.endFeedback(player.getUniqueId());
            return;
        }
        Vector vel = player.getVelocity();
        double hSpeedSq = vel.getX() * vel.getX() + vel.getZ() * vel.getZ();
        if (hSpeedSq < 0.0025) {
            this.endFeedback(player.getUniqueId());
            return;
        }
        double hSpeed = Math.sqrt(hSpeedSq);
        Location pos = player.getLocation();
        World world = pos.getWorld();
        UUID id = player.getUniqueId();
        int heightmapY = world.getHighestBlockYAt(pos.getBlockX(), pos.getBlockZ());
        if (pos.getY() - (double)heightmapY > this.effectHeight) {
            this.endFeedback(id);
            return;
        }
        CachedHit cached = this.hitCache.get(id);
        long tick = this.plugin.getServer().getCurrentTick();
        if (cached == null || tick - cached.tick() > 3L || cached.pos().distanceSquared(pos.toVector()) > 1.0) {
            RayTraceResult result = world.rayTraceBlocks(pos, new Vector(0, -1, 0), this.effectHeight, FluidCollisionMode.ALWAYS, true);
            cached = new CachedHit(result, pos.toVector(), tick);
            this.hitCache.put(id, cached);
        }
        if (cached.result() == null || cached.result().getHitBlock() == null) {
            this.endFeedback(id);
            return;
        }
        Location hitLoc = Objects.requireNonNull(cached.result().getHitPosition()).toLocation(world);
        double distToSurface = pos.getY() - hitLoc.getY();
        if (distToSurface <= 0.0 || distToSurface >= this.effectHeight) {
            this.endFeedback(id);
            return;
        }
        double proximity = GroundEffectMath.proximity(distToSurface, this.effectHeight);
        double surfaceY = hitLoc.getY();
        double tx = vel.getX() / hSpeed;
        double rx = tz = vel.getZ() / hSpeed;
        double rz = -tx;
        int playerTick = player.getTicksLived();
        Block hitBlock = cached.result().getHitBlock();
        BlockData hitData = hitBlock.getBlockData();
        boolean isWater = hitBlock.getType() == Material.WATER || hitBlock.getType() == Material.BUBBLE_COLUMN || hitBlock.getType() == Material.KELP || hitBlock.getType() == Material.KELP_PLANT || hitBlock.getType() == Material.SEAGRASS || hitBlock.getType() == Material.TALL_SEAGRASS || hitData instanceof Waterlogged && (wl = (Waterlogged)hitData).isWaterlogged();
        boolean bl = aboveThreshold = hSpeed >= this.effectSpeedThreshold * this.maxSpeed;
        if (aboveThreshold) {
            this.feedbackActive.add(id);
            this.feedback.effectActive(player, proximity, tick);
        } else {
            this.endFeedback(id);
        }
        if (!this.particlesEnabled) {
            return;
        }
        if (playerTick % 2 == 0) {
            if (aboveThreshold) {
                double wingOffset = 1.2;
                double vortexOut = 0.12 * proximity;
                world.spawnParticle(Particle.CLOUD, pos.getX() + rx * wingOffset, pos.getY() + 0.3, pos.getZ() + rz * wingOffset, 0, rx * vortexOut - tx * 0.03, 0.01, rz * vortexOut - tz * 0.03, 0.0);
                world.spawnParticle(Particle.CLOUD, pos.getX() - rx * wingOffset, pos.getY() + 0.3, pos.getZ() - rz * wingOffset, 0, -rx * vortexOut - tx * 0.03, 0.01, -rz * vortexOut - tz * 0.03, 0.0);
            }
            if (isWater && distToSurface <= this.waterSprayHeight) {
                waterProx = 1.0 - distToSurface / this.waterSprayHeight;
                int contactCount = 2 + (int)(waterProx * 3.0);
                world.spawnParticle(Particle.SPLASH, pos.getX() + rx, surfaceY + 0.05, pos.getZ() + rz, contactCount, 0.2, 0.05, 0.2, 1.0);
                world.spawnParticle(Particle.SPLASH, pos.getX() - rx, surfaceY + 0.05, pos.getZ() - rz, contactCount, 0.2, 0.05, 0.2, 1.0);
            }
        }
        if (playerTick % 3 != 0) {
            return;
        }
        if (isWater && distToSurface <= this.waterSprayHeight) {
            waterProx = 1.0 - distToSurface / this.waterSprayHeight;
            int sprayCount = 2 + (int)(waterProx * hSpeed * 6.0);
            for (int i = 0; i < Math.min(sprayCount, 8); ++i) {
                double wingPos = 0.8 + this.random.nextDouble() * 0.7;
                double jitter = (this.random.nextDouble() - 0.5) * 0.3;
                double outward = (0.3 + this.random.nextDouble() * 0.3) * waterProx;
                double forward = hSpeed * (0.08 + this.random.nextDouble() * 0.08);
                double up = (0.9 + this.random.nextDouble() * 1.2) * waterProx;
                world.spawnParticle(Particle.SPLASH, pos.getX() + rx * wingPos + tx * jitter, surfaceY + 0.05, pos.getZ() + rz * wingPos + tz * jitter, 0, rx * outward + tx * forward, up, rz * outward + tz * forward, 1.0);
                world.spawnParticle(Particle.SPLASH, pos.getX() - rx * wingPos + tx * jitter, surfaceY + 0.05, pos.getZ() - rz * wingPos + tz * jitter, 0, -rx * outward + tx * forward, up, -rz * outward + tz * forward, 1.0);
            }
            int wakeCount = 1 + (int)(waterProx * hSpeed * 3.0);
            for (int i = 0; i < Math.min(wakeCount, 5); ++i) {
                double back = 0.3 + this.random.nextDouble() * 2.0;
                double side = (this.random.nextDouble() - 0.5) * 0.8;
                world.spawnParticle(Particle.SPLASH, pos.getX() - tx * back + rx * side, surfaceY + 0.05, pos.getZ() - tz * back + rz * side, 0, (this.random.nextDouble() - 0.5) * 0.04, 0.08 + this.random.nextDouble() * 0.08, (this.random.nextDouble() - 0.5) * 0.04, 1.0);
            }
            if (waterProx > 0.5 && this.random.nextInt(3) == 0) {
                world.spawnParticle(Particle.FALLING_WATER, pos.getX() + tx * this.random.nextDouble() * 1.5 + rx * (this.random.nextDouble() - 0.5) * 1.5, surfaceY + 0.15 + this.random.nextDouble() * 0.4, pos.getZ() + tz * this.random.nextDouble() * 1.5 + rz * (this.random.nextDouble() - 0.5) * 1.5, 0, tx * 0.02, 0.02, tz * 0.02, 1.0);
            }
        } else if (!isWater) {
            int dustCount = 1 + (int)(proximity * hSpeed * 1.5);
            for (int i = 0; i < Math.min(dustCount, 4); ++i) {
                double sx = (this.random.nextDouble() - 0.5) * 2.5;
                double sz = (this.random.nextDouble() - 0.5) * 2.5;
                world.spawnParticle(Particle.BLOCK, pos.getX() + sx, surfaceY + 0.1, pos.getZ() + sz, 0, sx * 0.04, 0.05 + this.random.nextDouble() * 0.08, sz * 0.04, 0.0, (Object)hitData);
            }
            if (proximity > 0.3) {
                int puffCount = 1 + (int)(proximity * hSpeed * 0.5);
                for (int i = 0; i < Math.min(puffCount, 2); ++i) {
                    double back = 0.5 + this.random.nextDouble() * 1.5;
                    double side = this.random.nextDouble() - 0.5;
                    world.spawnParticle(Particle.POOF, pos.getX() - tx * back + rx * side, surfaceY + 0.2 + this.random.nextDouble() * 0.3, pos.getZ() - tz * back + rz * side, 0, (this.random.nextDouble() - 0.5) * 0.02, 0.03 + this.random.nextDouble() * 0.03, (this.random.nextDouble() - 0.5) * 0.02, 1.0);
                }
            }
        }
    }

    public void cleanup() {
        this.hitCache.clear();
        this.glidingPlayers.clear();
        this.feedbackActive.clear();
    }

    private record CachedHit(RayTraceResult result, Vector pos, long tick) {
    }
}
