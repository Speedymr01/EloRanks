package com.tdm.eloranks;

import org.bukkit.plugin.java.JavaPlugin;
import com.tdm.eloranks.commands.EloCommand;
import com.tdm.eloranks.commands.DuelCommand;
import com.tdm.eloranks.commands.LeaderboardCommand;
import com.tdm.eloranks.listeners.PlayerDeathListener;
import com.tdm.eloranks.manager.EloManager;
import com.tdm.eloranks.manager.DuelManager;
import com.tdm.eloranks.manager.WorldManager;
import com.tdm.eloranks.config.ConfigManager;

/**
 * EloRanks - Competitive 1v1 Elo-Based Ranking System
 * 
 * A plugin that assigns every player a dynamic rank (Rank 1 to Rank N)
 * based entirely on their Elo rating, earned through 1v1 duels.
 */
public final class EloRanks extends JavaPlugin {

    private static EloRanks instance;
    private ConfigManager configManager;
    private EloManager eloManager;
    private DuelManager duelManager;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers
        configManager = new ConfigManager(this);
        worldManager = new WorldManager(this);
        eloManager = new EloManager(this);
        duelManager = new DuelManager(this);

        // Register commands
        getCommand("elo").setExecutor(new EloCommand(this));
        getCommand("duel").setExecutor(new DuelCommand(this));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        getCommand("elostats").setExecutor(new EloCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);

        getLogger().info("EloRanks has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save all data
        if (eloManager != null) {
            eloManager.saveAll();
        }
        
        getLogger().info("EloRanks has been disabled!");
    }

    public static EloRanks getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EloManager getEloManager() {
        return eloManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
}
