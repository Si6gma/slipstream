/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 */
package com.si6gma.slipstream.paper;

import com.si6gma.slipstream.paper.SlipstreamPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;

final class UpdateChecker {
    private static final String API_URL = "https://api.modrinth.com/v2/project/elytra-slipstream/version";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version_number\":\\s*\"([^\"]+)\"");

    private UpdateChecker() {
    }

    static void checkAsync(SlipstreamPlugin plugin) {
        String current = plugin.getDescription().getVersion();
        String userAgent = "slipstream-paper/" + current + " (github.com/Si6gma/slipstream)";
        plugin.getServer().getScheduler().runTaskAsynchronously((Plugin)plugin, () -> {
            try {
                String body;
                HttpURLConnection conn = (HttpURLConnection)URI.create(API_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() != 200) {
                    return;
                }
                try (InputStream in = conn.getInputStream();){
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Matcher m = VERSION_PATTERN.matcher(body);
                if (!m.find()) {
                    return;
                }
                String latest = m.group(1);
                if (!latest.equals(current)) {
                    plugin.getLogger().warning("Slipstream " + latest + " is available (running " + current + "). https://modrinth.com/mod/slipstream");
                }
            }
            catch (IOException e) {
                plugin.getLogger().fine("Update check failed: " + e.getMessage());
            }
        });
    }
}
