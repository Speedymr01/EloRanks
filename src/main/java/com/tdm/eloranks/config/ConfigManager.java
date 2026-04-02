package com.tdm.eloranks.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.tdm.eloranks.EloRanks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private final EloRanks plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Store all config files
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    public ConfigManager(EloRanks plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        // Create or load config.yml
        configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    // Create default config
                    config = plugin.getConfig();
                    setDefaults();
                    saveConfig();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create config.yml: " + e.getMessage());
            }
        } else {
            config = YamlConfiguration.loadConfiguration(configFile);
            
            // Auto-migration: add any missing keys from defaults
            boolean migrated = migrateConfigDefaults();
            if (migrated) {
                plugin.getLogger().info("Config updated with new settings!");
                saveConfig();
            }
        }
        
        configs.put("config", config);
    }
    
    /**
     * Add missing config keys from defaults (auto-migration).
     * Returns true if any keys were added.
     */
    private boolean migrateConfigDefaults() {
        boolean changed = false;
        
        // Save current config values
        Map<String, Object> existingValues = new HashMap<>();
        for (String key : config.getKeys(true)) {
            existingValues.put(key, config.get(key));
        }
        
        // Apply defaults (this sets all expected keys)
        setDefaults();
        
        // Check which keys were missing and restore their values
        for (Map.Entry<String, Object> entry : existingValues.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Only restore if the value wasn't a default that was just added
            // and the key exists in defaults
            if (config.contains(key) && config.get(key).equals(value)) {
                // Value was already in config - good
            }
        }
        
        // Check if we added any new keys (by comparing key counts)
        Set<String> newKeys = new HashSet<>(config.getKeys(true));
        newKeys.removeAll(existingValues.keySet());
        
        if (!newKeys.isEmpty()) {
            plugin.getLogger().info("Added " + newKeys.size() + " new config settings:");
            for (String key : newKeys) {
                plugin.getLogger().info("  + " + key + ": " + config.get(key));
            }
            changed = true;
        }
        
        return changed;
    }

    private void setDefaults() {
        // ============ ELO SETTINGS ============
        config.set("elo.starting", 1000);
        config.set("elo.min", 0);
        config.set("elo.max", 10000);
        
        // ============ K-FACTOR (Base) ============
        config.set("elo.k-factor.win", 32);
        config.set("elo.k-factor.draw", 16);
        
        // ============ DYNAMIC K-FACTOR ============
        config.set("elo.k-factor.dynamic.enabled", true);
        config.set("elo.k-factor.dynamic.by-games.enabled", true);
        config.set("elo.k-factor.dynamic.by-games.threshold", 20);
        config.set("elo.k-factor.dynamic.by-games.new-player-k", 48);
        config.set("elo.k-factor.dynamic.by-elo.enabled", true);
        config.set("elo.k-factor.dynamic.by-elo.threshold-1", 1500);
        config.set("elo.k-factor.dynamic.by-elo.threshold-2", 2000);
        config.set("elo.k-factor.dynamic.by-elo.k-at-1500", 24);
        config.set("elo.k-factor.dynamic.by-elo.k-at-2000", 16);
        
        // ============ PLACEMENT MATCH SETTINGS ============
        config.set("elo.placement.enabled", true);
        config.set("elo.placement.min-players", 5);
        config.set("elo.placement.match-count", 5);
        config.set("elo.placement.k-factor-games", 5);
        config.set("elo.placement.bonus-beating-higher", true);
        
        // ============ RANK SETTINGS ============
        config.set("ranks.percentage", true);
        config.set("ranks.show-top-50", true);
        config.set("ranks.nametag-prefix", true);
        
        // ============ DUEL SETTINGS ============
        config.set("duel.cooldown", 60);
        config.set("duel.request-timeout", 30);
        config.set("duel.arena-world", "duel_arena");
        config.set("duel.allow-spectators", true);
        config.set("duel.spectator-permission", "er.spectate");
        
        // ============ SURRENDER SETTINGS ============
        config.set("surrender.enabled", true);
        config.set("surrender.min-duel-time-seconds", 30);  // Must wait 30 seconds before surrendering
        config.set("surrender.instant-loss", true);  // Surrender = full Elo penalty/reward (no halving)
        
        // ============ MATCHMAKING SETTINGS ============
        config.set("matchmaking.enabled", true);
        config.set("matchmaking.initial-elo-range", 50);
        config.set("matchmaking.range-increase-per-second", 10);
        config.set("matchmaking.max-elo-range", 500);
        config.set("matchmaking.bidirectional-check", true);
        config.set("matchmaking.check-interval-seconds", 1);
        
        // ============ COUNTDOWN SETTINGS ============
        config.set("countdown.teleport-seconds", 5);
        config.set("countdown.duel-start-seconds", 20);
        config.set("countdown.show-title", true);
        config.set("countdown.show-subtitle", true);
        config.set("countdown.show-chat-messages", true);
        
        // REMOVED: countdown.colors.* - colors are hardcoded in CountdownManager
        
        // ============ ARENA SETTINGS ============
        config.set("arena.initial-count", 10);
        config.set("arena.spacing", 100);
        config.set("arena.schematic", "cloudy.schematic");
        config.set("arena.auto-expand", true);
        config.set("arena.expand-count", 5);
        config.set("arena.respawn-delay", 3);
        config.set("arena.load-timeout", 10);
        
        // ============ KIT SETTINGS ============
        config.set("kit.enabled", true);
        config.set("kit.sword", "DIAMOND_SWORD");
        config.set("kit.bow", "BOW");
        config.set("kit.arrows", 64);
        config.set("kit.helmet", "DIAMOND_HELMET");
        config.set("kit.chestplate", "DIAMOND_CHESTPLATE");
        config.set("kit.leggings", "DIAMOND_LEGGINGS");
        config.set("kit.boots", "DIAMOND_BOOTS");
        config.set("kit.offhand", "SHIELD");
        config.set("kit.food", "GOLDEN_APPLE:10");
        config.set("kit.blocks", "COBWEB:16,OAK_PLANKS:64");
        config.set("kit.buckets", "WATER_BUCKET,LAVA_BUCKET");
        
        // ============ POTION EFFECTS (moved to kit.potions) ============
        // REMOVED: effects.* - now configured via kit.potions
        
        // ============ WORLD SETTINGS (managed by WorldManager) ============
        // REMOVED: world.* - handled internally
        
        // ============ GAMEPLAY SETTINGS ============
        config.set("gameplay.fall-damage", false);
        config.set("gameplay.fire-damage", false);
        config.set("gameplay.hunger-depletion", false);
        config.set("gameplay.keep-inventory", true);
        config.set("gameplay.natural-regeneration", false);
        
        // ============ SCOREBOARD SETTINGS ============
        config.set("scoreboard.enabled", true);
        config.set("scoreboard.show-title", true);
        config.set("scoreboard.show-rank", true);
        config.set("scoreboard.show-elo", true);
        config.set("scoreboard.show-world", true);
        config.set("scoreboard.show-opponent-in-duel", true);
        
        // ============ BOSSBAR SETTINGS ============
        config.set("bossbar.enabled", true);
        config.set("bossbar.health-update-interval", 5);
        
        // ============ CHAT MESSAGES ============
        config.set("chat.duel-request", true);
        config.set("chat.duel-start", true);
        config.set("chat.duel-end", true);
        config.set("chat.rank-up", true);
        config.set("chat.matchmaking-search", true);
        config.set("chat.pre-duel-instructions", true);
        
        // ============ MESSAGES ============
        config.set("messages.elo-gain", "&a+%elo% Elo! &7(Rank: #%rank%)");
        config.set("messages.elo-lost", "&c%elo% Elo! &7(Rank: #%rank%)");
        config.set("messages.new-rank", "&6&l★ &aYou are now Rank %rank%! ★");
        config.set("messages.leaderboard-header", "&7=== &eTop Players &7===");
        config.set("messages.duel-started", "&aDuel started! Good luck!");
        config.set("messages.duel-ended-winner", "&aYou won the duel!");
        config.set("messages.duel-ended-loser", "&cYou lost the duel!");
        
        // ============ LEADERBOARD SETTINGS ============
        config.set("leaderboard.entries-per-page", 10);
        config.set("leaderboard.show-rank-change", true);
        config.set("leaderboard.auto-update", true);
        config.set("leaderboard.update-interval", 60);
        
        // ============ DATABASE/STORAGE SETTINGS ============
        config.set("storage.save-interval", 300);
        config.set("storage.async-save", true);
        config.set("storage.backup-enabled", false);
        config.set("storage.backup-interval", 3600);
        
        // ============ DEBUG SETTINGS ============
        config.set("debug-mode", false);
        config.set("debug.show-elo-calculations", false);
        config.set("debug.show-matchmaking", false);
        config.set("debug.show-countdown", false);
    }

    public void saveConfig() {
        if (config == null || configFile == null) return;
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public void createCustomConfig(String name) {
        File file = new File(plugin.getDataFolder(), name + ".yml");
        
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create " + name + ".yml: " + e.getMessage());
            }
        }
        
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        configs.put(name, yaml);
        configFiles.put(name, file);
    }

    public void saveCustomConfig(String name) {
        FileConfiguration yaml = configs.get(name);
        File file = configFiles.get(name);
        
        if (yaml != null && file != null) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + name + ".yml: " + e.getMessage());
            }
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        configs.put("config", config);
    }

    // ============ ELO GETTERS ============
    public int getStartingElo() {
        return config.getInt("elo.starting", 1000);
    }

    public int getMinElo() {
        return config.getInt("elo.min", 0);
    }

    public int getMaxElo() {
        return config.getInt("elo.max", 10000);
    }

    public int getKFactorWin() {
        return config.getInt("elo.k-factor.win", 32);
    }

    public int getKFactorDraw() {
        return config.getInt("elo.k-factor.draw", 16);
    }

    // ============ DYNAMIC K-FACTOR GETTERS ============
    public boolean isDynamicKFactorEnabled() {
        return config.getBoolean("elo.k-factor.dynamic.enabled", true);
    }

    public boolean isDynamicKByGamesEnabled() {
        return config.getBoolean("elo.k-factor.dynamic.by-games.enabled", true);
    }

    public int getDynamicKByGamesThreshold() {
        return config.getInt("elo.k-factor.dynamic.by-games.threshold", 20);
    }

    public int getDynamicKNewPlayer() {
        return config.getInt("elo.k-factor.dynamic.by-games.new-player-k", 48);
    }

    public boolean isDynamicKByEloEnabled() {
        return config.getBoolean("elo.k-factor.dynamic.by-elo.enabled", true);
    }

    public int getDynamicKThreshold1() {
        return config.getInt("elo.k-factor.dynamic.by-elo.threshold-1", 1500);
    }

    public int getDynamicKThreshold2() {
        return config.getInt("elo.k-factor.dynamic.by-elo.threshold-2", 2000);
    }

    public int getDynamicKAt1500() {
        return config.getInt("elo.k-factor.dynamic.by-elo.k-at-1500", 24);
    }

    public int getDynamicKAt2000() {
        return config.getInt("elo.k-factor.dynamic.by-elo.k-at-2000", 16);
    }

    /**
     * Calculate the effective K-factor for a player based on their stats.
     * Uses dynamic K-factor if enabled, otherwise returns base K-factor.
     * 
     * @param playerElo The player's current Elo
     * @param totalMatches Total matches the player has played
     * @param isDraw Whether the match was a draw
     * @return The effective K-factor to use
     */
    public int getEffectiveKFactor(int playerElo, int totalMatches, boolean isDraw) {
        // Return draw K-factor if it's a draw
        if (isDraw) {
            return getKFactorDraw();
        }

        // If dynamic K is disabled, return base K-factor
        if (!isDynamicKFactorEnabled()) {
            return getKFactorWin();
        }

        int kFactor = getKFactorWin();

        // Apply by-games reduction if enabled
        if (isDynamicKByGamesEnabled() && totalMatches < getDynamicKByGamesThreshold()) {
            kFactor = getDynamicKNewPlayer();
        }

        // Apply by-Elo reduction if enabled
        if (isDynamicKByEloEnabled()) {
            if (playerElo >= getDynamicKThreshold2()) {
                kFactor = getDynamicKAt2000();
            } else if (playerElo >= getDynamicKThreshold1()) {
                kFactor = getDynamicKAt1500();
            }
        }

        return kFactor;
    }

    // ============ DUEL GETTERS ============
    public int getDuelCooldown() {
        return config.getInt("duel.cooldown", 60);
    }

    public int getRequestTimeout() {
        return config.getInt("duel.request-timeout", 30);
    }

    public String getArenaWorld() {
        return config.getString("duel.arena-world", "duel_arena");
    }

    // ============ KIT GETTERS ============
    public String getSword() {
        return config.getString("kit.sword", "DIAMOND_SWORD");
    }

    public String getBow() {
        return config.getString("kit.bow", "BOW");
    }

    public int getArrows() {
        return config.getInt("kit.arrows", 64);
    }

    public String getHelmet() {
        return config.getString("kit.helmet", "DIAMOND_HELMET");
    }

    public String getChestplate() {
        return config.getString("kit.chestplate", "DIAMOND_CHESTPLATE");
    }

    public String getLeggings() {
        return config.getString("kit.leggings", "DIAMOND_LEGGINGS");
    }

    public String getBoots() {
        return config.getString("kit.boots", "DIAMOND_BOOTS");
    }

    public String getOffhand() {
        return config.getString("kit.offhand", "SHIELD");
    }

    public String getFood() {
        return config.getString("kit.food", "GOLDEN_APPLE:10");
    }

    public String getBlocks() {
        return config.getString("kit.blocks", "COBWEB:16,OAK_PLANKS:64");
    }

    public String getBuckets() {
        return config.getString("kit.buckets", "WATER_BUCKET,LAVA_BUCKET");
    }

    public String getPotions() {
        return config.getString("kit.potions", "SPEED:2,STRENGTH:2");
    }

    // ============ ARENA GETTERS ============
    public int getInitialArenaCount() {
        return config.getInt("arena.initial-count", 10);
    }

    public int getArenaSpacing() {
        return config.getInt("arena.spacing", 100);
    }

    public String getArenaSchematic() {
        return config.getString("arena.schematic", "cloudy.schematic");
    }

    public boolean isAutoExpandEnabled() {
        return config.getBoolean("arena.auto-expand", true);
    }

    public int getArenaExpandCount() {
        return config.getInt("arena.expand-count", 5);
    }

    // ============ GAMEPLAY GETTERS ============
    public boolean isFallDamageEnabled() {
        return config.getBoolean("gameplay.fall-damage", false);
    }

    public boolean isFireDamageEnabled() {
        return config.getBoolean("gameplay.fire-damage", false);
    }

    public boolean isHungerDepletionEnabled() {
        return config.getBoolean("gameplay.hunger-depletion", false);
    }

    public boolean isKeepInventoryEnabled() {
        return config.getBoolean("gameplay.keep-inventory", true);
    }

    public boolean isNaturalRegenerationEnabled() {
        return config.getBoolean("gameplay.natural-regeneration", false);
    }

    // ============ CHAT GETTERS ============
    public boolean isDuelRequestChatEnabled() {
        return config.getBoolean("chat.duel-request", true);
    }

    public boolean isDuelStartChatEnabled() {
        return config.getBoolean("chat.duel-start", true);
    }

    public boolean isDuelEndChatEnabled() {
        return config.getBoolean("chat.duel-end", true);
    }

    public boolean isRankUpChatEnabled() {
        return config.getBoolean("chat.rank-up", true);
    }

    // ============ LEADERBOARD GETTERS ============
    public int getLeaderboardEntriesPerPage() {
        return config.getInt("leaderboard.entries-per-page", 10);
    }
    
    // ============ DEBUG GETTERS ============
    public boolean isDebugMode() {
        return config.getBoolean("debug-mode", false);
    }
    
    // ============ PLACEMENT MATCH GETTERS ============
    public boolean isPlacementEnabled() {
        return config.getBoolean("elo.placement.enabled", true);
    }
    
    public int getPlacementMinPlayers() {
        return config.getInt("elo.placement.min-players", 5);
    }
    
    public int getPlacementMatchCount() {
        return config.getInt("elo.placement.match-count", 5);
    }
    
    public int getPlacementKFactorGames() {
        return config.getInt("elo.placement.k-factor-games", 3);
    }
    
    public boolean isPlacementBonusEnabled() {
        return config.getBoolean("elo.placement.bonus-beating-higher", true);
    }
    
    // ============ RANK GETTERS ============
    public boolean isShowTop50() {
        return config.getBoolean("ranks.show-top-50", true);
    }
    
    public boolean isNametagPrefixEnabled() {
        return config.getBoolean("ranks.nametag-prefix", true);
    }
    
    // ============ SURRENDER GETTERS ============
    public boolean isSurrenderEnabled() {
        return config.getBoolean("surrender.enabled", true);
    }
    
    public int getSurrenderMinDuelTimeSeconds() {
        return config.getInt("surrender.min-duel-time-seconds", 30);
    }
    
    public boolean isSurrenderInstantLoss() {
        return config.getBoolean("surrender.instant-loss", true);
    }
    
    // ============ MATCHMAKING GETTERS ============
    public boolean isMatchmakingEnabled() {
        return config.getBoolean("matchmaking.enabled", true);
    }
    
    public int getMatchmakingInitialRange() {
        return config.getInt("matchmaking.initial-elo-range", 50);
    }
    
    public int getMatchmakingRangeIncrease() {
        return config.getInt("matchmaking.range-increase-per-second", 10);
    }
    
    public int getMatchmakingMaxRange() {
        return config.getInt("matchmaking.max-elo-range", 500);
    }
    
    public boolean isMatchmakingBidirectionalCheck() {
        return config.getBoolean("matchmaking.bidirectional-check", true);
    }
    
    public int getMatchmakingCheckInterval() {
        return config.getInt("matchmaking.check-interval-seconds", 1);
    }
    
    // ============ COUNTDOWN GETTERS ============
    public int getTeleportCountdownSeconds() {
        return config.getInt("countdown.teleport-seconds", 5);
    }
    
    public int getDuelStartCountdownSeconds() {
        return config.getInt("countdown.duel-start-seconds", 20);
    }
    
    public boolean isCountdownTitleEnabled() {
        return config.getBoolean("countdown.show-title", true);
    }
    
    public boolean isCountdownSubtitleEnabled() {
        return config.getBoolean("countdown.show-subtitle", true);
    }
    
    public boolean isCountdownChatEnabled() {
        return config.getBoolean("countdown.show-chat-messages", true);
    }
    
    // ============ ARENA GETTERS ============
    public int getArenaLoadTimeout() {
        return config.getInt("arena.load-timeout", 10);
    }
    
    // ============ SCOREBOARD GETTERS ============
    public boolean isScoreboardEnabled() {
        return config.getBoolean("scoreboard.enabled", true);
    }
    
    public boolean isScoreboardTitleEnabled() {
        return config.getBoolean("scoreboard.show-title", true);
    }
    
    public boolean isScoreboardTitleAnimated() {
        return config.getBoolean("scoreboard.title-animation", true);
    }
    
    public boolean isScoreboardRankEnabled() {
        return config.getBoolean("scoreboard.show-rank", true);
    }
    
    public boolean isScoreboardEloEnabled() {
        return config.getBoolean("scoreboard.show-elo", true);
    }
    
    public boolean isScoreboardWorldEnabled() {
        return config.getBoolean("scoreboard.show-world", true);
    }
    
    public boolean isScoreboardOpponentEnabled() {
        return config.getBoolean("scoreboard.show-opponent-in-duel", true);
    }
    
    public int getScoreboardUpdateInterval() {
        return config.getInt("scoreboard.update-interval", 2);
    }
    
    // ============ BOSSBAR GETTERS ============
    public boolean isBossbarEnabled() {
        return config.getBoolean("bossbar.enabled", true);
    }
    
    public boolean isBossbarHealthEnabled() {
        return config.getBoolean("bossbar.show-opponent-health", true);
    }
    
    public int getBossbarHealthUpdateInterval() {
        return config.getInt("bossbar.health-update-interval", 5);
    }
    
    // ============ CHAT GETTERS ============
    public boolean isMatchmakingChatEnabled() {
        return config.getBoolean("chat.matchmaking-search", true);
    }
    
    public boolean isPreDuelInstructionsEnabled() {
        return config.getBoolean("chat.pre-duel-instructions", true);
    }
    
    // ============ MESSAGE GETTERS ============
    public String getEloGainMessage() {
        return config.getString("messages.elo-gain", "&a+%elo% Elo! &7(Rank: #%rank%)");
    }
    
    public String getEloLostMessage() {
        return config.getString("messages.elo-lost", "&c%elo% Elo! &7(Rank: #%rank%)");
    }
    
    public String getNewRankMessage() {
        return config.getString("messages.new-rank", "&6&l★ &aYou are now Rank %rank%! ★");
    }
    
    public String getLeaderboardHeaderMessage() {
        return config.getString("messages.leaderboard-header", "&7=== &eTop Players &7===");
    }
    
    public String getDuelStartedMessage() {
        return config.getString("messages.duel-started", "&aDuel started! Good luck!");
    }
    
    public String getDuelEndedWinnerMessage() {
        return config.getString("messages.duel-ended-winner", "&aYou won the duel!");
    }
    
    public String getDuelEndedLoserMessage() {
        return config.getString("messages.duel-ended-loser", "&cYou lost the duel!");
    }
    
    // ============ LEADERBOARD GETTERS ============
    public boolean isLeaderboardAutoUpdate() {
        return config.getBoolean("leaderboard.auto-update", true);
    }
    
    public int getLeaderboardUpdateInterval() {
        return config.getInt("leaderboard.update-interval", 60);
    }
    
    // ============ STORAGE GETTERS ============
    public boolean isBackupEnabled() {
        return config.getBoolean("storage.backup-enabled", false);
    }
    
    public int getBackupInterval() {
        return config.getInt("storage.backup-interval", 3600);
    }
    
    // ============ DEBUG GETTERS ============
    public boolean isDebugEloCalculations() {
        return config.getBoolean("debug.show-elo-calculations", false);
    }
    
    public boolean isDebugMatchmaking() {
        return config.getBoolean("debug.show-matchmaking", false);
    }
    
    public boolean isDebugCountdown() {
        return config.getBoolean("debug.show-countdown", false);
    }
}