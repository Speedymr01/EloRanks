package com.tdm.eloranks.manager;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.config.ConfigManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages duel requests, matchmaking, and arena handling.
 */
public class DuelManager {

    private final EloRanks plugin;
    private final ConfigManager configManager;
    private final EloManager eloManager;
    
    // Color scheme
    private final ChatColor PRIMARY = ChatColor.AQUA;
    private final ChatColor ACCENT = ChatColor.GOLD;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor DANGER = ChatColor.RED;
    private final ChatColor INFO = ChatColor.YELLOW;
    private final ChatColor MUTED = ChatColor.GRAY;
    
    // Active duel requests (requester -> target)
    private final Map<UUID, UUID> duelRequests = new ConcurrentHashMap<>();
    
    // Active duels (player1 -> player2)
    private final Map<UUID, UUID> activeDuels = new ConcurrentHashMap<>();
    
    // Stored inventories for when duel ends
    private final Map<UUID, ItemStack[]> playerInventories = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> playerArmor = new ConcurrentHashMap<>();
    // Request cooldown map (separate from duel cooldown)
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();
    private static final long REQUEST_COOLDOWN_MS = 10000; // 10 seconds
    
    // Stored locations for when duel ends
    private final Map<UUID, Location> playerLocations = new ConcurrentHashMap<>();
    
    // Track players who were warned about pending requests when enabling matchmaking
    private final Map<UUID, Boolean> pendingMatchmakingConfirm = new ConcurrentHashMap<>();
    
    // Track when each duel started (for surrender time check)
    private final Map<UUID, Long> duelStartTimes = new ConcurrentHashMap<>();
    
    // Track pending duels (match found, countdown started) - key is player UUID, value is countdown seconds remaining
    private final Map<UUID, Integer> pendingDuels = new ConcurrentHashMap<>();
    
    public DuelManager(EloRanks plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.eloManager = plugin.getEloManager();
    }
    
    // Players waiting for matchmaking with their wait time (in seconds)
    private final Set<UUID> waitingForMatchmaking = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> matchmakingWaitTime = new ConcurrentHashMap<>();
    
    // Matchmaking task ID for cancellation
    private int matchmakingTaskId = -1;

    /**
     * Find a player with similar Elo for matchmaking.
     * Returns the closest matching player within acceptable range.
     * Range expands over time, and both players must be in each other's range.
     */
    public Player findMatchmakingOpponent(Player requester) {
        // Get requester's Elo
        var playerData = eloManager.getOrCreatePlayerData(requester.getUniqueId(), requester.getName());
        int requesterElo = playerData.getElo();
        
        // Get config values
        int initialRange = configManager.getMatchmakingInitialRange();
        int rangeIncrease = configManager.getMatchmakingRangeIncrease();
        int maxRange = configManager.getMatchmakingMaxRange();
        
        // Get wait time for this player to calculate dynamic range
        int waitSeconds = matchmakingWaitTime.getOrDefault(requester.getUniqueId(), 0);
        int eloRange = Math.min(initialRange + (waitSeconds * rangeIncrease), maxRange);
        
        if (plugin.getConfigManager().isDebugMatchmaking()) {
            plugin.getLogger().info("Matchmaking for " + requester.getName() + ": wait=" + waitSeconds + "s, range=" + eloRange);
        }
        
        Player bestMatch = null;
        int bestEloDiff = Integer.MAX_VALUE;
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Skip self
            if (onlinePlayer.equals(requester)) continue;
            
            // Skip players already in a duel or have pending request
            if (hasActiveDuel(onlinePlayer.getUniqueId())) continue;
            if (duelRequests.containsKey(requester.getUniqueId()) && 
                duelRequests.get(requester.getUniqueId()).equals(onlinePlayer.getUniqueId())) continue;
            
            // Check if this player also wants a matchmade duel
            if (!waitingForMatchmaking.contains(onlinePlayer.getUniqueId())) continue;
            
            // Get their Elo
            var targetData = eloManager.getOrCreatePlayerData(onlinePlayer.getUniqueId(), onlinePlayer.getName());
            int targetElo = targetData.getElo();
            
            // Calculate their wait time and range (only if bidirectional check enabled)
            int targetRange = eloRange;  // Default to requester's range
            if (configManager.isMatchmakingBidirectionalCheck()) {
                int targetWaitSeconds = matchmakingWaitTime.getOrDefault(onlinePlayer.getUniqueId(), 0);
                targetRange = Math.min(initialRange + (targetWaitSeconds * rangeIncrease), maxRange);
            }
            
            // Check if BOTH players are in each other's allowed range
            int diffToTarget = Math.abs(requesterElo - targetElo);
            
            if (diffToTarget <= eloRange && diffToTarget <= targetRange) {
                if (diffToTarget < bestEloDiff) {
                    bestMatch = onlinePlayer;
                    bestEloDiff = diffToTarget;
                }
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Toggle matchmaking for a player.
     */
    public boolean toggleMatchmaking(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        if (waitingForMatchmaking.contains(playerUuid)) {
            waitingForMatchmaking.remove(playerUuid);
            player.sendMessage(INFO + "➖ " + MUTED + "Matchmaking disabled");
            return false;
        } else {
            if (hasActiveDuel(playerUuid)) {
                player.sendMessage(DANGER + "✖ " + MUTED + "You are already in a duel!");
                return false;
            }
            
            // Check if player was previously warned about pending request
            // This is their second toggle - confirm the matchmake
            if (pendingMatchmakingConfirm.containsKey(playerUuid) && pendingMatchmakingConfirm.get(playerUuid)) {
                // They confirmed - remove their pending request
                pendingMatchmakingConfirm.remove(playerUuid);
                
                if (duelRequests.containsKey(playerUuid)) {
                    UUID targetUuid = duelRequests.get(playerUuid);
                    Player target = Bukkit.getPlayer(targetUuid);
                    if (target != null && target.isOnline()) {
                        target.sendMessage(DANGER + "⚠ " + MUTED + "Duel request cancelled - " + 
                            ACCENT + player.getName() + MUTED + " entered matchmaking!");
                    }
                    duelRequests.remove(playerUuid);
                    player.sendMessage(INFO + "➖ " + MUTED + "Pending request to " + 
                        ACCENT + (target != null ? target.getName() : "player") + MUTED + " cancelled.");
                }
            }
            
            // Check if this player has sent any pending duel requests
            if (duelRequests.containsKey(playerUuid)) {
                UUID targetUuid = duelRequests.get(playerUuid);
                Player target = Bukkit.getPlayer(targetUuid);
                String targetName = target != null ? target.getName() : "unknown";
                
                // Send "are you sure?" message
                player.sendMessage(INFO + "⚠ " + MUTED + "You have a pending request to " + ACCENT + targetName);
                player.sendMessage(INFO + "  Enable matchmaking anyway? This will cancel your pending request.");
                player.sendMessage(MUTED + "  Type /duel match again to confirm, or wait for request to expire.");
                
                // Store that they were warned about pending request
                pendingMatchmakingConfirm.put(playerUuid, true);
                return false;
            }
            
            // Case 4: Player has pending requests TO them (they are the target)
            // Do NOT cancel these requests - just notify senders that target is now in matchmaking
            for (Map.Entry<UUID, UUID> entry : duelRequests.entrySet()) {
                if (entry.getValue().equals(playerUuid)) {
                    Player sender = Bukkit.getPlayer(entry.getKey());
                    if (sender != null && sender.isOnline()) {
                        sender.sendMessage(INFO + "ℹ " + MUTED + "Your request target " + 
                            ACCENT + player.getName() + MUTED + " is now in matchmaking.");
                        sender.sendMessage(INFO + "  They can still accept your request, or may be matched soon.");
                    }
                }
            }
            // DO NOT remove requests where this player is the target - keep them for potential accept
            
            waitingForMatchmaking.add(playerUuid);
            matchmakingWaitTime.put(playerUuid, 0);
            player.sendMessage(SUCCESS + "✅ " + MUTED + "Matchmaking enabled! Looking for opponent...");
            player.sendMessage(INFO + "  Elo range expands over time. Use /duel cancel to stop.");
            
            // Start matchmaking check task if not already running
            startMatchmakingTask();
            
            // Try to find a match immediately
            Player match = findMatchmakingOpponent(player);
            if (match != null) {
                // Remove both from waiting
                waitingForMatchmaking.remove(playerUuid);
                waitingForMatchmaking.remove(match.getUniqueId());
                matchmakingWaitTime.remove(playerUuid);
                matchmakingWaitTime.remove(match.getUniqueId());
                
                // Cancel any requests FROM player (they're the requester)
                if (duelRequests.containsKey(playerUuid)) {
                    Player oldTarget = Bukkit.getPlayer(duelRequests.get(playerUuid));
                    if (oldTarget != null && oldTarget.isOnline()) {
                        oldTarget.sendMessage(DANGER + "⚠ " + MUTED + "Duel request cancelled - " + 
                            ACCENT + player.getName() + MUTED + " was matched with another player!");
                    }
                    duelRequests.remove(playerUuid);
                }
                
                // Send duel requests to both
                sendDuelRequest(player, match);
                return true;
            }
            return true;
        }
    }
    
    /**
     * Start the matchmaking checker task (runs every second).
     */
    private void startMatchmakingTask() {
        if (matchmakingTaskId != -1) {
            return; // Already running
        }
        
        matchmakingTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (waitingForMatchmaking.isEmpty()) {
                // No one waiting, stop the task
                if (matchmakingTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(matchmakingTaskId);
                    matchmakingTaskId = -1;
                }
                return;
            }
            
            // Increment wait time for all waiting players
            for (UUID uuid : waitingForMatchmaking) {
                int current = matchmakingWaitTime.getOrDefault(uuid, 0);
                matchmakingWaitTime.put(uuid, current + 1);
                
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    // Only show message if enabled in config
                    if (configManager.isMatchmakingChatEnabled()) {
                        int initialRange = configManager.getMatchmakingInitialRange();
                        int rangeIncrease = configManager.getMatchmakingRangeIncrease();
                        int maxRange = configManager.getMatchmakingMaxRange();
                        int range = Math.min(initialRange + (current + 1) * rangeIncrease, maxRange);
                        player.sendMessage(INFO + "⏳ " + MUTED + "Searching... Elo range: " + range);
                    }
                }
            }
            
            // Try to match players
            Set<UUID> toRemove = new HashSet<>();
            for (UUID uuid : waitingForMatchmaking) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    toRemove.add(uuid);
                    continue;
                }
                
                Player match = findMatchmakingOpponent(player);
                if (match != null) {
                    // Remove both from waiting
                    UUID matchUuid = match.getUniqueId();
                    waitingForMatchmaking.remove(uuid);
                    waitingForMatchmaking.remove(matchUuid);
                    matchmakingWaitTime.remove(uuid);
                    matchmakingWaitTime.remove(matchUuid);
                    
                    // Notify players
                    player.sendMessage(SUCCESS + "✅ " + MUTED + "Opponent found: " + ACCENT + match.getName() + MUTED + "!");
                    match.sendMessage(SUCCESS + "✅ " + MUTED + "Opponent found: " + ACCENT + player.getName() + MUTED + "!");
                    
                    // Send duel requests
                    sendDuelRequest(player, match);
                    toRemove.add(uuid);
                    toRemove.add(matchUuid);
                    break; // Only match one pair at a time
                }
            }
            
            // Clean up any players who went offline
            for (UUID uuid : toRemove) {
                waitingForMatchmaking.remove(uuid);
                matchmakingWaitTime.remove(uuid);
            }
            
            // If no one left waiting, stop task
            if (waitingForMatchmaking.isEmpty() && matchmakingTaskId != -1) {
                Bukkit.getScheduler().cancelTask(matchmakingTaskId);
                matchmakingTaskId = -1;
            }
        }, 20L, 20L).getTaskId(); // Run every second (20 ticks)
    }
    
    /**
     * Cancel matchmaking for a specific player.
     */
    public void cancelMatchmaking(Player player) {
        UUID playerUuid = player.getUniqueId();
        if (waitingForMatchmaking.contains(playerUuid)) {
            waitingForMatchmaking.remove(playerUuid);
            matchmakingWaitTime.remove(playerUuid);
            player.sendMessage(INFO + "➖ " + MUTED + "Matchmaking cancelled");
            
            // Stop task if no one left waiting
            if (waitingForMatchmaking.isEmpty() && matchmakingTaskId != -1) {
                Bukkit.getScheduler().cancelTask(matchmakingTaskId);
                matchmakingTaskId = -1;
            }
        } else {
            player.sendMessage(DANGER + "✖ " + MUTED + "You are not in matchmaking!");
        }
    }
    
    /**
     * Handle player surrendering from a duel.
     * Surrenderer gets full penalty (instant loss), opponent gets full reward.
     * Only available after 30 seconds of the duel starting.
     */
    public boolean surrender(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        if (!configManager.isSurrenderEnabled()) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Surrendering is disabled!");
            return false;
        }
        
        if (!hasActiveDuel(playerUuid)) {
            player.sendMessage(DANGER + "✖ " + MUTED + "You are not in a duel!");
            return false;
        }
        
        // Check if minimum duel time has passed
        long duelStartTime = duelStartTimes.get(playerUuid);
        if (duelStartTime > 0) {
            int minSeconds = configManager.getSurrenderMinDuelTimeSeconds();
            long elapsed = (System.currentTimeMillis() - duelStartTime) / 1000;
            if (elapsed < minSeconds) {
                player.sendMessage(DANGER + "✖ " + MUTED + "You must wait " + minSeconds + " seconds before surrendering!");
                player.sendMessage(INFO + "  Time remaining: " + (minSeconds - elapsed) + "s");
                return false;
            }
        }
        
        UUID opponentUuid = getDuelOpponent(playerUuid);
        if (opponentUuid == null) {
            player.sendMessage(DANGER + "✖ " + MUTED + "No opponent found!");
            return false;
        }
        
        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent == null || !opponent.isOnline()) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Opponent not found!");
            return false;
        }
        
        // Calculate full Elo changes (instant loss - no halving)
        EloManager.EloChangeResult result = eloManager.applySurrender(playerUuid, opponentUuid);
        
        if (result != null) {
            int playerRank = eloManager.getPlayerRank(playerUuid);
            int opponentRank = eloManager.getPlayerRank(opponentUuid);
            
            // Notify surrendering player
            player.sendMessage("");
            player.sendMessage(DANGER + "╔═════════════════════════════════╗");
            player.sendMessage(DANGER + "║" + MUTED + "      🏳️ YOU SURRENDERED      " + DANGER + "║");
            player.sendMessage(DANGER + "╚═════════════════════════════════╝");
            player.sendMessage("");
            player.sendMessage(MUTED + "  ⚡ " + result.loserChange + " Elo (instant loss)");
            player.sendMessage(INFO + "  🏆 Rank: #" + playerRank + " / " + eloManager.getTotalPlayers());
            player.sendMessage("");
            
            // Notify opponent
            opponent.sendMessage("");
            opponent.sendMessage(SUCCESS + "╔═════════════════════════════════╗");
            opponent.sendMessage(SUCCESS + "║" + ACCENT + "   🏆 OPPONENT SURRENDERED    " + SUCCESS + "║");
            opponent.sendMessage(SUCCESS + "╚═════════════════════════════════╝");
            opponent.sendMessage("");
            opponent.sendMessage(INFO + "  ⚡ +" + result.winnerChange + " Elo");
            opponent.sendMessage(INFO + "  🏆 Rank: #" + opponentRank + " / " + eloManager.getTotalPlayers());
            opponent.sendMessage("");
        }
        
        // Restore inventories and clean up
        restorePlayerInventory(player);
        player.clearActivePotionEffects();
        
        restorePlayerInventory(opponent);
        opponent.clearActivePotionEffects();
        
        // Free arena
        var arena = plugin.getArenaManager().getPlayerArena(player);
        if (arena != null) {
            plugin.getArenaManager().freeArena(arena.getId());
        }
        
        // Remove from arena tracking
        plugin.getArenaManager().removePlayer(player);
        plugin.getArenaManager().removePlayer(opponent);
        
        // Clear active duel
        activeDuels.remove(playerUuid);
        activeDuels.remove(opponentUuid);
        
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Duel ended by surrender: " + player.getName() + " surrendered to " + opponent.getName());
        }
        
        return true;
    }
    
    /**
     * Check if player is waiting for matchmaking.
     */
    public boolean isWaitingForMatchmaking(UUID uuid) {
        return waitingForMatchmaking.contains(uuid);
    }
    
    /**
     * Get matchmaking wait time in seconds for a player.
     */
    public int getMatchmakingWaitTime(UUID uuid) {
        return matchmakingWaitTime.getOrDefault(uuid, 0);
    }
    
    /**
     * Get count of outgoing duel requests for a player.
     */
    public int getOutgoingRequests(UUID uuid) {
        if (duelRequests.containsKey(uuid)) {
            return 1;
        }
        return 0;
    }
    
    /**
     * Get count of incoming duel requests for a player.
     */
    public int getIncomingRequests(UUID uuid) {
        int count = 0;
        for (UUID requester : duelRequests.keySet()) {
            if (duelRequests.get(requester).equals(uuid)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Get countdown seconds remaining for pending duel (match found).
     * Returns null if no pending duel.
     */
    public Integer getPendingDuelCountdown(UUID uuid) {
        return pendingDuels.get(uuid);
    }
    
    /**
     * Check if player is in pending duel (countdown phase).
     */
    public boolean isInPendingDuel(UUID uuid) {
        return pendingDuels.containsKey(uuid);
    }
    
    /**
     * Decrement pending duel countdowns for all pending duels.
     * Called by scoreboard updater.
     */
    public void decrementPendingDuels() {
        for (UUID uuid : pendingDuels.keySet()) {
            int current = pendingDuels.get(uuid);
            if (current > 0) {
                pendingDuels.put(uuid, current - 1);
            }
        }
    }

    /**
     * Send a duel request from one player to another.
     */
    public boolean sendDuelRequest(Player requester, Player target) {
        if (requester.equals(target)) {
            requester.sendMessage(DANGER + "✖ " + MUTED + "You cannot duel yourself!");
            return false;
        }
        
        // Check if requester is already in a duel
        if (hasActiveDuel(requester.getUniqueId())) {
            requester.sendMessage(DANGER + "✖ " + MUTED + "You are already in a duel!");
            return false;
        }
        
        // Edge case: If requester is in matchmaking, remove them
        if (isWaitingForMatchmaking(requester.getUniqueId())) {
            waitingForMatchmaking.remove(requester.getUniqueId());
            requester.sendMessage(INFO + "➖ " + MUTED + "Removed from matchmaking");
        }
        
        // Check if target is already in a duel
        if (hasActiveDuel(target.getUniqueId())) {
            requester.sendMessage(DANGER + "✖ " + MUTED + target.getName() + " is already in a duel!");
            return false;
        }
        
        // Edge case: If target is in matchmaking, notify sender
        if (isWaitingForMatchmaking(target.getUniqueId())) {
            requester.sendMessage(INFO + "ℹ " + MUTED + target.getName() + " is currently in matchmaking queue.");
            requester.sendMessage(INFO + "  You can still send a request, but they may be matched soon.");
            // Do NOT remove them - let them stay in matchmaking in case request expires
        }
        
        // Check if target already has a pending request from this requester
        if (duelRequests.containsKey(requester.getUniqueId()) && 
            duelRequests.get(requester.getUniqueId()).equals(target.getUniqueId())) {
            requester.sendMessage(DANGER + "✖ " + MUTED + "You already have a pending request to " + target.getName() + "!");
            return false;
        }
        
        // Check if target already has ANY pending request (from anyone)
        for (Map.Entry<UUID, UUID> entry : duelRequests.entrySet()) {
            if (entry.getValue().equals(target.getUniqueId())) {
                requester.sendMessage(DANGER + "✖ " + MUTED + target.getName() + " already has a pending duel request!");
                return false;
            }
        }
        
        // Check 10 second request cooldown for sender
        Long lastRequest = requestCooldowns.get(requester.getUniqueId());
        if (lastRequest != null && System.currentTimeMillis() - lastRequest < REQUEST_COOLDOWN_MS) {
            long remaining = (REQUEST_COOLDOWN_MS - (System.currentTimeMillis() - lastRequest)) / 1000;
            requester.sendMessage(DANGER + "⏳ " + MUTED + "Wait " + remaining + "s before sending another request!");
            return false;
        }
        
        // Check main duel cooldown
        var playerData = eloManager.getPlayerData(requester.getUniqueId());
        if (playerData == null) {
            playerData = eloManager.getOrCreatePlayerData(requester.getUniqueId(), requester.getName());
        }
        
        long lastDuel = playerData.getLastDuelTime();
        long cooldown = configManager.getDuelCooldown() * 1000L;
        if (System.currentTimeMillis() - lastDuel < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - lastDuel)) / 1000;
            requester.sendMessage(DANGER + "⏳ " + MUTED + "Duel cooldown: " + remaining + "s remaining");
            return false;
        }
        
        // Set request cooldown
        requestCooldowns.put(requester.getUniqueId(), System.currentTimeMillis());
        
        // Send request
        duelRequests.put(requester.getUniqueId(), target.getUniqueId());
        
        requester.sendMessage(SUCCESS + "✓ " + INFO + "Duel request sent to " + ACCENT + target.getName() + INFO + "!");
        target.sendMessage(INFO + "⚔️  " + ACCENT + requester.getName() + MUTED + " challenges you to a duel!");
        target.sendMessage(SUCCESS + "✅ " + MUTED + "Type " + INFO + "/duel accept " + requester.getName() + MUTED + " to fight!");
        
        // Auto-expire after timeout
        final UUID reqUuid = requester.getUniqueId();
        final UUID tarUuid = target.getUniqueId();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duelRequests.containsKey(reqUuid) && duelRequests.get(reqUuid).equals(tarUuid)) {
                duelRequests.remove(reqUuid);
                if (requester.isOnline()) {
                    requester.sendMessage(MUTED + "⏰ " + "Duel request to " + ACCENT + target.getName() + MUTED + " expired");
                }
            }
        }, configManager.getRequestTimeout() * 20L);
        
        return true;
    }

    /**
     * Accept a duel request.
     */
    public boolean acceptDuelRequest(Player acceptor, String requesterName) {
        Player requester = Bukkit.getPlayer(requesterName);
        
        if (requester == null) {
            acceptor.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
            return false;
        }
        
        // Edge case: If acceptor is in matchmaking, remove them
        if (isWaitingForMatchmaking(acceptor.getUniqueId())) {
            waitingForMatchmaking.remove(acceptor.getUniqueId());
            acceptor.sendMessage(INFO + "➖ " + MUTED + "Removed from matchmaking");
        }
        
        // Edge case: If requester is in matchmaking, remove them (they sent a manual request)
        if (isWaitingForMatchmaking(requester.getUniqueId())) {
            waitingForMatchmaking.remove(requester.getUniqueId());
        }
        
        UUID reqUuid = requester.getUniqueId();
        UUID accUuid = acceptor.getUniqueId();
        
        if (!duelRequests.containsKey(reqUuid) || !duelRequests.get(reqUuid).equals(accUuid)) {
            acceptor.sendMessage(DANGER + "✖ " + MUTED + "No pending duel request from that player!");
            return false;
        }
        
        // Remove request
        duelRequests.remove(reqUuid);
        
        // Start duel
        startDuel(requester, acceptor);
        
        return true;
    }

    /**
     * Start a duel between two players.
     * This is the internal method that actually starts the duel after countdown.
     */
    private void startDuelInternal(Player player1, Player player2, ArenaManager.Arena arena) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("=== DUEL START ===");
            plugin.getLogger().info("Player 1: " + player1.getName() + " | Player 2: " + player2.getName());
        }
        
        // Get arena spawn locations
        Location spawn1 = arena.getSpawn1();
        Location spawn2 = arena.getSpawn2();
        
        if (spawn1 == null || spawn2 == null) {
            player1.sendMessage(DANGER + "✖ " + MUTED + "Arena spawn points not configured! Contact an admin.");
            player2.sendMessage(DANGER + "✖ " + MUTED + "Arena spawn points not configured! Contact an admin.");
            return;
        }
        
        // Save inventories and locations FIRST, before any teleport or countdown
        savePlayerInventory(player1);
        savePlayerInventory(player2);
        
        // Send pre-duel instructions
        sendPreDuelInstructionsPreTeleport(player1, player2, arena);
        
        // Start countdown BEFORE teleport (gives players time to react)
        startPreTeleportCountdown(player1, player2, arena);
    }
    
    /**
     * Send pre-duel instructions BEFORE teleport.
     */
    private void sendPreDuelInstructionsPreTeleport(Player player1, Player player2, ArenaManager.Arena arena) {
        player1.sendMessage("");
        player1.sendMessage(PRIMARY + "╔════════════════════════════════════════════════╗");
        player1.sendMessage(PRIMARY + "║" + ACCENT + "              📋 DUEL INSTRUCTIONS             " + PRIMARY + "║");
        player1.sendMessage(PRIMARY + "╚════════════════════════════════════════════════╝");
        player1.sendMessage("");
        player1.sendMessage(INFO + "  🎯 " + MUTED + "Opponent: " + ACCENT + player2.getName());
        player1.sendMessage(INFO + "  🏟️  " + MUTED + "Arena: " + arena.getId());
        
        player2.sendMessage("");
        player2.sendMessage(PRIMARY + "╔════════════════════════════════════════════════╗");
        player2.sendMessage(PRIMARY + "║" + ACCENT + "              📋 DUEL INSTRUCTIONS             " + PRIMARY + "║");
        player2.sendMessage(PRIMARY + "╚════════════════════════════════════════════════╝");
        player2.sendMessage("");
        player2.sendMessage(INFO + "  🎯 " + MUTED + "Opponent: " + ACCENT + player1.getName());
        player2.sendMessage(INFO + "  🏟️  " + MUTED + "Arena: " + arena.getId());
    }
    
    /**
     * Start countdown BEFORE teleporting to arena.
     */
    private void startPreTeleportCountdown(Player player1, Player player2, ArenaManager.Arena arena) {
        CountdownManager countdown = plugin.getCountdownManager();
        
        // Track pending duels for scoreboard countdown display
        int teleportSeconds = configManager.getTeleportCountdownSeconds();
        int duelSeconds = configManager.getDuelStartCountdownSeconds();
        int totalCountdown = teleportSeconds + duelSeconds;
        
        pendingDuels.put(player1.getUniqueId(), totalCountdown);
        pendingDuels.put(player2.getUniqueId(), totalCountdown);
        
        // Show pre-teleport countdown
        countdown.startTeleportCountdown(player1, player2, () -> {
            // After teleport countdown, teleport and start duel countdown
            finishDuelStart(player1, player2, arena);
        });
    }
    
    /**
     * Actually teleport and start duel (called after pre-teleport countdown).
     */
    private void finishDuelStart(Player player1, Player player2, ArenaManager.Arena arena) {
        // Check if players are still online - activeDuels will be checked at the end
        // during duel countdown start, not here (pendingDuels is the indicator for countdown phase)
        if (!player1.isOnline() || !player2.isOnline()) {
            // One of the players went offline, cancel
            pendingDuels.remove(player1.getUniqueId());
            pendingDuels.remove(player2.getUniqueId());
            plugin.getCountdownManager().cancelCountdowns(player1.getUniqueId(), player2.getUniqueId());
            return;
        }
        
        Location spawn1 = arena.getSpawn1();
        Location spawn2 = arena.getSpawn2();
        
        // Apply UHC kit AFTER countdown, just before teleport
        applyUHCKit(player1);
        applyUHCKit(player2);
        
        // Teleport to arena
        player1.teleport(spawn1);
        player2.teleport(spawn2);
        
        // Apply potion effects after teleport
        applyPotionEffects(player1);
        applyPotionEffects(player2);
        
        // Clear health and food
        player1.setHealth(player1.getMaxHealth());
        player2.setHealth(player2.getMaxHealth());
        player1.setFoodLevel(20);
        player2.setFoodLevel(20);
        
        // Mark arena as in use
        plugin.getArenaManager().occupyArena(arena.getId(), player1);
        plugin.getArenaManager().occupyArena(arena.getId(), player2);
        
        // Set as active duel NOW so subsequent checks work
        activeDuels.put(player1.getUniqueId(), player2.getUniqueId());
        activeDuels.put(player2.getUniqueId(), player1.getUniqueId());
        
        // Send instructions for after teleport
        sendPreDuelInstructions(player1, player2, arena);
        
        // Start duel countdown (20 seconds before FIGHT!)
        startDuelCountdown(player1, player2, arena);
    }
    
    /**
     * Send pre-duel instructions to both players.
     */
    private void sendPreDuelInstructions(Player player1, Player player2, ArenaManager.Arena arena) {
        // Instructions for player 1
        player1.sendMessage("");
        player1.sendMessage(ACCENT + "  📦 Kit includes:");
        player1.sendMessage(MUTED + "    • Diamond Sword & Bow");
        player1.sendMessage(MUTED + "    • 64 Arrows, 10 Golden Apples");
        player1.sendMessage(MUTED + "    • Full " + configManager.getHelmet().toLowerCase() + " armor");
        player1.sendMessage(MUTED + "    • Shield, 16 Cobwebs, 64 Oak Planks");
        player1.sendMessage(MUTED + "    • Water & Lava Buckets");
        player1.sendMessage("");
        player1.sendMessage(INFO + "  ⚔️  " + MUTED + "Type " + ACCENT + "/surrender " + MUTED + "to forfeit (half Elo penalty)");
        player1.sendMessage(INFO + "  💀 " + MUTED + "Last one standing wins!");
        player1.sendMessage("");
        
        // Instructions for player 2
        player2.sendMessage("");
        player2.sendMessage(ACCENT + "  📦 Kit includes:");
        player2.sendMessage(MUTED + "    • Diamond Sword & Bow");
        player2.sendMessage(MUTED + "    • 64 Arrows, 10 Golden Apples");
        player2.sendMessage(MUTED + "    • Full " + configManager.getHelmet().toLowerCase() + " armor");
        player2.sendMessage(MUTED + "    • Shield, 16 Cobwebs, 64 Oak Planks");
        player2.sendMessage(MUTED + "    • Water & Lava Buckets");
        player2.sendMessage("");
        player2.sendMessage(INFO + "  ⚔️  " + MUTED + "Type " + ACCENT + "/surrender " + MUTED + "to forfeit (half Elo penalty)");
        player2.sendMessage(INFO + "  💀 " + MUTED + "Last one standing wins!");
        player2.sendMessage("");
    }
    
    /**
     * Start duel with countdown timer.
     */
    private void startDuelCountdown(Player player1, Player player2, ArenaManager.Arena arena) {
        CountdownManager countdown = plugin.getCountdownManager();
        
        // Track pending duels for scoreboard countdown display
        int teleportSeconds = configManager.getTeleportCountdownSeconds();
        int duelSeconds = configManager.getDuelStartCountdownSeconds();
        int totalCountdown = teleportSeconds + duelSeconds;
        
        pendingDuels.put(player1.getUniqueId(), totalCountdown);
        pendingDuels.put(player2.getUniqueId(), totalCountdown);
        
        // Show countdown for both players
        countdown.startTeleportCountdown(player1, player2, () -> {
            // After teleport countdown, start FIGHT countdown
            countdown.startDuelCountdown(player1, player2, () -> {
                // Duel officially begins - remove from pending and record start time
                pendingDuels.remove(player1.getUniqueId());
                pendingDuels.remove(player2.getUniqueId());
                
                // Send start messages
                sendDuelStartMessages(player1, player2, arena);
            });
        });
    }
    
    /**
     * Send duel start messages after countdown.
     */
    private void sendDuelStartMessages(Player player1, Player player2, ArenaManager.Arena arena) {
        // Record the duel start time for surrender time check
        duelStartTimes.put(player1.getUniqueId(), System.currentTimeMillis());
        duelStartTimes.put(player2.getUniqueId(), System.currentTimeMillis());
        
        // Notify players
        player1.sendMessage("");
        player1.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        player1.sendMessage(ACCENT + "║" + SUCCESS + "    ⚔️ DUEL STARTED! ⚔️    " + ACCENT + "║");
        player1.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        player1.sendMessage("");
        player1.sendMessage(INFO + "  🎯 vs " + ACCENT + player2.getName());
        player1.sendMessage(INFO + "  🏟️  Arena: " + arena.getId());
        player1.sendMessage(PRIMARY + "  ⚔️  FIGHT!");
        player1.sendMessage("");
        
        player2.sendMessage("");
        player2.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        player2.sendMessage(ACCENT + "║" + SUCCESS + "    ⚔️ DUEL STARTED! ⚔️    " + ACCENT + "║");
        player2.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        player2.sendMessage("");
        player2.sendMessage(INFO + "  🎯 vs " + ACCENT + player1.getName());
        player2.sendMessage(INFO + "  🏟️  Arena: " + arena.getId());
        player2.sendMessage(PRIMARY + "  ⚔️  FIGHT!");
        player2.sendMessage("");
    }

    /**
     * Start a duel between two players (called from acceptDuelRequest).
     */
    public void startDuel(Player player1, Player player2) {
        // Check if arena system is available
        if (!plugin.getArenaManager().isArenaSystemAvailable()) {
            plugin.getLogger().severe("Arena system not available - FAWE may not be installed!");
            player1.sendMessage(DANGER + "⚠ " + MUTED + "Duel system unavailable! FAWE not found.");
            player2.sendMessage(DANGER + "⚠ " + MUTED + "Duel system unavailable! FAWE not found.");
            return;
        }
        
        // Get available arena
        var arenaOpt = plugin.getArenaManager().getAvailableArena();
        
        if (arenaOpt.isEmpty()) {
            plugin.getLogger().warning("No available arenas for duel!");
            player1.sendMessage(DANGER + "✖ " + MUTED + "No arenas available! Try again later.");
            player2.sendMessage(DANGER + "✖ " + MUTED + "No arenas available! Try again later.");
            return;
        }
        
        var arena = arenaOpt.get();
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Using Arena #" + arena.getId() + " at offset X: " + arena.getOffsetX());
        }
        
        // Start duel with countdown
        startDuelInternal(player1, player2, arena);
    }

    private void savePlayerInventory(Player player) {
        // Save inventory and armor
        playerInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
        playerArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
        
        // Save location (create a copy to avoid reference issues)
        playerLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public void restorePlayerInventory(Player player) {
        if (playerInventories.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(playerInventories.remove(player.getUniqueId()));
        }
        if (playerArmor.containsKey(player.getUniqueId())) {
            player.getInventory().setArmorContents(playerArmor.remove(player.getUniqueId()));
        }
        
        // Restore location
        if (playerLocations.containsKey(player.getUniqueId())) {
            Location savedLoc = playerLocations.remove(player.getUniqueId());
            World world = Bukkit.getWorld(savedLoc.getWorld().getName());
            if (world != null) {
                // Create location with the saved world
                Location restoreLoc = new Location(world, savedLoc.getX(), savedLoc.getY(), savedLoc.getZ(), 
                    savedLoc.getYaw(), savedLoc.getPitch());
                player.teleport(restoreLoc);
            } else {
                // World doesn't exist, send to spawn
                player.teleport(Bukkit.getWorld("world").getSpawnLocation());
                plugin.getLogger().warning("Could not restore world for " + player.getName() + ", sent to spawn");
            }
        }
    }

    /**
     * Apply UHC-style PvP kit to a player.
     */
    private void applyUHCKit(Player player) {
        player.getInventory().clear();
        
        ConfigManager kit = configManager;
        
        // Diamond Sword
        player.getInventory().setItem(0, createUnbreakable(Material.DIAMOND_SWORD));
        
        // Bow
        player.getInventory().setItem(1, createUnbreakable(Material.BOW));
        
        // Arrows
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, kit.getArrows()));
        
        // Golden Apples
        player.getInventory().setItem(8, new ItemStack(Material.GOLDEN_APPLE, 10));
        
        // Armor
        player.getInventory().setHelmet(createUnbreakable(Material.valueOf(kit.getHelmet())));
        player.getInventory().setChestplate(createUnbreakable(Material.valueOf(kit.getChestplate())));
        player.getInventory().setLeggings(createUnbreakable(Material.valueOf(kit.getLeggings())));
        player.getInventory().setBoots(createUnbreakable(Material.valueOf(kit.getBoots())));
        
        // Shield
        ItemStack shield = createUnbreakable(Material.SHIELD);
        player.getInventory().setItem(9, shield);
        
        // Cobwebs
        player.getInventory().setItem(3, new ItemStack(Material.COBWEB, 16));
        
        // Oak Planks
        player.getInventory().setItem(4, new ItemStack(Material.OAK_PLANKS, 64));
        
        // Buckets
        player.getInventory().setItem(5, new ItemStack(Material.WATER_BUCKET));
        player.getInventory().setItem(6, new ItemStack(Material.LAVA_BUCKET));
    }

    private ItemStack createUnbreakable(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyPotionEffects(Player player) {
        player.clearActivePotionEffects();
        // Speed II for 3 minutes
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 180, 1, true, false));
        // Strength II for 3 minutes
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 180, 1, true, false));
    }

    /**
     * End a duel and apply Elo changes.
     */
    public void endDuel(UUID winnerUuid, UUID loserUuid) {
        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = Bukkit.getPlayer(loserUuid);
        
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("=== DUEL END ===");
            plugin.getLogger().info("Winner: " + (winner != null ? winner.getName() : winnerUuid) + " (+" + 
                (eloManager.getPlayerData(winnerUuid) != null ? eloManager.getPlayerData(winnerUuid).getElo() : "?") + " Elo)");
            plugin.getLogger().info("Loser: " + (loser != null ? loser.getName() : loserUuid) + " (-" + 
                (eloManager.getPlayerData(loserUuid) != null ? eloManager.getPlayerData(loserUuid).getElo() : "?") + " Elo)");
        }
        
        // Apply Elo changes
        EloManager.EloChangeResult result = eloManager.applyDuelResult(winnerUuid, loserUuid);
        
        if (result != null) {
            int winnerRank = eloManager.getPlayerRank(winnerUuid);
            int loserRank = eloManager.getPlayerRank(loserUuid);
            
            if (winner != null && winner.isOnline()) {
                restorePlayerInventory(winner);
                winner.clearActivePotionEffects();
                
                // Win message
                winner.sendMessage("");
                winner.sendMessage(SUCCESS + "╔═════════════════════════════════╗");
                winner.sendMessage(SUCCESS + "║" + ACCENT + "       🎉 YOU WON! 🎉       " + SUCCESS + "║");
                winner.sendMessage(SUCCESS + "╚═════════════════════════════════╝");
                winner.sendMessage("");
                winner.sendMessage(INFO + "  ⚡ +" + result.winnerChange + " Elo");
                winner.sendMessage(INFO + "  🏆 Rank: #" + winnerRank + " / " + eloManager.getTotalPlayers());
                winner.sendMessage("");
            }
            
            if (loser != null && loser.isOnline()) {
                restorePlayerInventory(loser);
                loser.clearActivePotionEffects();
                
                // Loss message
                loser.sendMessage("");
                loser.sendMessage(DANGER + "╔═════════════════════════════════╗");
                loser.sendMessage(DANGER + "║" + MUTED + "       💀 YOU LOST 💀       " + DANGER + "║");
                loser.sendMessage(DANGER + "╚═════════════════════════════════╝");
                loser.sendMessage("");
                loser.sendMessage(MUTED + "  ⚡ " + result.loserChange + " Elo");
                loser.sendMessage(INFO + "  🏆 Rank: #" + loserRank + " / " + eloManager.getTotalPlayers());
                loser.sendMessage("");
            }
        }
        
        // Free the arena (if we can find it)
        if (winner != null) {
            var arena = plugin.getArenaManager().getPlayerArena(winner);
            if (arena != null) {
                plugin.getArenaManager().freeArena(arena.getId());
            }
        }
        
        // Remove players from arena tracking
        if (winner != null) {
            plugin.getArenaManager().removePlayer(winner);
        }
        if (loser != null) {
            plugin.getArenaManager().removePlayer(loser);
        }
        
        // Clear active duel
        activeDuels.remove(winnerUuid);
        activeDuels.remove(loserUuid);
        
        // Clear duel start times
        duelStartTimes.remove(winnerUuid);
        duelStartTimes.remove(loserUuid);
    }

    public boolean hasActiveDuel(UUID uuid) {
        return activeDuels.containsKey(uuid);
    }

    /**
     * Cancel a duel without a winner (e.g., when admin ends it or player disconnects).
     * This handles BOTH players in the duel.
     */
    public void cancelDuel(UUID playerUuid) {
        // Get opponent first
        UUID opponentUuid = activeDuels.get(playerUuid);
        
        // Cancel countdown tasks if in countdown phase
        plugin.getCountdownManager().cancelCountdowns(playerUuid, opponentUuid);
        
        // Clear pending duels (in case in countdown phase)
        pendingDuels.remove(playerUuid);
        if (opponentUuid != null) {
            pendingDuels.remove(opponentUuid);
        }
        
        // Restore inventory and location for player
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            restorePlayerInventory(player);
            player.clearActivePotionEffects();
            plugin.getArenaManager().removePlayer(player);
            player.sendMessage(DANGER + "⚔️ " + MUTED + "Your duel was cancelled!");
        }
        
        // Clean up player data
        playerInventories.remove(playerUuid);
        playerArmor.remove(playerUuid);
        playerLocations.remove(playerUuid);
        activeDuels.remove(playerUuid);
        duelStartTimes.remove(playerUuid);
        
        // Handle opponent
        if (opponentUuid != null) {
            Player opponent = Bukkit.getPlayer(opponentUuid);
            if (opponent != null && opponent.isOnline()) {
                restorePlayerInventory(opponent);
                opponent.clearActivePotionEffects();
                plugin.getArenaManager().removePlayer(opponent);
                opponent.sendMessage(DANGER + "⚔️ " + MUTED + "Your duel was cancelled!");
            }
            
            // Clean up opponent data
            playerInventories.remove(opponentUuid);
            playerArmor.remove(opponentUuid);
            playerLocations.remove(opponentUuid);
            activeDuels.remove(opponentUuid);
            duelStartTimes.remove(opponentUuid);
        }
        
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Duel cancelled for player " + (player != null ? player.getName() : playerUuid));
        }
    }

    public UUID getDuelOpponent(UUID uuid) {
        return activeDuels.get(uuid);
    }

    public void removeDuelRequest(UUID requester) {
        duelRequests.remove(requester);
    }

    public boolean hasPendingRequest(UUID requester, UUID target) {
        return duelRequests.containsKey(requester) && duelRequests.get(requester).equals(target);
    }
}