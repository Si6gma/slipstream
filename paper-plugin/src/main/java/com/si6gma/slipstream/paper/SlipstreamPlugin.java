package com.si6gma.slipstream.paper;

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
    Player player = e.getPlayer();
    if (player.getListeningPluginChannels().contains(CHANNEL)) {
      Bukkit.getScheduler().runTaskLater(this, () -> sendConfigForWorld(player, player.getWorld().getName()), 2L);
    }
  }

  @EventHandler
  public void onChannelRegister(PlayerRegisterChannelEvent e) {
    if (!e.getChannel().equals(CHANNEL)) return;
    if (!getConfig().getBoolean("override-clients", true)) return;
    Player player = e.getPlayer();
    sendConfigForWorld(player, player.getWorld().getName());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent e) {
    if (!getConfig().getBoolean("override-clients", true)) return;
    Player player = e.getPlayer();
    if (!player.getListeningPluginChannels().contains(CHANNEL)) return;
    sendConfigForWorld(player, player.getWorld().getName());
  }

  private void sendConfigForWorld(Player player, String worldName) {
    if (!player.isOnline()) return;
    if (getConfig().getStringList("disabled-worlds").contains(worldName)) {
      sendDisabledConfig(player);
    } else if (effectEnabled) {
      sendConfig(player);
    } else {
      sendDisabledConfig(player);
    }
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
          .toList();
    }
    return List.of();
  }

  // 6 doubles × 8 bytes = 48 bytes; pre-sized to avoid internal ByteArrayOutputStream resize.
  // Field order must match ServerConfigOverride.apply() on the Fabric side.
  private static byte[] serializePayload(
      double effectHeight, double acceleration, double maxSpeed,
      double waterSprayHeight, double liftStrength, double speedThreshold) throws IOException {
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
    if (!player.isOnline()) return;
    try {
      player.sendPluginMessage(this, CHANNEL, serializePayload(
          getConfig().getDouble("effect-height", 20.0),
          getConfig().getDouble("acceleration", 0.005),
          getConfig().getDouble("max-speed", 1.5),
          getConfig().getDouble("water-spray-height", 5.0),
          getConfig().getDouble("lift-strength", 0.6),
          getConfig().getDouble("effect-speed-threshold", 0.3)));
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
      player.sendPluginMessage(this, CHANNEL, serializePayload(
          getConfig().getDouble("effect-height", 20.0),
          0.0,
          getConfig().getDouble("max-speed", 1.5),
          getConfig().getDouble("water-spray-height", 5.0),
          0.0,
          1.0));
    } catch (IOException ex) {
      getLogger()
          .warning("Failed to send disabled config to " + player.getName() + ": " + ex.getMessage());
    }
  }

  public void broadcastConfig() {
    reloadConfig();
    if (task != null) task.reload();
    List<String> disabled = getConfig().getStringList("disabled-worlds");
    try {
      // Build the payload once — all players in the same state receive the same bytes.
      byte[] payload = effectEnabled
          ? serializePayload(
              getConfig().getDouble("effect-height", 20.0),
              getConfig().getDouble("acceleration", 0.005),
              getConfig().getDouble("max-speed", 1.5),
              getConfig().getDouble("water-spray-height", 5.0),
              getConfig().getDouble("lift-strength", 0.6),
              getConfig().getDouble("effect-speed-threshold", 0.3))
          : serializePayload(
              getConfig().getDouble("effect-height", 20.0),
              0.0,
              getConfig().getDouble("max-speed", 1.5),
              getConfig().getDouble("water-spray-height", 5.0),
              0.0,
              1.0);
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (!disabled.contains(p.getWorld().getName()) && p.isOnline())
          p.sendPluginMessage(this, CHANNEL, payload);
      }
    } catch (IOException ex) {
      getLogger().warning("Failed to serialize config for broadcast: " + ex.getMessage());
    }
    getLogger().info("Config rebroadcast to " + Bukkit.getOnlinePlayers().size() + " players.");
  }
}
