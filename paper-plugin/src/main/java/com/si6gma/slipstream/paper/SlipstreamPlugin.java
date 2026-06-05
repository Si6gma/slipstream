package com.si6gma.slipstream.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SlipstreamPlugin extends JavaPlugin implements Listener {

    static final String CHANNEL = "slipstream:server_config";

    private GroundEffectTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        task = new GroundEffectTask(this);
        task.runTaskTimer(this, 0L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(task, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getLogger().info("Slipstream enabled.");
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
            task.cleanup();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("override-clients", true)) return;
        // Small delay so the client finishes loading before we send the packet
        Bukkit.getScheduler().runTaskLater(this, () -> sendConfig(e.getPlayer()), 20L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("slipstream")) return false;
        if (!sender.hasPermission("slipstream.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            broadcastConfig();
            sender.sendMessage("§aSlipstream config reloaded and pushed to all online players.");
            return true;
        }
        sender.sendMessage("§eUsage: /slipstream reload");
        return true;
    }

    void sendConfig(Player player) {
        if (!player.isOnline()) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeDouble(getConfig().getDouble("effect-height", 20.0));
            out.writeDouble(getConfig().getDouble("acceleration", 0.001));
            out.writeDouble(getConfig().getDouble("max-speed", 3.0));
            out.writeDouble(getConfig().getDouble("water-spray-height", 5.0));
            out.writeDouble(getConfig().getDouble("lift-strength", 0.015));
            out.writeDouble(getConfig().getDouble("effect-speed-threshold", 0.5));
            player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
        } catch (IOException ex) {
            getLogger().warning("Failed to send config to " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void broadcastConfig() {
        reloadConfig();
        if (task != null) task.reload();
        for (Player p : Bukkit.getOnlinePlayers()) sendConfig(p);
        getLogger().info("Config rebroadcast to " + Bukkit.getOnlinePlayers().size() + " players.");
    }
}
