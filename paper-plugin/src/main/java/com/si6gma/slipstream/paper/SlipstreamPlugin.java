package com.si6gma.slipstream.paper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class SlipstreamPlugin extends JavaPlugin implements Listener, TabCompleter {

  static final String CHANNEL = "slipstream:server_config";
  private static final String HELLO_CHANNEL = "slipstream:hello";
  private static final List<String> SUBCOMMANDS = List.of("enable", "disable", "reload");

  private GroundEffectTask task;
  private final Random durabilityRandom = new Random();
  private boolean effectEnabled = true;
  private int handshakeTimeoutTicks = 60;
  private VersionPolicy.EnforcementPolicy versionEnforcement = VersionPolicy.EnforcementPolicy.DISABLE;

  // Final classification per player for the session, populated once a hello arrives or the
  // handshake deadline passes. A vanilla player (never listening on CHANNEL) is never added.
  private final Map<UUID, VersionPolicy.ClientState> handshakeState = new HashMap<>();

  // Pending deadline tasks, keyed by player, so classifying a player early (hello arrives) can
  // cancel it and so a disconnect can clean it up. Emptied when a player is classified or leaves.
  private final Map<UUID, BukkitTask> handshakeDeadlines = new HashMap<>();

  @Override
  public void onEnable() {
    saveDefaultConfig();
    effectEnabled = getConfig().getBoolean("effect-enabled", true);
    handshakeTimeoutTicks = getConfig().getInt("handshake-timeout-ticks", 60);
    versionEnforcement = parseEnforcementPolicy(getConfig().getString("version-enforcement", "disable"));
    task = new GroundEffectTask(this);
    task.runTaskTimer(this, 0L, 1L);
    getServer().getPluginManager().registerEvents(this, this);
    getServer().getPluginManager().registerEvents(task, this);
    getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
    getServer().getMessenger().registerIncomingPluginChannel(this, HELLO_CHANNEL, this::onHelloMessage);
    getCommand("slipstream").setTabCompleter(this);
    UpdateChecker.checkAsync(this);
    getLogger().info("Slipstream enabled.");
  }

  @Override
  public void onDisable() {
    if (task != null) {
      task.cancel();
      task.cleanup();
    }
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    getServer().getMessenger().unregisterIncomingPluginChannel(this, HELLO_CHANNEL);
    handshakeDeadlines.values().forEach(BukkitTask::cancel);
    handshakeDeadlines.clear();
    handshakeState.clear();
  }

  public boolean isEffectEnabled() {
    return effectEnabled;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    beginHandshake(e.getPlayer());
    if (!getConfig().getBoolean("override-clients", true)) return;
    Player player = e.getPlayer();
    if (player.getListeningPluginChannels().contains(CHANNEL)) {
      Bukkit.getScheduler().runTaskLater(this, () -> sendConfigForWorld(player, player.getWorld().getName()), 2L);
    }
  }

  /**
   * Drafting wears your elytra out more slowly, scaled by how squarely you sit in the wake. It
   * gives the mechanic a payoff on a survival server where nobody is racing anybody.
   *
   * <p>Done through {@link PlayerItemDamageEvent} rather than a mixin. The vanilla cost is one
   * point every twenty ticks from inside {@code updateFallFlying}, and redirecting that call site
   * is exactly the kind of fragile injection this mod already has too much of.
   *
   * <p>The strength is the server's own estimate and never a number the client sent. A client
   * able to report its own draft strength would simply always claim a perfect one and never wear
   * out an elytra again.
   */
  @EventHandler
  public void onItemDamage(PlayerItemDamageEvent e) {
    if (e.getItem().getType() != Material.ELYTRA) return;
    Player player = e.getPlayer();
    if (!player.isGliding()) return;
    if (!getConfig().getBoolean("drafting-saves-durability", true)) return;
    double strength = task.draftStrengthFor(player.getUniqueId());
    if (DraftEstimate.skipDamage(strength, durabilityRandom.nextDouble())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    BukkitTask deadline = handshakeDeadlines.remove(id);
    if (deadline != null) deadline.cancel();
    handshakeState.remove(id);
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
    if (!isVersionGateOpen(player)) return;
    // The payload is what grants the client permission to move itself faster, so it is also the
    // right place to honour an anticheat exemption. Default true, so a server that never touches
    // permissions behaves exactly as before; an operator who exempts only some players revokes it
    // for the rest and those clients are told to switch the effects off rather than left silent.
    if (!player.hasPermission("slipstream.use")) {
      sendDisabledConfig(player);
      return;
    }
    if (getConfig().getStringList("disabled-worlds").contains(worldName)) {
      sendDisabledConfig(player);
    } else if (effectEnabled) {
      sendConfig(player);
    } else {
      sendDisabledConfig(player);
    }
  }

  /**
   * Whether a config send may proceed for this player. Under {@code off} every send goes through,
   * restoring pre-handshake behaviour exactly; otherwise a send requires having classified the
   * player as compatible.
   */
  private boolean isVersionGateOpen(Player player) {
    if (versionEnforcement == VersionPolicy.EnforcementPolicy.OFF) return true;
    return handshakeState.get(player.getUniqueId()) == VersionPolicy.ClientState.COMPATIBLE;
  }

  /**
   * Starts tracking a joining player's handshake, unless they are vanilla (never announced
   * {@link #CHANNEL}) or already classified because their hello arrived before this ran.
   */
  private void beginHandshake(Player player) {
    UUID id = player.getUniqueId();
    if (handshakeState.containsKey(id)) return;
    if (!player.getListeningPluginChannels().contains(CHANNEL)) return;
    BukkitTask deadline =
        Bukkit.getScheduler()
            .runTaskLater(this, () -> onHandshakeDeadline(id), handshakeTimeoutTicks);
    handshakeDeadlines.put(id, deadline);
  }

  private void onHelloMessage(String channel, Player player, byte[] message) {
    UUID id = player.getUniqueId();
    // Classification happens once: a second hello is ignored.
    if (handshakeState.containsKey(id)) return;
    BukkitTask deadline = handshakeDeadlines.remove(id);
    if (deadline != null) deadline.cancel();
    HelloCodec.Hello hello = HelloCodec.decode(message);
    VersionPolicy.ClientState state =
        hello.protocolVersion() == SlipstreamProtocol.VERSION
            ? VersionPolicy.ClientState.COMPATIBLE
            : VersionPolicy.ClientState.MISMATCHED;
    classifyAndAct(player, state, hello.protocolVersion());
  }

  private void onHandshakeDeadline(UUID id) {
    handshakeDeadlines.remove(id);
    if (handshakeState.containsKey(id)) return; // hello arrived just as the deadline fired
    Player player = Bukkit.getPlayer(id);
    if (player == null || !player.isOnline()) return;
    classifyAndAct(player, VersionPolicy.ClientState.LEGACY, HelloCodec.INVALID_PROTOCOL_VERSION);
  }

  private void classifyAndAct(Player player, VersionPolicy.ClientState state, int clientProtocol) {
    handshakeState.put(player.getUniqueId(), state);
    VersionPolicy.Action action = VersionPolicy.decide(state, versionEnforcement);
    switch (action) {
      case SEND_CONFIG -> sendConfigForWorld(player, player.getWorld().getName());
      case WITHHOLD_AND_MESSAGE -> player.sendMessage(disableMessage(clientProtocol));
      case KICK -> player.kickPlayer(kickMessage(clientProtocol));
      case DO_NOTHING -> {}
    }
  }

  private VersionPolicy.EnforcementPolicy parseEnforcementPolicy(String value) {
    return switch (value) {
      case "off" -> VersionPolicy.EnforcementPolicy.OFF;
      case "disable" -> VersionPolicy.EnforcementPolicy.DISABLE;
      case "kick" -> VersionPolicy.EnforcementPolicy.KICK;
      default -> {
        getLogger()
            .warning("Unrecognised version-enforcement '" + value + "', falling back to disable.");
        yield VersionPolicy.EnforcementPolicy.DISABLE;
      }
    };
  }

  /** The client's protocol is unknown when it never reported one, whether legacy or malformed. */
  private static String reasonSentence(int clientProtocol) {
    if (clientProtocol == HelloCodec.INVALID_PROTOCOL_VERSION) {
      return "Your version is too old to report its protocol.";
    }
    return "This server runs protocol "
        + SlipstreamProtocol.VERSION
        + " and your version speaks protocol "
        + clientProtocol
        + ".";
  }

  private static String disableMessage(int clientProtocol) {
    return "Slipstream effects are disabled here. "
        + reasonSentence(clientProtocol)
        + " Update Slipstream to use it on this server.";
  }

  private static String kickMessage(int clientProtocol) {
    return "Slipstream effects are disabled here. "
        + reasonSentence(clientProtocol)
        + " Update Slipstream to join this server.";
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

  /** Drafting values as configured. Defaults mirror the mod's own. */
  private PayloadCodec.DraftValues draftFromConfig() {
    return new PayloadCodec.DraftValues(
        getConfig().getBoolean("drafting-enabled", true),
        getConfig().getDouble("draft-acceleration", 0.08),
        getConfig().getDouble("draft-speed-multiplier", 1.0),
        getConfig().getDouble("draft-pull-strength", 0.25),
        getConfig().getDouble("draft-release-angle", 35.0),
        getConfig().getDouble("wake-base-radius", 1.5),
        getConfig().getDouble("wake-spread-rate", 1.2),
        getConfig().getInt("wake-lifetime-ticks", 60),
        getConfig().getInt("wake-sample-interval-ticks", 2),
        getConfig().getDouble("draft-leader-bonus-per-drafter", 0.15),
        getConfig().getInt("draft-leader-bonus-max-drafters", 3),
        getConfig().getBoolean("draft-camera-assist", true),
        getConfig().getDouble("draft-camera-assist-strength", 0.5));
  }


  void sendConfig(Player player) {
    if (!player.isOnline()) return;
    // Under server authority the plugin applies the ground effect itself with setVelocity, so a
    // modded client must not also apply its own or the two stack. Zeroing acceleration and lift
    // says exactly that within the existing wire format, with no protocol bump: liftForce returns
    // zero immediately at liftStrength 0, including its antigravity term, and boostDelta
    // multiplies by acceleration.
    //
    // Everything else is sent unchanged, so a modded client keeps drafting, the camera assist and
    // its particles. Drafting stays client applied either way; only the ground effect moves.
    boolean authoritative = getConfig().getBoolean("server-authoritative", true);
    try {
      player.sendPluginMessage(this, CHANNEL, PayloadCodec.serialize(
          getConfig().getDouble("effect-height", 20.0),
          authoritative ? 0.0 : getConfig().getDouble("acceleration", 0.005),
          getConfig().getDouble("max-speed", 1.5),
          getConfig().getDouble("water-spray-height", 5.0),
          authoritative ? 0.0 : getConfig().getDouble("lift-strength", 0.6),
          getConfig().getDouble("effect-speed-threshold", 0.3),
          draftFromConfig()));
    } catch (IOException ex) {
      getLogger().warning("Failed to send config to " + player.getName() + ": " + ex.getMessage());
    }
  }

  // Sends a no-op config so the Fabric mixin's boost and lift never activate.
  // liftStrength=0 makes liftForce() return 0 immediately (including the antigravity term).
  // acceleration=0 kills the horizontal boost. effectSpeedThreshold=1.0 is a belt and suspenders
  // guard so the lift block is never even entered at normal flight speeds.
  private void sendDisabledConfig(Player player) {
    if (!player.isOnline()) return;
    try {
      player.sendPluginMessage(this, CHANNEL, PayloadCodec.serialize(
          getConfig().getDouble("effect-height", 20.0),
          0.0,
          getConfig().getDouble("max-speed", 1.5),
          getConfig().getDouble("water-spray-height", 5.0),
          0.0,
          1.0,
          PayloadCodec.draftDisabled()));
    } catch (IOException ex) {
      getLogger()
          .warning("Failed to send disabled config to " + player.getName() + ": " + ex.getMessage());
    }
  }

  public void broadcastConfig() {
    reloadConfig();
    effectEnabled = getConfig().getBoolean("effect-enabled", true);
    handshakeTimeoutTicks = getConfig().getInt("handshake-timeout-ticks", 60);
    versionEnforcement = parseEnforcementPolicy(getConfig().getString("version-enforcement", "disable"));
    if (task != null) task.reload();
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (p.isOnline() && p.getListeningPluginChannels().contains(CHANNEL))
        sendConfigForWorld(p, p.getWorld().getName());
    }
    getLogger().info("Config rebroadcast to " + Bukkit.getOnlinePlayers().size() + " players.");
  }
}
