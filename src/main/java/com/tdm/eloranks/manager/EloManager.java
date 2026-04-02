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
        plugin.getLogger().info("Loading Elo data from disk...");
        loadData();
        plugin.getLogger().info("Loaded " + playerCache.size() + " player records");
    }

    private void loadData() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "elo_data.yml");
        
        if (!dataFile.exists()) {
            plugin.getLogger().info("No existing data file, creating new one...");
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create elo_data.yml: " + e.getMessage());
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        plugin.getLogger().info("Loading players from config...");
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
        
        updateRanks();
        plugin.getLogger().info("Loaded " + playerCache.size() + " player records");
    }

    public void saveAll() {
        plugin.getLogger().info("Saving " + playerCache.size() + " player records to disk...");
        
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
            plugin.getLogger().info("Data saved successfully!");
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
            updateRanks();
            saveAll();
        } else {
            if (!pd.getPlayerName().equals(playerName)) {
                pd.setPlayerName(playerName);
            }
        }
        return pd;
    }

    /**
     * Calculate Elo change using standard Elo formula with dynamic K-factor.
     * 
     * @param playerElo The player's current Elo
     * @param opponentElo The opponent's current Elo
     * @param totalMatches Total matches the player has played (for dynamic K)
     * @param placementMatches Number of placement matches played
     * @param placementActive Whether placement system is active
     * @param isWin true if player won, false if lost
     * @return Elo points to add/subtract
     */
    public int calculateEloChange(int playerElo, int opponentElo, int totalMatches, 
            int placementMatches, boolean placementActive, boolean isWin) {
        // Expected score calculation
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentElo - playerElo) / 400.0));
        
        // Get effective K-factor (dynamic based on games played and Elo)
        int kFactor;
        
        // Special K-factor for placement matches (use high K for first few matches)
        if (placementActive && placementMatches < configManager.getPlacementKFactorGames()) {
            kFactor = configManager.getEffectiveKFactor(1000, 0, false); // Use new player K (48)
        } else {
            kFactor = configManager.getEffectiveKFactor(playerElo, totalMatches, false);
        }
        
        int eloChange;
        if (isWin) {
            // Win: K * (1 - Expected)
            eloChange = (int) Math.round(kFactor * (1.0 - expected));
        } else {
            // Loss: K * (0 - Expected) = -K * Expected
            eloChange = (int) Math.round(-kFactor * expected);
        }
        
        // Add placement bonus if active
        if (placementActive && placementMatches < configManager.getPlacementMatchCount()) {
            int bonus = getPlacementBonus(playerElo, opponentElo, isWin);
            eloChange += bonus;
        }
        
        return eloChange;
    }

    /**
     * Apply Elo changes after a duel.
     */
    public EloChangeResult applyDuelResult(UUID winnerUuid, UUID loserUuid) {
        PlayerData winner = getPlayerData(winnerUuid);
        PlayerData loser = getPlayerData(loserUuid);
        
        if (winner == null || loser == null) {
            return null;
        }
        
        // Check if placement system is active
        boolean placementActive = isPlacementSystemActive();
        
        // Calculate Elo changes with dynamic K-factor and placement bonuses
        int winnerChange = calculateEloChange(winner.getElo(), loser.getElo(), 
            winner.getTotalMatches(), winner.getPlacementMatches(), placementActive, true);
        int loserChange = calculateEloChange(loser.getElo(), winner.getElo(), 
            loser.getTotalMatches(), loser.getPlacementMatches(), placementActive, false);
        
        // Apply changes
        int newWinnerElo = Math.max(configManager.getMinElo(), 
            Math.min(configManager.getMaxElo(), winner.getElo() + winnerChange));
        int newLoserElo = Math.max(configManager.getMinElo(), 
            Math.min(configManager.getMaxElo(), loser.getElo() + loserChange));
        
        winner.setElo(newWinnerElo);
        loser.setElo(newLoserElo);
        
        winner.addWin();
        loser.addLoss();
        
        // Update placement matches
        if (placementActive) {
            winner.incrementPlacementMatches();
            loser.incrementPlacementMatches();
            
            // Check if placement completed
            if (winner.getPlacementMatches() >= configManager.getPlacementMatchCount()) {
                winner.setPlacementCompleted(true);
            }
            if (loser.getPlacementMatches() >= configManager.getPlacementMatchCount()) {
                loser.setPlacementCompleted(true);
            }
        }
        
        winner.setLastDuelTime(System.currentTimeMillis());
        loser.setLastDuelTime(System.currentTimeMillis());
        
        updateRanks();
        saveAll();
        
        return new EloChangeResult(winnerChange, loserChange);
    }
    
    /**
     * Apply Elo changes for a surrender.
     * Surrenderer gets full penalty (instant loss), opponent gets full reward.
     * 
     * @param surrendererUuid UUID of the surrendering player
     * @param opponentUuid UUID of the opponent
     * @return EloChangeResult or null if players not found
     */
    public EloChangeResult applySurrender(UUID surrendererUuid, UUID opponentUuid) {
        PlayerData surrenderer = getPlayerData(surrendererUuid);
        PlayerData opponent = getPlayerData(opponentUuid);
        
        if (surrenderer == null || opponent == null) {
            return null;
        }
        
        // Calculate full Elo changes (as if normal win/loss) - instant loss!
        boolean placementActive = isPlacementSystemActive();
        
        int fullWinnerChange = calculateEloChange(opponent.getElo(), surrenderer.getElo(),
            opponent.getTotalMatches(), opponent.getPlacementMatches(), placementActive, true);
        int fullLoserChange = calculateEloChange(surrenderer.getElo(), opponent.getElo(),
            surrenderer.getTotalMatches(), surrenderer.getPlacementMatches(), placementActive, false);
        
        // Apply full changes (no halving)
        int surrendererPenalty = Math.abs(fullLoserChange);
        int opponentReward = fullWinnerChange;
        
        // Apply changes
        int newSurrendererElo = Math.max(configManager.getMinElo(),
            Math.min(configManager.getMaxElo(), surrenderer.getElo() - surrendererPenalty));
        int newOpponentElo = Math.max(configManager.getMinElo(),
            Math.min(configManager.getMaxElo(), opponent.getElo() + opponentReward));
        
        surrenderer.setElo(newSurrendererElo);
        opponent.setElo(newOpponentElo);
        
        // Stats: surrender counts as loss for surrenderer, win for opponent
        surrenderer.addLoss();
        opponent.addWin();
        
        // Update placement matches if active
        if (placementActive) {
            surrenderer.incrementPlacementMatches();
            opponent.incrementPlacementMatches();
            
            if (surrenderer.getPlacementMatches() >= configManager.getPlacementMatchCount()) {
                surrenderer.setPlacementCompleted(true);
            }
            if (opponent.getPlacementMatches() >= configManager.getPlacementMatchCount()) {
                opponent.setPlacementCompleted(true);
            }
        }
        
        surrenderer.setLastDuelTime(System.currentTimeMillis());
        opponent.setLastDuelTime(System.currentTimeMillis());
        
        updateRanks();
        saveAll();
        
        // Return with surrenderer as "loser" (negative change) and opponent as "winner" (positive change)
        return new EloChangeResult(opponentReward, -surrendererPenalty);
    }
    
    /**
     * Check if placement system is active (5+ players on leaderboard).
     */
    public boolean isPlacementSystemActive() {
        if (!configManager.isPlacementEnabled()) return false;
        return playerCache.size() >= configManager.getPlacementMinPlayers();
    }
    
    /**
     * Get the predicted Elo change bonus for placement matches.
     * Based on expected outcome vs actual outcome.
     */
    public int getPlacementBonus(int playerElo, int opponentElo, boolean isWin) {
        if (!isPlacementSystemActive()) return 0;
        
        // Calculate expected score (probability of winning)
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentElo - playerElo) / 400.0));
        
        // Bonus is higher when you win against a higher-rated opponent
        // or lose less than expected against a higher-rated opponent
        if (isWin) {
            // Win bonus: extra Elo if you beat someone higher rated
            if (opponentElo > playerElo) {
                return (int) Math.round((opponentElo - playerElo) * 0.1); // 10% of Elo gap
            }
            return 0;
        } else {
            // Loss mitigation: lose less if you were expected to lose badly
            if (opponentElo > playerElo && expected < 0.5) {
                return (int) Math.round((expected * 10)); // Reduce loss by up to 10
            }
            return 0;
        }
    }

    /**
     * Update all player ranks based on their Elo.
     */
    public void updateRanks() {
        if (playerCache.isEmpty()) return;
        
        List<PlayerData> sorted = new ArrayList<>(playerCache.values());
        sorted.sort((a, b) -> Integer.compare(b.getElo(), a.getElo()));
        
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRank(i + 1);
        }
        
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
        return new ArrayList<>(top.subList(0, Math.min(count, top.size())));
    }

    public int getPlayerRank(UUID uuid) {
        PlayerData pd = getPlayerData(uuid);
        return pd != null ? pd.getRank() : 0;
    }

    public int getTotalPlayers() {
        return playerCache.size();
    }

    /**
     * Reset all player statistics.
     */
    public void resetAllStats() {
        playerCache.clear();
        leaderboard.clear();
        saveAll();
    }

    /**
     * Reset a specific player's stats by UUID.
     */
    public void resetPlayerStats(UUID uuid) {
        playerCache.remove(uuid);
        leaderboard.removeIf(pd -> pd.getPlayerId().equals(uuid));
    }

    /**
     * Reset a specific player's stats by name.
     */
    public void resetPlayerStatsByName(String playerName) {
        playerCache.entrySet().removeIf(entry -> 
            entry.getValue().getPlayerName().equalsIgnoreCase(playerName));
        leaderboard.removeIf(pd -> pd.getPlayerName().equalsIgnoreCase(playerName));
    }

    /**
     * Set a player's Elo by UUID.
     */
    public void setPlayerElo(UUID uuid, int elo) {
        PlayerData pd = playerCache.get(uuid);
        if (pd != null) {
            pd.setElo(elo);
            updateRanks();
            saveAll();
        }
    }

    /**
     * Set a player's Elo by name.
     */
    public void setPlayerEloByName(String playerName, int elo) {
        for (PlayerData pd : playerCache.values()) {
            if (pd.getPlayerName().equalsIgnoreCase(playerName)) {
                pd.setElo(elo);
                updateRanks();
                saveAll();
                return;
            }
        }
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