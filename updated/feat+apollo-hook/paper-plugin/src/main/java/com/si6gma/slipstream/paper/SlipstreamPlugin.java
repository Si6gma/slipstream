/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerRegisterChannelEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.jetbrains.annotations.NotNull
 */
package com.si6gma.slipstream.paper;

import com.si6gma.slipstream.paper.GroundEffectTask;
import com.si6gma.slipstream.paper.UpdateChecker;
import com.si6gma.slipstream.paper.apollo.ClientFeedback;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class SlipstreamPlugin
extends JavaPlugin
implements Listener,
TabCompleter {
    static final String CHANNEL = "slipstream:server_config";
    private static final List<String> SUBCOMMANDS = List.of("enable", "disable", "reload");
    private GroundEffectTask task;
    private ClientFeedback feedback = ClientFeedback.NOOP;
    private boolean effectEnabled = true;

    public void onEnable() {
        this.saveDefaultConfig();
        this.effectEnabled = this.getConfig().getBoolean("effect-enabled", true);
        this.feedback = ClientFeedback.create(this);
        this.task = new GroundEffectTask(this, this.feedback);
        this.task.runTaskTimer((Plugin)this, 0L, 1L);
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)this.task, (Plugin)this);
        this.getServer().getMessenger().registerOutgoingPluginChannel((Plugin)this, CHANNEL);
        this.getCommand("slipstream").setTabCompleter((TabCompleter)this);
        UpdateChecker.checkAsync(this);
        this.getLogger().info("Slipstream enabled.");
    }

    public void onDisable() {
        if (this.task != null) {
            this.task.cancel();
            this.task.cleanup();
        }
        this.feedback.shutdown();
        this.getServer().getMessenger().unregisterOutgoingPluginChannel((Plugin)this, CHANNEL);
    }

    public boolean isEffectEnabled() {
        return this.effectEnabled;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!this.getConfig().getBoolean("override-clients", true)) {
            return;
        }
        Player player = e.getPlayer();
        if (player.getListeningPluginChannels().contains(CHANNEL)) {
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.sendConfigForWorld(player, player.getWorld().getName()), 2L);
        }
    }

    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent e) {
        if (!e.getChannel().equals(CHANNEL)) {
            return;
        }
        if (!this.getConfig().getBoolean("override-clients", true)) {
            return;
        }
        Player player = e.getPlayer();
        this.sendConfigForWorld(player, player.getWorld().getName());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!this.getConfig().getBoolean("override-clients", true)) {
            return;
        }
        Player player = e.getPlayer();
        if (!player.getListeningPluginChannels().contains(CHANNEL)) {
            return;
        }
        this.sendConfigForWorld(player, player.getWorld().getName());
    }

    private void sendConfigForWorld(Player player, String worldName) {
        if (!player.isOnline()) {
            return;
        }
        if (this.getConfig().getStringList("disabled-worlds").contains(worldName)) {
            this.sendDisabledConfig(player);
        } else if (this.effectEnabled) {
            this.sendConfig(player);
        } else {
            this.sendDisabledConfig(player);
        }
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("slipstream")) {
            return false;
        }
        if (!sender.hasPermission("slipstream.admin")) {
            sender.sendMessage("\u00a7cYou don't have permission to use this command.");
            return true;
        }
        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "enable": {
                    if (this.effectEnabled) {
                        sender.sendMessage("\u00a7eSlipstream is already enabled.");
                    } else {
                        this.effectEnabled = true;
                        this.getConfig().set("effect-enabled", (Object)true);
                        this.saveConfig();
                        this.broadcastConfig();
                        sender.sendMessage("\u00a7aSlipstream ground effect enabled.");
                    }
                    return true;
                }
                case "disable": {
                    if (!this.effectEnabled) {
                        sender.sendMessage("\u00a7eSlipstream is already disabled.");
                    } else {
                        this.effectEnabled = false;
                        this.getConfig().set("effect-enabled", (Object)false);
                        this.saveConfig();
                        List disabled = this.getConfig().getStringList("disabled-worlds");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (disabled.contains(p.getWorld().getName())) continue;
                            this.sendDisabledConfig(p);
                        }
                        sender.sendMessage("\u00a7cSlipstream ground effect disabled.");
                    }
                    return true;
                }
                case "reload": {
                    this.broadcastConfig();
                    sender.sendMessage("\u00a7aSlipstream config reloaded and pushed to all online players.");
                    return true;
                }
            }
        }
        sender.sendMessage("\u00a7eUsage: /slipstream <enable|disable|reload>");
        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("slipstream")) {
            return List.of();
        }
        if (!sender.hasPermission("slipstream.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }

    private static byte[] serializePayload(double effectHeight, double acceleration, double maxSpeed, double waterSprayHeight, double liftStrength, double speedThreshold) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(48);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeDouble(effectHeight);
        out.writeDouble(acceleration);
        out.writeDouble(maxSpeed);
        out.writeDouble(waterSprayHeight);
        out.writeDouble(liftStrength);
        out.writeDouble(speedThreshold);
        return bytes.toByteArray();
    }

    void sendConfig(Player player) {
        if (!player.isOnline()) {
            return;
        }
        try {
            player.sendPluginMessage((Plugin)this, CHANNEL, SlipstreamPlugin.serializePayload(this.getConfig().getDouble("effect-height", 20.0), this.getConfig().getDouble("acceleration", 0.005), this.getConfig().getDouble("max-speed", 1.5), this.getConfig().getDouble("water-spray-height", 5.0), this.getConfig().getDouble("lift-strength", 0.6), this.getConfig().getDouble("effect-speed-threshold", 0.3)));
        }
        catch (IOException ex) {
            this.getLogger().warning("Failed to send config to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void sendDisabledConfig(Player player) {
        if (!player.isOnline()) {
            return;
        }
        try {
            player.sendPluginMessage((Plugin)this, CHANNEL, SlipstreamPlugin.serializePayload(this.getConfig().getDouble("effect-height", 20.0), 0.0, this.getConfig().getDouble("max-speed", 1.5), this.getConfig().getDouble("water-spray-height", 5.0), 0.0, 1.0));
        }
        catch (IOException ex) {
            this.getLogger().warning("Failed to send disabled config to " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void broadcastConfig() {
        this.reloadConfig();
        this.effectEnabled = this.getConfig().getBoolean("effect-enabled", true);
        if (this.task != null) {
            this.task.reload();
        }
        this.feedback.reload();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline() || !p.getListeningPluginChannels().contains(CHANNEL)) continue;
            this.sendConfigForWorld(p, p.getWorld().getName());
        }
        this.getLogger().info("Config rebroadcast to " + Bukkit.getOnlinePlayers().size() + " players.");
    }
}
