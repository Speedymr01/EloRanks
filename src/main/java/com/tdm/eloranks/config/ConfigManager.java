package com.tdm.eloranks.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.tdm.eloranks.EloRanks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

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
        }
        
        configs.put("config", config);
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
        
        // ============ RANK SETTINGS ============
        config.set("ranks.percentage", true);
        
        // ============ DUEL SETTINGS ============
        config.set("duel.cooldown", 60);
        config.set("duel.request-timeout", 30);
        config.set("duel.arena-world", "duel_arena");
        config.set("duel.allow spectators", true);
        config.set("duel.spectator-permission", "er.spectate");
        config.set("duel.forfeit-enabled", true);
        
        // ============ ARENA SETTINGS ============
        config.set("arena.initial-count", 10);
        config.set("arena.spacing", 100);
        config.set("arena.schematic", "cloudy.schematic");
        config.set("arena.auto-expand", true);
        config.set("arena.expand-count", 5);
        config.set("arena.respawn-delay", 3);
        
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
        config.set("kit.potions", "SPEED:2,STRENGTH:2");
        
        // ============ POTION EFFECTS ============
        config.set("effects.speed", true);
        config.set("effects.speed-level", 2);
        config.set("effects.speed-duration", 180);
        config.set("effects.strength", true);
        config.set("effects.strength-level", 2);
        config.set("effects.strength-duration", 180);
        
        // ============ WORLD SETTINGS ============
        config.set("world.void-world", true);
        config.set("world.spawn-protection", false);
        config.set("world.pvp-enabled", true);
        
        // ============ GAMEPLAY SETTINGS ============
        config.set("gameplay.fall-damage", false);
        config.set("gameplay.fire-damage", false);
        config.set("gameplay.hunger-depletion", false);
        config.set("gameplay.keep-inventory", true);
        config.set("gameplay.natural-regeneration", false);
        
        // ============ CHAT MESSAGES ============
        config.set("chat.duel-request", true);
        config.set("chat.duel-start", true);
        config.set("chat.duel-end", true);
        config.set("chat.rank-up", true);
        
        // ============ MESSAGES ============
        config.set("messages.elo-gain", "&a+%elo% Elo! &7(Rank: #%rank%)");
        config.set("messages.elo-lost", "&c%elo% Elo! &7(Rank: #%rank%)");
        config.set("messages.new-rank", "&6&l★ &aYou are now Rank %rank%! ★");
        config.set("messages.leaderboard-header", "&7=== &eTop Players &7===");
        
        // ============ LEADERBOARD SETTINGS ============
        config.set("leaderboard.entries-per-page", 10);
        config.set("leaderboard.show-rank-change", true);
        
        // ============ DATABASE/SORAGE SETTINGS ============
        config.set("storage.save-interval", 300); // seconds
        config.set("storage.async-save", true);
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

    // ============ LEADERBOARD GETTERS ============
    public int getLeaderboardEntriesPerPage() {
        return config.getInt("leaderboard.entries-per-page", 10);
    }
}