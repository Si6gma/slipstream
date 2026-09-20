/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.si6gma.slipstream.paper.apollo;

import com.si6gma.slipstream.paper.apollo.GroundEffectFeedback;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public interface ClientFeedback {
    public static final ClientFeedback NOOP = new ClientFeedback(){

        @Override
        public void effectActive(Player player, double proximity, long tick) {
        }

        @Override
        public void effectEnded(UUID playerId) {
        }

        @Override
        public void playerGone(UUID playerId) {
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void reload() {
        }

        @Override
        public void shutdown() {
        }
    };

    public static ClientFeedback create(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("apollo.enabled", true)) {
            return NOOP;
        }
        if (!ClientFeedback.apolloInstalled(plugin)) {
            plugin.getLogger().info("Apollo not installed; Lunar Client feedback is off.");
            return NOOP;
        }
        try {
            GroundEffectFeedback feedback = new GroundEffectFeedback(plugin);
            plugin.getLogger().info("Apollo detected; Lunar Client feedback is on.");
            return feedback;
        }
        catch (Throwable t) {
            plugin.getLogger().warning("Apollo hook failed to start, continuing without it: " + String.valueOf(t));
            return NOOP;
        }
    }

    private static boolean apolloInstalled(JavaPlugin plugin) {
        for (String name : new String[]{"Apollo-Bukkit", "Apollo-Folia"}) {
            if (!plugin.getServer().getPluginManager().isPluginEnabled(name)) continue;
            return true;
        }
        return false;
    }

    public void effectActive(Player var1, double var2, long var4);

    public void effectEnded(UUID var1);

    public void playerGone(UUID var1);

    public boolean isActive();

    public void reload();

    public void shutdown();
}
