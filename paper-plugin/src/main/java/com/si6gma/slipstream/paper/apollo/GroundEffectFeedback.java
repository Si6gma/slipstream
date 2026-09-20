/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.lunarclient.apollo.Apollo
 *  com.lunarclient.apollo.event.EventBus
 *  com.lunarclient.apollo.event.player.ApolloRegisterPlayerEvent
 *  com.lunarclient.apollo.mods.impl.ModFov
 *  com.lunarclient.apollo.module.ApolloModule
 *  com.lunarclient.apollo.module.modsetting.ModSettingModule
 *  com.lunarclient.apollo.module.notification.Notification
 *  com.lunarclient.apollo.module.notification.NotificationModule
 *  com.lunarclient.apollo.module.vignette.Vignette
 *  com.lunarclient.apollo.module.vignette.VignetteModule
 *  com.lunarclient.apollo.option.Option
 *  com.lunarclient.apollo.option.Options
 *  com.lunarclient.apollo.player.ApolloPlayer
 *  com.lunarclient.apollo.recipients.Recipients
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.si6gma.slipstream.paper.apollo;

import com.lunarclient.apollo.Apollo;
import com.lunarclient.apollo.event.EventBus;
import com.lunarclient.apollo.event.player.ApolloRegisterPlayerEvent;
import com.lunarclient.apollo.mods.impl.ModFov;
import com.lunarclient.apollo.module.ApolloModule;
import com.lunarclient.apollo.module.modsetting.ModSettingModule;
import com.lunarclient.apollo.module.notification.Notification;
import com.lunarclient.apollo.module.notification.NotificationModule;
import com.lunarclient.apollo.module.vignette.Vignette;
import com.lunarclient.apollo.module.vignette.VignetteModule;
import com.lunarclient.apollo.option.Options;
import com.lunarclient.apollo.player.ApolloPlayer;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class GroundEffectFeedback
implements ClientFeedback {
    private static final Component NOTIFICATION_TITLE = Component.text("Slipstream", NamedTextColor.AQUA, TextDecoration.BOLD);
    private static final Component NOTIFICATION_BODY = Component.text("Ground effect engaged. Skim low to build speed.", NamedTextColor.GRAY);
    private static final Duration NOTIFICATION_TIME = Duration.ofSeconds(4L);
    private static final int FAILURE_LIMIT = 3;
    private final JavaPlugin plugin;
    private final FirstActivationStore seen;
    private final Map<UUID, VignetteThrottle> throttles = new HashMap<>();
    private final Consumer<ApolloRegisterPlayerEvent> registerHandler = this::onApolloRegister;
    private boolean vignetteEnabled;
    private boolean notificationEnabled;
    private boolean modSettingsEnabled;
    private String vignetteTexture;
    private double vignetteMaxOpacity;
    private float flyingFovModifier;
    private int consecutiveFailures;
    private boolean inert;

    GroundEffectFeedback(JavaPlugin plugin) {
        this.plugin = plugin;
        this.seen = new FirstActivationStore(plugin.getDataFolder().toPath().resolve("lunar-seen.txt"));
        this.reload();
        EventBus.getBus().register(ApolloRegisterPlayerEvent.class, this.registerHandler);
    }

    @Override
    public void reload() {
        boolean vignetteWasEnabled = this.vignetteEnabled;
        this.vignetteEnabled = this.plugin.getConfig().getBoolean("apollo.vignette.enabled", true);
        if (vignetteWasEnabled && !this.vignetteEnabled) {
            this.clearDisplayedVignettes();
        }
        this.vignetteTexture = this.plugin.getConfig().getString("apollo.vignette.texture", "misc/vignette.png");
        this.vignetteMaxOpacity = GroundEffectFeedback.clamp01(this.plugin.getConfig().getDouble("apollo.vignette.max-opacity", 0.5));
        this.notificationEnabled = this.plugin.getConfig().getBoolean("apollo.notification.enabled", true);
        this.modSettingsEnabled = this.plugin.getConfig().getBoolean("apollo.mod-settings.enabled", false);
        this.flyingFovModifier = (float)this.plugin.getConfig().getDouble("apollo.mod-settings.flying-fov-modifier", 1.2);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public boolean isActive() {
        return !this.inert && (this.vignetteEnabled || this.notificationEnabled);
    }

    @Override
    public void effectActive(Player player, double proximity, long tick) {
        if (this.inert) {
            return;
        }
        UUID id = player.getUniqueId();
        Optional<ApolloPlayer> lunar = this.lunarPlayer(id);
        if (lunar.isEmpty()) {
            return;
        }
        ApolloPlayer apolloPlayer = lunar.get();
        if (this.notificationEnabled && this.seen.markIfFirst(id)) {
            this.notifyFirstActivation(apolloPlayer);
            this.flushSeenAsync();
        }
        if (!this.vignetteEnabled) {
            return;
        }
        float opacity = VignetteThrottle.opacity(proximity, this.vignetteMaxOpacity);
        VignetteThrottle throttle = this.throttles.computeIfAbsent(id, unused -> new VignetteThrottle());
        if (!throttle.shouldSend(opacity, tick)) {
            return;
        }
        VignetteModule module = this.module(VignetteModule.class);
        if (module == null) {
            return;
        }
        this.run(() -> module.displayVignette(apolloPlayer, Vignette.builder().resourceLocation(this.vignetteTexture).opacity(opacity).build()));
    }

    @Override
    public void effectEnded(UUID playerId) {
        if (this.inert) {
            return;
        }
        VignetteThrottle throttle = this.throttles.remove(playerId);
        if (throttle == null || !this.vignetteEnabled) {
            return;
        }
        VignetteModule module = this.module(VignetteModule.class);
        if (module == null) {
            return;
        }
        this.lunarPlayer(playerId).ifPresent(p -> this.run(() -> module.resetVignette(p)));
    }

    @Override
    public void playerGone(UUID playerId) {
        this.throttles.remove(playerId);
    }

    private void clearDisplayedVignettes() {
        if (this.throttles.isEmpty() || this.inert) {
            return;
        }
        VignetteModule module = this.module(VignetteModule.class);
        if (module != null) {
            for (UUID id : this.throttles.keySet()) {
                this.lunarPlayer(id).ifPresent(p -> this.run(() -> module.resetVignette(p)));
            }
        }
        this.throttles.clear();
    }

    @Override
    public void shutdown() {
        try {
            EventBus.getBus().unregister(ApolloRegisterPlayerEvent.class, this.registerHandler);
        }
        catch (Throwable t) {
            this.plugin.getLogger().fine("Apollo event bus already unavailable during shutdown: " + String.valueOf(t));
        }
        this.throttles.clear();
        this.flushSeen();
    }

    private void onApolloRegister(ApolloRegisterPlayerEvent event) {
        if (this.inert || !this.modSettingsEnabled) {
            return;
        }
        ModSettingModule module = this.module(ModSettingModule.class);
        if (module == null) {
            return;
        }
        ApolloPlayer apolloPlayer = event.getPlayer();
        this.run(() -> {
            Options options = module.getOptions();
            options.set(apolloPlayer, ModFov.ENABLED, true);
            options.set(apolloPlayer, ModFov.DYNAMIC_FLYING, true);
            options.set(apolloPlayer, ModFov.FLYING_MODIFIER, Float.valueOf(this.flyingFovModifier));
        });
    }

    private void notifyFirstActivation(ApolloPlayer apolloPlayer) {
        NotificationModule module = this.module(NotificationModule.class);
        if (module == null) {
            return;
        }
        this.run(() -> module.displayNotification(apolloPlayer, Notification.builder().titleComponent(NOTIFICATION_TITLE).descriptionComponent(NOTIFICATION_BODY).displayTime(NOTIFICATION_TIME).build()));
    }

    private Optional<ApolloPlayer> lunarPlayer(UUID playerId) {
        try {
            return Apollo.getPlayerManager().getPlayer(playerId);
        }
        catch (Throwable t) {
            this.recordFailure("looking up a Lunar player", t);
            return Optional.empty();
        }
    }

    private <T extends ApolloModule> T module(Class<T> type) {
        try {
            if (!Apollo.getModuleManager().isEnabled(type)) {
                return null;
            }
            return (T)Apollo.getModuleManager().getModule(type);
        }
        catch (Throwable t) {
            this.recordFailure("resolving the " + type.getSimpleName(), t);
            return null;
        }
    }

    private void run(Runnable action) {
        try {
            action.run();
            this.consecutiveFailures = 0;
        }
        catch (Throwable t) {
            this.recordFailure("sending to Lunar Client", t);
        }
    }

    private void recordFailure(String what, Throwable t) {
        if (this.inert) {
            return;
        }
        if (++this.consecutiveFailures < 3) {
            return;
        }
        this.inert = true;
        this.plugin.getLogger().log(Level.WARNING, "Apollo hook failed 3 times while " + what + "; Lunar Client feedback is off until the next restart.", t);
    }

    private void flushSeenAsync() {
        if (!this.seen.isDirty()) {
            return;
        }
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, this::flushSeen);
    }

    private void flushSeen() {
        try {
            this.seen.save();
        }
        catch (IOException ex) {
            this.plugin.getLogger().warning("Failed to save lunar-seen.txt: " + ex.getMessage());
        }
    }
}
