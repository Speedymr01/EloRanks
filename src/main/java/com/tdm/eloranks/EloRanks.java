package com.tdm.eloranks;

import org.bukkit.plugin.java.JavaPlugin;
import com.tdm.eloranks.commands.EloCommand;
import com.tdm.eloranks.commands.DuelCommand;
import com.tdm.eloranks.commands.LeaderboardCommand;
import com.tdm.eloranks.commands.AdminCommand;
import com.tdm.eloranks.listeners.PlayerDeathListener;
import com.tdm.eloranks.listeners.BlockBreakListener;
import com.tdm.eloranks.listeners.PlayerQuitListener;
import com.tdm.eloranks.listeners.EntityDamageListener;
import com.tdm.eloranks.manager.EloManager;
import com.tdm.eloranks.manager.DuelManager;
import com.tdm.eloranks.manager.ArenaManager;
import com.tdm.eloranks.manager.ScoreboardManager;
import com.tdm.eloranks.manager.CountdownManager;
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
    private ArenaManager arenaManager;
    private ScoreboardManager scoreboardManager;
    private CountdownManager countdownManager;

    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║       EloRanks Loading...              ║");
        getLogger().info("╚══════════════════════════════════════╝");
        
        // Initialize managers
        getLogger().info("[1/5] Loading config...");
        configManager = new ConfigManager(this);
        
        getLogger().info("[2/5] Loading Elo data...");
        eloManager = new EloManager(this);
        
        getLogger().info("[3/5] Loading DuelManager...");
        duelManager = new DuelManager(this);
        
        getLogger().info("[4/5] Loading ArenaManager (FAWE)...");
        arenaManager = new ArenaManager(
            this, 
            configManager.getArenaSchematic(), 
            configManager.getInitialArenaCount(), 
            configManager.getArenaSpacing()
        );

        getLogger().info("[5/5] Loading ScoreboardManager...");
        scoreboardManager = new ScoreboardManager(this);
        
        getLogger().info("[6/6] Loading CountdownManager...");
        countdownManager = new CountdownManager(this);
        
        getLogger().info("[7/7] Registering commands & listeners...");
        
        // Register commands
        getCommand("er").setExecutor(new EloCommand(this));
        getCommand("er").setTabCompleter(new EloCommand(this));
        getCommand("duel").setExecutor(new DuelCommand(this));
        getCommand("duel").setTabCompleter(new DuelCommand(this));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        getCommand("leaderboard").setTabCompleter(new LeaderboardCommand(this));
        getCommand("eradmin").setExecutor(new AdminCommand(this));
        getCommand("eradmin").setTabCompleter(new AdminCommand(this));
        getCommand("surrender").setExecutor(new DuelCommand(this));  // Reuse DuelCommand for surrender
        getCommand("surrender").setTabCompleter(new DuelCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityDamageListener(this), this);

        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║  ✅ EloRanks Enabled Successfully!  ║");
        getLogger().info("║  Version: " + getDescription().getVersion() + "                    ║");
        getLogger().info("║  Author: " + getDescription().getAuthors() + "                     ║");
        getLogger().info("╚══════════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("  EloRanks Shutting Down...");
        
        // Save all data
        if (eloManager != null) {
            getLogger().info("  Saving player data...");
            eloManager.saveAll();
        }
        
        getLogger().info("  ✅ Shutdown complete!");
        getLogger().info("═══════════════════════════════════════");
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

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
    
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    public CountdownManager getCountdownManager() {
        return countdownManager;
    }
}
