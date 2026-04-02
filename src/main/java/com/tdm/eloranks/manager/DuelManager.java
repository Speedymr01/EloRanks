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
    
    /**
     * Check if a player is currently in matchmaking queue.
     */
    public boolean isWaitingForMatchmaking(UUID playerUuid) {
        return waitingForMatchmaking.contains(playerUuid);
    }
    
    // Track when each duel started (for surrender time check)
    private final Map<UUID, Long> duelStartTimes = new ConcurrentHashMap<>();
    
    // Track pending duels (match found, countdown started) - key is player UUID, value is countdown seconds remaining
    private final Map<UUID, Integer> pendingDuels = new ConcurrentHashMap<>();
    
    // Track countdown pairs - who is dueling with who during countdown
    private final Map<UUID, UUID> countdownPairs = new ConcurrentHashMap<>();
    
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
                        if (configManager.isDuelRequestChatEnabled()) {
                            target.sendMessage(SUCCESS + "✅ " + MUTED + "Duel request sent to " + ACCENT + player.getName() + INFO + "!");
                            target.sendMessage(SUCCESS + "✅ " + MUTED + "Type " + INFO + "/duel accept " + player.getName() + MUTED + " to fight!");
                            player.sendMessage(SUCCESS + "✓ " + INFO + "Duel request sent to " + ACCENT + target.getName() + INFO + "!");
                            target.sendMessage(INFO + "⚔️  " + ACCENT + player.getName() + MUTED + " challenges you to a duel!");
                            target.sendMessage(SUCCESS + "✅ " + MUTED + "Type " + INFO + "/duel accept " + player.getName() + MUTED + " to fight!");
                        }
                    }
                }
            }
            
            // Add player to matchmaking
            waitingForMatchmaking.add(playerUuid);
            
            // Auto-expire after timeout (for pending requests)
            if (duelRequests.containsKey(playerUuid)) {
                final UUID reqUuid = playerUuid;
                final UUID tarUuid = duelRequests.get(playerUuid);
                final Player target = Bukkit.getPlayer(tarUuid);
                 
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (duelRequests.containsKey(reqUuid) && duelRequests.get(reqUuid).equals(tarUuid)) {
                        duelRequests.remove(reqUuid);
                        Player requester = Bukkit.getPlayer(reqUuid);
                        if (requester != null && requester.isOnline()) {
                            requester.sendMessage(MUTED + "⏰ " + "Duel request to " + ACCENT + (target != null ? target.getName() : "player") + MUTED + " expired");
                        }
                    }
                }, configManager.getRequestTimeout() * 20L);
            }
            
            return true;
        }
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
        if (!configManager.isPreDuelInstructionsEnabled()) return;
        
        player1.sendMessage("----------------");
        player1.sendMessage("  DUEL INSTRUCTIONS  ");
        player1.sendMessage("----------------");
        player1.sendMessage("");
        player1.sendMessage(INFO + "  Opponent: " + ACCENT + player2.getName());
        player1.sendMessage(INFO + "  Arena: " + arena.getId());
        
        player2.sendMessage("----------------");
        player2.sendMessage("  DUEL INSTRUCTIONS  ");
        player2.sendMessage("----------------");
        player2.sendMessage("");
        player2.sendMessage(INFO + "  Opponent: " + ACCENT + player1.getName());
        player2.sendMessage(INFO + "  Arena: " + arena.getId());
    }
    
    /**
     * Start countdown BEFORE teleporting to arena.
     * Players can move during this phase (no freeze).
     */
    private void startPreTeleportCountdown(Player player1, Player player2, ArenaManager.Arena arena) {
        CountdownManager countdown = plugin.getCountdownManager();
        
        // Track countdown pairs from the start (both teleport + duel countdowns)
        countdownPairs.put(player1.getUniqueId(), player2.getUniqueId());
        countdownPairs.put(player2.getUniqueId(), player1.getUniqueId());
        
        // DO NOT add to pendingDuels here - players can move during teleport countdown
        // pendingDuels will be added AFTER teleport in startDuelCountdown
        
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
            countdownPairs.remove(player1.getUniqueId());
            countdownPairs.remove(player2.getUniqueId());
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
        if (!configManager.isPreDuelInstructionsEnabled()) return;
        
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
     * Start duel with countdown timer (second countdown after teleport).
     */
    private void startDuelCountdown(Player player1, Player player2, ArenaManager.Arena arena) {
        CountdownManager countdown = plugin.getCountdownManager();
        
        // countdownPairs is already set from startPreTeleportCountdown, no need to set again
        
        // Update pending duels for scoreboard countdown display (just the duel start seconds)
        int duelSeconds = configManager.getDuelStartCountdownSeconds();
        pendingDuels.put(player1.getUniqueId(), duelSeconds);
        pendingDuels.put(player2.getUniqueId(), duelSeconds);
        
        // Show duel start countdown for both players (NOT teleport countdown again!)
        countdown.startDuelCountdown(player1, player2, () -> {
            // Duel officially begins - remove from pending and countdown pairs
            pendingDuels.remove(player1.getUniqueId());
            pendingDuels.remove(player2.getUniqueId());
            countdownPairs.remove(player1.getUniqueId());
            countdownPairs.remove(player2.getUniqueId());
            
            // Send start messages
            sendDuelStartMessages(player1, player2, arena);
        });
    }
    
    /**
     * Send duel start messages after countdown.
     */
    private void sendDuelStartMessages(Player player1, Player player2, ArenaManager.Arena arena) {
        // Record the duel start time for surrender time check
        duelStartTimes.put(player1.getUniqueId(), System.currentTimeMillis());
        duelStartTimes.put(player2.getUniqueId(), System.currentTimeMillis());
        
        // Only send messages if enabled in config
        if (!configManager.isDuelStartChatEnabled()) return;
        
        // Notify players
        player1.sendMessage("---------------");
        player1.sendMessage("  DUEL STARTED!  ");
        player1.sendMessage("---------------");
        player1.sendMessage("");
        player1.sendMessage(INFO + "  vs " + ACCENT + player2.getName());
        player1.sendMessage(INFO + "  Arena: " + arena.getId());
        player1.sendMessage(PRIMARY + "  FIGHT!");
        player1.sendMessage("");
        
        player2.sendMessage("---------------");
        player2.sendMessage("  DUEL STARTED!  ");
        player2.sendMessage("---------------");
        player2.sendMessage("");
        player2.sendMessage(INFO + "  vs " + ACCENT + player1.getName());
        player2.sendMessage(INFO + "  Arena: " + arena.getId());
        player2.sendMessage(PRIMARY + "  FIGHT!");
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
        // Restore inventory first
        if (playerInventories.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(playerInventories.remove(player.getUniqueId()));
        }
        if (playerArmor.containsKey(player.getUniqueId())) {
            player.getInventory().setArmorContents(playerArmor.remove(player.getUniqueId()));
        }
        
        // Restore location - use async teleport for better handling
        if (playerLocations.containsKey(player.getUniqueId())) {
            Location savedLoc = playerLocations.remove(player.getUniqueId());
            World world = savedLoc.getWorld();
            if (world == null) {
                // Try to get world by name
                world = Bukkit.getWorld(savedLoc.getWorld().getName());
            }
            if (world != null) {
                // Create location with proper world
                Location restoreLoc = new Location(world, savedLoc.getX(), savedLoc.getY(), savedLoc.getZ(), 
                    savedLoc.getYaw(), savedLoc.getPitch());
                // Use safe teleport - this works even for dead/respawning players
                player.teleport(restoreLoc);
            } else {
                // World doesn't exist, send to spawn
                World defaultWorld = Bukkit.getWorld("world");
                if (defaultWorld != null) {
                    player.teleport(defaultWorld.getSpawnLocation());
                }
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
        
        // Get potion config (format: "SPEED:2,STRENGTH:2" where number is amplifier)
        String potionsConfig = configManager.getPotions();
        
        if (potionsConfig != null && !potionsConfig.isEmpty()) {
            String[] potions = potionsConfig.split(",");
            for (String potion : potions) {
                String[] parts = potion.trim().split(":");
                if (parts.length == 2) {
                    try {
                        String effectName = parts[0].trim().toUpperCase();
                        int amplifier = Integer.parseInt(parts[1].trim()) - 1; // Convert 1-based to 0-based
                        
                        PotionEffectType effectType = PotionEffectType.getByKey(
                            org.bukkit.NamespacedKey.minecraft(effectName.toLowerCase())
                        );
                        
                        if (effectType != null) {
                            // Default duration: 180 ticks (3 minutes)
                            player.addPotionEffect(new PotionEffect(effectType, 180, amplifier, true, false));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid potion config: " + potion);
                    }
                }
            }
        }
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
            
            boolean chatEnabled = configManager.isDuelEndChatEnabled();
            
            if (winner != null && winner.isOnline()) {
                restorePlayerInventory(winner);
                winner.clearActivePotionEffects();
                
                // Win message - always show Elo info, use config for decorative messages
                if (chatEnabled) {
                    winner.sendMessage("---------------");
                    winner.sendMessage("  YOU WON!  ");
                    winner.sendMessage("---------------");
                    winner.sendMessage("");
                }
                winner.sendMessage(INFO + "  +" + result.winnerChange + " Elo");
                winner.sendMessage(INFO + "  Rank: #" + winnerRank + " / " + eloManager.getTotalPlayers());
                winner.sendMessage("");
            }
            
            if (loser != null && loser.isOnline()) {
                restorePlayerInventory(loser);
                loser.clearActivePotionEffects();
                
                // Loss message - always show Elo info, use config for decorative messages
                if (chatEnabled) {
                    loser.sendMessage("---------------");
                    loser.sendMessage("  YOU LOST  ");
                    loser.sendMessage("---------------");
                    loser.sendMessage("");
                }
                loser.sendMessage(MUTED + "  " + result.loserChange + " Elo");
                loser.sendMessage(INFO + "  Rank: #" + loserRank + " / " + eloManager.getTotalPlayers());
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
    
    public int getActiveDuelCount() {
        // Each duel has 2 entries, so divide by 2
        return activeDuels.size() / 2;
    }

    /**
     * Cancel a duel without a winner (e.g., when admin ends it or player disconnects).
     * This handles BOTH players in the duel - works during countdown or active duel.
     */
    public void cancelDuel(UUID playerUuid) {
        // Get opponent from countdown pairs first (during countdown phase)
        UUID opponentUuid = countdownPairs.get(playerUuid);
        
        // If not in countdown, check active duels (during active duel)
        if (opponentUuid == null) {
            opponentUuid = activeDuels.get(playerUuid);
        }
        
        // Cancel countdown tasks if in countdown phase
        if (opponentUuid != null) {
            plugin.getCountdownManager().cancelCountdowns(playerUuid, opponentUuid);
        }
        
        // Clear pending duels and countdown pairs (in case in countdown phase)
        pendingDuels.remove(playerUuid);
        countdownPairs.remove(playerUuid);
        if (opponentUuid != null) {
            pendingDuels.remove(opponentUuid);
            countdownPairs.remove(opponentUuid);
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
    
    /**
     * Get matchmaking wait time for a player.
     */
    public int getMatchmakingWaitTime(UUID playerUuid) {
        return matchmakingWaitTime.getOrDefault(playerUuid, 0);
    }
    
    /**
     * Check if player is in pending duel (countdown phase).
     */
    public boolean isInPendingDuel(UUID playerUuid) {
        return pendingDuels.containsKey(playerUuid);
    }
    
    /**
     * Check if player is in countdown (either pending duel or countdown pairs).
     */
    public boolean isInCountdown(UUID playerUuid) {
        return pendingDuels.containsKey(playerUuid) || countdownPairs.containsKey(playerUuid);
    }
    
    /**
     * Get pending duel countdown seconds remaining.
     */
    public Integer getPendingDuelCountdown(UUID playerUuid) {
        return pendingDuels.get(playerUuid);
    }
    
    /**
     * Decrement all pending duel countdowns.
     */
    public void decrementPendingDuels() {
        Set<UUID> keys = new HashSet<>(pendingDuels.keySet());
        for (UUID uuid : keys) {
            int current = pendingDuels.get(uuid);
            if (current <= 0) {
                pendingDuels.remove(uuid);
            } else {
                pendingDuels.put(uuid, current - 1);
            }
        }
        
        // Increment matchmaking wait times
        for (UUID uuid : waitingForMatchmaking) {
            matchmakingWaitTime.merge(uuid, 1, Integer::sum);
        }
    }
    
    /**
     * Get outgoing duel request count for a player.
     */
    public int getOutgoingRequests(UUID playerUuid) {
        return duelRequests.containsKey(playerUuid) ? 1 : 0;
    }
    
    /**
     * Get incoming duel request count for a player.
     */
    public int getIncomingRequests(UUID playerUuid) {
        int count = 0;
        for (UUID requester : duelRequests.keySet()) {
            if (duelRequests.get(requester).equals(playerUuid)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Send a duel request from one player to another.
     * @return true if request was sent, false if failed
     */
    public boolean sendDuelRequest(Player requester, Player target) {
        if (hasActiveDuel(requester.getUniqueId()) || hasActiveDuel(target.getUniqueId())) {
            requester.sendMessage(DANGER + "✖ " + MUTED + "One of the players is already in a duel!");
            return false;
        }
        
        duelRequests.put(requester.getUniqueId(), target.getUniqueId());
        
        if (configManager.isDuelRequestChatEnabled()) {
            requester.sendMessage(SUCCESS + "✅ " + MUTED + "Duel request sent to " + ACCENT + target.getName() + INFO + "!");
            target.sendMessage(INFO + "⚔️  " + ACCENT + requester.getName() + MUTED + " challenges you to a duel!");
            target.sendMessage(SUCCESS + "✅ " + MUTED + "Type " + INFO + "/duel accept " + requester.getName() + MUTED + " to fight!");
        }
        
        // Auto-expire after timeout
        final UUID reqUuid = requester.getUniqueId();
        final UUID tarUuid = target.getUniqueId();
        final Player reqPlayer = requester;
        final Player tarPlayer = target;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duelRequests.containsKey(reqUuid) && duelRequests.get(reqUuid).equals(tarUuid)) {
                duelRequests.remove(reqUuid);
                if (reqPlayer != null && reqPlayer.isOnline()) {
                    reqPlayer.sendMessage(MUTED + "⏰ Duel request to " + ACCENT + tarPlayer.getName() + MUTED + " expired");
                }
            }
        }, configManager.getRequestTimeout() * 20L);
        
        return true;
    }
    
    /**
     * Handle player surrender.
     * @return true if surrender was successful, false otherwise
     */
    public boolean surrender(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        if (!hasActiveDuel(playerUuid)) {
            player.sendMessage(DANGER + "✖ " + MUTED + "You are not in a duel!");
            return false;
        }
        
        // Check minimum duel time
        Long startTime = duelStartTimes.get(playerUuid);
        if (startTime != null) {
            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
            int minTime = configManager.getSurrenderMinDuelTimeSeconds();
            if (elapsedSeconds < minTime) {
                player.sendMessage(DANGER + "✖ " + MUTED + "You must wait " + minTime + " seconds before surrendering!");
                return false;
            }
        }
        
        UUID opponentUuid = getDuelOpponent(playerUuid);
        
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("Player " + player.getName() + " surrendered!");
        }
        
        // End duel with opponent as winner
        endDuel(opponentUuid, playerUuid);
        return true;
    }
    
    /**
     * Cancel matchmaking for a player.
     */
    public void cancelMatchmaking(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        if (waitingForMatchmaking.contains(playerUuid)) {
            waitingForMatchmaking.remove(playerUuid);
            matchmakingWaitTime.remove(playerUuid);
            player.sendMessage(INFO + "➖ " + MUTED + "Matchmaking disabled");
        } else {
            player.sendMessage(MUTED + "You are not in matchmaking queue");
        }
    }
}