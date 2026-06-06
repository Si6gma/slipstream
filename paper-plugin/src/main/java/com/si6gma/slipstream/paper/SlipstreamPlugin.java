package com.si6gma.slipstream.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class SlipstreamPlugin extends JavaPlugin implements Listener, TabCompleter {

  static final String CHANNEL = "slipstream:server_config";
  private static final List<String> SUBCOMMANDS = List.of("enable", "disable", "reload");

  private GroundEffectTask task;
  private boolean effectEnabled = true;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    effectEnabled = getConfig().getBoolean("effect-enabled", true);
    task = new GroundEffectTask(this);
    task.runTaskTimer(this, 0L, 1L);
    getServer().getPluginManager().registerEvents(this, this);
    getServer().getPluginManager().registerEvents(task, this);
    getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
    getCommand("slipstream").setTabCompleter(this);
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

  public boolean isEffectEnabled() {
    return effectEnabled;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    if (!getConfig().getBoolean("override-clients", true)) return;
    Bukkit.getScheduler()
        .runTaskLater(
            this,
            () -> {
              Player player = e.getPlayer();
              if (getConfig().getStringList("disabled-worlds").contains(player.getWorld().getName()))
                return;
              if (effectEnabled) sendConfig(player);
              else sendDisabledConfig(player);
            },
            20L);
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase("slipstream")) return false;
    if (!sender.hasPermission("slipstream.admin")) {
      sender.sendMessage("§cYou don't have permission to use this command.");
      return true;
    }
    if (args.length == 1) {
      switch (args[0].toLowerCase()) {
        case "enable" -> {
          if (effectEnabled) {
            sender.sendMessage("§eSlipstream is already enabled.");
          } else {
            effectEnabled = true;
            getConfig().set("effect-enabled", true);
            saveConfig();
            broadcastConfig();
            sender.sendMessage("§aSlipstream ground effect enabled.");
          }
          return true;
        }
        case "disable" -> {
          if (!effectEnabled) {
            sender.sendMessage("§eSlipstream is already disabled.");
          } else {
            effectEnabled = false;
            getConfig().set("effect-enabled", false);
            saveConfig();
            List<String> disabled = getConfig().getStringList("disabled-worlds");
            for (Player p : Bukkit.getOnlinePlayers()) {
              if (!disabled.contains(p.getWorld().getName())) sendDisabledConfig(p);
            }
            sender.sendMessage("§cSlipstream ground effect disabled.");
          }
          return true;
        }
        case "reload" -> {
          broadcastConfig();
          sender.sendMessage("§aSlipstream config reloaded and pushed to all online players.");
          return true;
        }
      }
    }
    sender.sendMessage("§eUsage: /slipstream <enable|disable|reload>");
    return true;
  }

  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase("slipstream")) return List.of();
    if (!sender.hasPermission("slipstream.admin")) return List.of();
    if (args.length == 1) {
      return SUBCOMMANDS.stream()
          .filter(s -> s.startsWith(args[0].toLowerCase()))
          .collect(Collectors.toList());
    }
    return List.of();
  }

  void sendConfig(Player player) {
    if (!player.isOnline()) return;
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeDouble(getConfig().getDouble("effect-height", 20.0));
      out.writeDouble(getConfig().getDouble("acceleration", 0.013));
      out.writeDouble(getConfig().getDouble("max-speed", 1.5));
      out.writeDouble(getConfig().getDouble("water-spray-height", 5.0));
      out.writeDouble(getConfig().getDouble("lift-strength", 0.6));
      out.writeDouble(getConfig().getDouble("effect-speed-threshold", 0.3));
      player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
    } catch (IOException ex) {
      getLogger().warning("Failed to send config to " + player.getName() + ": " + ex.getMessage());
    }
  }

  // Sends a no-op config so the Fabric mixin's boost and lift never activate.
  // liftStrength=0 makes liftForce() return 0 immediately (including the anti-gravity term).
  // acceleration=0 kills the horizontal boost. effectSpeedThreshold=1.0 is a belt-and-suspenders
  // guard so the lift block is never even entered at normal flight speeds.
  private void sendDisabledConfig(Player player) {
    if (!player.isOnline()) return;
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeDouble(getConfig().getDouble("effect-height", 20.0));
      out.writeDouble(0.0); // acceleration — no boost
      out.writeDouble(getConfig().getDouble("max-speed", 1.5));
      out.writeDouble(getConfig().getDouble("water-spray-height", 5.0));
      out.writeDouble(0.0); // liftStrength=0 — kills lift AND anti-gravity in liftForce()
      out.writeDouble(1.0); // effectSpeedThreshold — threshold == maxSpeed, belt-and-suspenders
      player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
    } catch (IOException ex) {
      getLogger()
          .warning("Failed to send disabled config to " + player.getName() + ": " + ex.getMessage());
    }
  }

  public void broadcastConfig() {
    reloadConfig();
    if (task != null) task.reload();
    List<String> disabled = getConfig().getStringList("disabled-worlds");
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (disabled.contains(p.getWorld().getName())) continue;
      if (effectEnabled) sendConfig(p);
      else sendDisabledConfig(p);
    }
    getLogger().info("Config rebroadcast to " + Bukkit.getOnlinePlayers().size() + " players.");
  }
}
