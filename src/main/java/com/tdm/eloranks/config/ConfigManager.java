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
        // Default settings
        config.set("elo.starting", 1000);
        config.set("elo.min", 0);
        config.set("elo.max", 10000);
        
        // K-factor (determines how much Elo changes per match)
        config.set("elo.k-factor.win", 32);
        config.set("elo.k-factor.draw", 16);
        
        // Rank calculation
        config.set("ranks.percentage", true); // Use top X% for rank 1
        
        // Duel settings
        config.set("duel.cooldown", 60); // seconds
        config.set("duel.request-timeout", 30); // seconds
        config.set("duel.arena-world", "duel_arena");
        
        // Kit settings
        config.set("kit.sword", "DIAMOND_SWORD");
        config.set("kit.armor", "DIAMOND_CHESTPLATE,DIAMOND_LEGGINGS,DIAMOND_BOOTS");
        config.set("kit.food", "COOKED_BEEF:64");
        config.set("kit.potions", "SPEED:2,STRENGTH:1");
        
        // Messages
        config.set("messages.elo-gain", "&a+%elo% Elo! &7(Match: %match_elo%)");
        config.set("messages.elo-lost", "&c-%elo% Elo! &7(Match: %match_elo%)");
        config.set("messages.new-rank", "&6&l★ &aYou are now Rank %rank%! ★");
        config.set("messages.leaderboard-header", "&7=== &eTop Players &7===");
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

    // Convenience methods for common config values
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

    public int getDuelCooldown() {
        return config.getInt("duel.cooldown", 60);
    }

    public int getRequestTimeout() {
        return config.getInt("duel.request-timeout", 30);
    }
}
