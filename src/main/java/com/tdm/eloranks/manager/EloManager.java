package com.tdm.eloranks.manager;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.config.ConfigManager;
import com.tdm.eloranks.data.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all Elo-related data and calculations.
 */
public class EloManager {

    private final EloRanks plugin;
    private final ConfigManager configManager;
    
    // Cache of player data (UUID -> PlayerData)
    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    
    // Sorted list for leaderboard (by Elo descending)
    private final List<PlayerData> leaderboard = Collections.synchronizedList(new ArrayList<>());
    
    // File for persistent storage
    private File dataFile;
    private YamlConfiguration dataConfig;

    public EloManager(EloRanks plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadData();
    }

    private void loadData() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "elo_data.yml");
        
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create elo_data.yml: " + e.getMessage());
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadPlayers();
    }

    private void loadPlayers() {
        if (dataConfig.contains("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String name = dataConfig.getString("players." + uuidStr + ".name", "Unknown");
                    int elo = dataConfig.getInt("players." + uuidStr + ".elo", configManager.getStartingElo());
                    int wins = dataConfig.getInt("players." + uuidStr + ".wins", 0);
                    int losses = dataConfig.getInt("players." + uuidStr + ".losses", 0);
                    int draws = dataConfig.getInt("players." + uuidStr + ".draws", 0);
                    long lastDuel = dataConfig.getLong("players." + uuidStr + ".lastDuel", 0);
                    
                    PlayerData pd = new PlayerData(uuid, name, elo);
                    pd.setElo(elo);
                    pd.setPlayerName(name);
                    
                    // Manually set stats
                    for (int i = 0; i < wins; i++) pd.addWin();
                    for (int i = 0; i < losses; i++) pd.addLoss();
                    for (int i = 0; i < draws; i++) pd.addDraw();
                    pd.setLastDuelTime(lastDuel);
                    
                    playerCache.put(uuid, pd);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load player data for " + uuidStr + ": " + e.getMessage());
                }
            }
        }
        
        updateLeaderboard();
        plugin.getLogger().info("Loaded " + playerCache.size() + " player records");
    }

    public void saveAll() {
        // Clear and rebuild config
        dataConfig.set("players", null);
        
        for (PlayerData pd : playerCache.values()) {
            String path = "players." + pd.getPlayerId().toString();
            dataConfig.set(path + ".name", pd.getPlayerName());
            dataConfig.set(path + ".elo", pd.getElo());
            dataConfig.set(path + ".wins", pd.getWins());
            dataConfig.set(path + ".losses", pd.getLosses());
            dataConfig.set(path + ".draws", pd.getDraws());
            dataConfig.set(path + ".lastDuel", pd.getLastDuelTime());
        }
        
        try {
            dataConfig.save(dataFile);
            plugin.getLogger().info("Saved Elo data for " + playerCache.size() + " players");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save elo_data.yml: " + e.getMessage());
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerCache.get(uuid);
    }

    public PlayerData getOrCreatePlayerData(UUID uuid, String playerName) {
        PlayerData pd = playerCache.get(uuid);
        if (pd == null) {
            pd = new PlayerData(uuid, playerName, configManager.getStartingElo());
            playerCache.put(uuid, pd);
            updateLeaderboard();
            
            // Save immediately for new players
            saveAll();
        } else {
            // Update name in case they changed it
            if (!pd.getPlayerName().equals(playerName)) {
                pd.setPlayerName(playerName);
            }
        }
        return pd;
    }

    /**
     * Calculate Elo change using standard Elo formula.
     * 
     * @param winnerElo Winner/Player's current Elo
     * @param loserElo Loser/Opponent's current Elo
     * @param isWin true if winner is calculating their gain, false for loser
     * @return Elo points to add/subtract
     */
    public int calculateEloChange(int winnerElo, int loserElo, boolean isWin) {
        // Expected score calculation
        double expectedWinner = 1.0 / (1.0 + Math.pow(10, (loserElo - winnerElo) / 400.0));
        double expectedLoser = 1.0 - expectedWinner;
        
        // Determine K-factor based on games played (fewer games = higher K-factor)
        // This allows newer players to rank up faster
        double kFactor = configManager.getKFactorWin();
        
        if (isWin) {
            // Winner gains: K * (1 - Expected)
            return (int) Math.round(kFactor * (1.0 - expectedWinner));
        } else {
            // Loser loses: K * (0 - Expected) = -K * Expected
            return (int) Math.round(-kFactor * expectedLoser);
        }
    }

    /**
     * Apply Elo changes after a duel.
     * 
     * @param winnerUuid Winner's UUID
     * @param loserUuid Loser's UUID
     * @return EloChangeResult containing the changes
     */
    public EloChangeResult applyDuelResult(UUID winnerUuid, UUID loserUuid) {
        PlayerData winner = getPlayerData(winnerUuid);
        PlayerData loser = getPlayerData(loserUuid);
        
        if (winner == null || loser == null) {
            return null;
        }
        
        int winnerChange = calculateEloChange(winner.getElo(), loser.getElo(), true);
        int loserChange = calculateEloChange(winner.getElo(), loser.getElo(), false);
        
        // Apply changes
        int newWinnerElo = Math.max(configManager.getMinElo(), 
            Math.min(configManager.getMaxElo(), winner.getElo() + winnerChange));
        int newLoserElo = Math.max(configManager.getMinElo(), 
            Math.min(configManager.getMaxElo(), loser.getElo() + loserChange));
        
        winner.setElo(newWinnerElo);
        loser.setElo(newLoserElo);
        
        winner.addWin();
        loser.addLoss();
        
        winner.setLastDuelTime(System.currentTimeMillis());
        loser.setLastDuelTime(System.currentTimeMillis());
        
        // Update ranks
        updateRanks();
        
        // Save data
        saveAll();
        
        return new EloChangeResult(winnerChange, loserChange);
    }

    /**
     * Update all player ranks based on their Elo.
     */
    public void updateRanks() {
        if (playerCache.isEmpty()) return;
        
        // Sort by Elo descending
        List<PlayerData> sorted = new ArrayList<>(playerCache.values());
        sorted.sort((a, b) -> Integer.compare(b.getElo(), a.getElo()));
        
        // Update ranks (Rank 1 = highest Elo)
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRank(i + 1);
        }
        
        // Update leaderboard
        leaderboard.clear();
        leaderboard.addAll(sorted);
    }

    public List<PlayerData> getLeaderboard() {
        if (leaderboard.isEmpty()) {
            updateRanks();
        }
        return new ArrayList<>(leaderboard);
    }

    public List<PlayerData> getTopPlayers(int count) {
        List<PlayerData> top = getLeaderboard();
        return top.subList(0, Math.min(count, top.size()));
    }

    public int getPlayerRank(UUID uuid) {
        PlayerData pd = getPlayerData(uuid);
        return pd != null ? pd.getRank() : 0;
    }

    public int getTotalPlayers() {
        return playerCache.size();
    }

    public void updateLeaderboard() {
        updateRanks();
    }

    /**
     * Result class for duel Elo changes.
     */
    public static class EloChangeResult {
        public final int winnerChange;
        public final int loserChange;

        public EloChangeResult(int winnerChange, int loserChange) {
            this.winnerChange = winnerChange;
            this.loserChange = loserChange;
        }
    }
}
