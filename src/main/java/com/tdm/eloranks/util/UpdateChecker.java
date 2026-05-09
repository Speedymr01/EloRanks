package com.tdm.eloranks.util;

import com.tdm.eloranks.EloRanks;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * UpdateChecker - Checks for plugin updates from Modrinth
 * 
 * Fetches the latest version from the Modrinth API and compares it
 * to the plugin's current version. Logs a warning if an update is available.
 */
public class UpdateChecker {

    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/%s/version";
    private static final String MODRINTH_DOWNLOAD_URL = "https://modrinth.com/plugin/%s";

    private final JavaPlugin plugin;
    private final String projectId;
    private final HttpClient httpClient;

    /**
     * Creates a new UpdateChecker instance
     * 
     * @param plugin    The plugin instance
     * @param projectId The Modrinth project ID (base62)
     */
    public UpdateChecker(JavaPlugin plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Checks for updates asynchronously
     */
    public void check() {
        // Run asynchronously to avoid blocking the server thread
        httpClient.sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(String.format(MODRINTH_API_URL, projectId)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                try {
                    JSONArray versions = new JSONArray(response.body());
                    if (versions.length() > 0) {
                        String latestVersion = versions.getJSONObject(0).getString("version_number");
                        String currentVersion = plugin.getDescription().getVersion();
                        
                        if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                            plugin.getLogger().warning("§e═══════════════════════════════════════");
                            plugin.getLogger().warning("§e  A new update is available!");
                            plugin.getLogger().warning("§e  Current version: §c" + currentVersion);
                            plugin.getLogger().warning("§e  Latest version: §a" + latestVersion);
                            plugin.getLogger().warning("§e  Download: §b" + String.format(MODRINTH_DOWNLOAD_URL, projectId));
                            plugin.getLogger().warning("§e═══════════════════════════════════════");
                        } else {
                            plugin.getLogger().info("§aPlugin is up to date! (v" + latestVersion + ")");
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse Modrinth version data: " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Failed to check for updates (HTTP " + response.statusCode() + ")");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Error checking for updates: " + ex.getMessage());
            return null;
        });
    }
}