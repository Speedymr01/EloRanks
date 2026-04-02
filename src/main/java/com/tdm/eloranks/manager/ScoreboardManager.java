package com.tdm.eloranks.manager;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.data.PlayerData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player scoreboard sidebar and bossbar showing duel information.
 */
public class ScoreboardManager {

    private final EloRanks plugin;
    private final EloManager eloManager;
    
    // Animated title frames
    private final String[] titleFrames = {
        "§b§lE§r§el§r§bo§r§el§r§bR§r§ea§r§bn§r§bk§r§bs",
        "§e§lE§r§dl§r§eo§r§dl§r§eR§r§da§r§en§r§dk§r§bs",
        "§a§lE§r§dl§r§co§r§dl§r§cR§r§da§r§en§r§dk§r§bs",
        "§c§lE§r§da§r§eL§r§do§r§eR§r§ca§r§en§r§dk§r§es",
        "§d§lE§r§da§r§bL§r§co§r§bR§r§ca§r§bn§r§dk§r§bs",
    };
    private int currentFrame = 0;
    
    // Track active bossbars for duel players
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    
    // Color scheme
    private final ChatColor PRIMARY = ChatColor.AQUA;
    private final ChatColor ACCENT = ChatColor.GOLD;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor INFO = ChatColor.YELLOW;
    private final ChatColor MUTED = ChatColor.GRAY;

    public ScoreboardManager(EloRanks plugin) {
        this.plugin = plugin;
        this.eloManager = plugin.getEloManager();
        
        // Create main scoreboard for team prefixes
        setupTeamScoreboard();
        
        // Update scoreboard for all players periodically
        startScoreboardUpdater();
        // Update bossbars during duels
        startBossbarUpdater();
        // Update nametags
        startNametagUpdater();
    }
    
    /**
     * Setup the main scoreboard with teams for nametags.
     */
    private void setupTeamScoreboard() {
        // Create a persistent scoreboard for team prefixes
        teamScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Create teams for each rank prefix
        for (int rank = 1; rank <= 50; rank++) {
            String teamName = "rank" + rank;
            Team team = teamScoreboard.registerNewTeam(teamName);
            
            // Format: [#x] with color based on rank
            String prefix = getRankPrefix(rank);
            team.prefix(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix));
            team.setDisplayName("Rank " + rank);
        }
        
        // Default team for unranked
        Team defaultTeam = teamScoreboard.registerNewTeam("unranked");
        defaultTeam.prefix(LegacyComponentSerializer.legacyAmpersand().deserialize("§7[#?§7] "));
    }
    
    private Scoreboard teamScoreboard;
    
    /**
     * Get rank prefix with color based on rank position.
     */
    private String getRankPrefix(int rank) {
        switch (rank) {
            case 1: return "§6§l[#1§6§l] §6";  // Gold for top 1
            case 2: return "§f[#§72§f] §7";    // Silver for top 2
            case 3: return "§f[#§c3§f] §c";    // Bronze for top 3
            case 4: case 5: return "§f[#§e" + rank + "§f] §e"; // Gold for top 5
            case 6: case 7: case 8: case 9: case 10: return "§f[#§b" + rank + "§f] §b"; // Aquatic for top 10
            default: return "§f[#§7" + rank + "§f] §7"; // Gray for others
        }
    }
    
    /**
     * Update nametag for a player.
     */
    public void updateNametag(Player player) {
        if (teamScoreboard == null) return;
        
        PlayerData pd = eloManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        int rank = pd.getRank();
        
        // Find the appropriate team
        String teamName = (rank <= 50) ? "rank" + rank : "unranked";
        Team team = teamScoreboard.getTeam(teamName);
        
        if (team == null) {
            team = teamScoreboard.getTeam("unranked");
        }
        
        // Add player to team (this sets their nametag prefix)
        try {
            // Remove from all other teams first
            for (Team t : teamScoreboard.getTeams()) {
                if (t.hasEntry(player.getName())) {
                    t.removeEntry(player.getName());
                }
            }
            // Add to correct team
            if (team != null) {
                team.addEntry(player.getName());
            }
        } catch (Exception ignored) {}
        
        // Store the scoreboard with teams for use in updateScoreboard
        playerScoreboards.put(player.getUniqueId(), teamScoreboard);
    }
    
    // Store scoreboard per player
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    
    /**
     * Start nametag update task.
     */
    private void startNametagUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("er.duel")) {
                    updateNametag(player);
                }
            }
        }, 40L, 40L); // Update every 2 seconds
    }

    /**
     * Update scoreboard for a player.
     */
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard;
        Objective objective;
        
        // Get or create scoreboard
        if (player.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard()) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        } else {
            scoreboard = player.getScoreboard();
        }
        
        // Get or create objective
        objective = scoreboard.getObjective("eloranks");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("eloranks", "dummy", getAnimatedTitleOnce());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            // Only update the title, not all entries
            objective.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(getAnimatedTitleOnce()));
        }
        
        // Clear existing entries first to prevent duplication
        for (String entry : objective.getScoreboard().getEntries()) {
            if (entry.startsWith("§")) {  // Only clear our entries
                objective.getScoreboard().resetScores(entry);
            }
        }
        
        // Get player data
        PlayerData pd = eloManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        int rank = pd.getRank();
        int elo = pd.getElo();
        
        // Get world name
        String worldName = getWorldDisplayName(player);
        
        // Get opponent info if in duel
        String opponentInfo = getOpponentInfo(player);
        
        // Set scoreboard entries with proper color handling
        setScoreboardEntry(objective, "§7§m-----------------", 12);
        setScoreboardEntry(objective, getAnimatedTitleOnce(), 11);
        setScoreboardEntry(objective, "§6✦ §eRank §6✦", 10);
        setScoreboardEntry(objective, "  §f#§e" + rank + " §7/ §e" + eloManager.getTotalPlayers(), 9);
        setScoreboardEntry(objective, " ", 8);
        setScoreboardEntry(objective, "§6⚔ §eElo §6⚔", 7);
        setScoreboardEntry(objective, "  §e" + elo, 6);
        setScoreboardEntry(objective, "  ", 5);
        setScoreboardEntry(objective, "§6🌍 §eWorld §6🌍", 4);
        setScoreboardEntry(objective, "  §e" + worldName, 3);
        
        // Show opponent info if in duel
        if (opponentInfo != null) {
            setScoreboardEntry(objective, "§c§l⚔ §eVS §c⚔", 2);
            setScoreboardEntry(objective, "  §e" + opponentInfo, 1);
        } else {
            setScoreboardEntry(objective, "§7§m-----------------", 2);
        }
        
        // Use the scoreboard with team prefixes (nametags) if available, otherwise use current
        Scoreboard finalScoreboard = playerScoreboards.getOrDefault(player.getUniqueId(), scoreboard);
        player.setScoreboard(finalScoreboard);
    }
    
    /**
     * Get animated title - cycles through colors (called once per update cycle).
     */
    private int titleFrameCounter = 0;
    
    private String getAnimatedTitleOnce() {
        titleFrameCounter = (titleFrameCounter + 1) % titleFrames.length;
        return titleFrames[titleFrameCounter];
    }
    
    /**
     * Get opponent information if player is in a duel.
     */
    private String getOpponentInfo(Player player) {
        if (!plugin.getDuelManager().hasActiveDuel(player.getUniqueId())) {
            return null;
        }
        
        UUID opponentUuid = plugin.getDuelManager().getDuelOpponent(player.getUniqueId());
        if (opponentUuid == null) return null;
        
        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent == null || !opponent.isOnline()) return null;
        
        // Get opponent's current health
        double health = opponent.getHealth();
        double maxHealth = opponent.getMaxHealth();
        int healthPercent = (int) ((health / maxHealth) * 100);
        
        // Get opponent's elo
        PlayerData opponentData = eloManager.getPlayerData(opponentUuid);
        int opponentElo = (opponentData != null) ? opponentData.getElo() : 0;
        
        // Return colored health bar + player name + elo
        String healthBar = getHealthBar(healthPercent);
        return healthBar + " §f" + opponent.getName() + " §7(§e" + opponentElo + "§7)";
    }
    
    /**
     * Generate a health bar based on percentage.
     */
    private String getHealthBar(int percent) {
        int filled = percent / 10; // 10 segments
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("§7§l█");
            }
        }
        return bar.toString();
    }
    
    /**
     * Set a scoreboard entry with specific score.
     */
    private void setScoreboardEntry(Objective objective, String text, int score) {
        // Use a unique key based on score to avoid conflicts
        String entryKey = "entry_" + score;
        
        Team team = objective.getScoreboard().getTeam(entryKey);
        if (team == null) {
            team = objective.getScoreboard().registerNewTeam(entryKey);
        }
        
        // Handle long text with prefix/suffix using Component
        if (text.length() > 16) {
            String prefix = text.substring(0, 16);
            String suffix = text.substring(16);
            team.prefix(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix));
            team.suffix(LegacyComponentSerializer.legacyAmpersand().deserialize(suffix));
        } else {
            team.prefix(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
            team.suffix(LegacyComponentSerializer.legacyAmpersand().deserialize(""));
        }
        
        // Add entry to objective with score
        // First remove any existing entry with same name
        try {
            Score oldScore = objective.getScore(text);
            if (oldScore != null) {
                objective.getScoreboard().resetScores(text);
            }
        } catch (Exception ignored) {}
        
        team.addEntry(text);
        objective.getScore(text).setScore(score);
    }
    
    /**
     * Get world display name (overworld/nether/end or arena ID).
     */
    private String getWorldDisplayName(Player player) {
        String worldName = player.getWorld().getName();
        
        // Check if player is in a duel
        if (plugin.getDuelManager().hasActiveDuel(player.getUniqueId())) {
            var arena = plugin.getArenaManager().getPlayerArena(player);
            if (arena != null) {
                return "§cArena §e#" + arena.getId();
            }
        }
        
        // Map world names
        switch (worldName.toLowerCase()) {
            case "world":
                return "§aOverworld";
            case "world_nether":
                return "§cNether";
            case "world_the_end":
                return "§5The End";
            default:
                return "§e" + worldName;
        }
    }

    /**
     * Start the scoreboard update task.
     */
    private void startScoreboardUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("er.duel")) {
                    updateScoreboard(player);
                }
            }
        }, 20L, 20L); // Update every 1 second (20 ticks)
    }
    
    /**
     * Update bossbars showing opponent health during duels.
     */
    private void startBossbarUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("er.duel")) {
                    updateDuelBossbar(player);
                }
            }
        }, 10L, 10L); // Update every 0.5 seconds (10 ticks)
    }
    
    /**
     * Update bossbar for player if they're in a duel.
     */
    private void updateDuelBossbar(Player player) {
        if (!plugin.getDuelManager().hasActiveDuel(player.getUniqueId())) {
            // Hide bossbar if not in duel
            if (activeBossBars.containsKey(player.getUniqueId())) {
                player.hideBossBar(activeBossBars.remove(player.getUniqueId()));
            }
            return;
        }
        
        UUID opponentUuid = plugin.getDuelManager().getDuelOpponent(player.getUniqueId());
        if (opponentUuid == null) return;
        
        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent == null || !opponent.isOnline()) return;
        
        // Get opponent health
        double health = opponent.getHealth();
        double maxHealth = opponent.getMaxHealth();
        float healthPercent = (float) (health / maxHealth);
        
        // Get opponent name and elo
        PlayerData opponentData = eloManager.getPlayerData(opponentUuid);
        int opponentElo = (opponentData != null) ? opponentData.getElo() : 0;
        
        // Create bossbar text
        String bossbarText = "§c⚔ §e" + opponent.getName() + " §7(§e" + opponentElo + "§7) §c⚔ §c" + 
            getHealthBar((int)(healthPercent * 100));
        
        net.kyori.adventure.text.Component component = 
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(bossbarText);
        
        // Determine color based on health
        BossBar.Color color;
        if (healthPercent > 0.6f) {
            color = BossBar.Color.GREEN;
        } else if (healthPercent > 0.3f) {
            color = BossBar.Color.YELLOW;
        } else {
            color = BossBar.Color.RED;
        }
        
        BossBar bossBar = BossBar.bossBar(component, healthPercent, color, BossBar.Overlay.NOTCHED_10);
        
        // Show new bossbar
        player.showBossBar(bossBar);
        
        // Remove old bossbar
        if (activeBossBars.containsKey(player.getUniqueId())) {
            player.hideBossBar(activeBossBars.get(player.getUniqueId()));
        }
        
        activeBossBars.put(player.getUniqueId(), bossBar);
    }
    
    /**
     * Remove scoreboard from a player.
     */
    public void removeScoreboard(Player player) {
        try {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        } catch (Exception e) {
            // Ignore errors
        }
        
        // Hide bossbar
        if (activeBossBars.containsKey(player.getUniqueId())) {
            player.hideBossBar(activeBossBars.remove(player.getUniqueId()));
        }
    }
}