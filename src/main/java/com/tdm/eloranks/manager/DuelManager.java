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
    // Stored locations for when duel ends
    private final Map<UUID, Location> playerLocations = new ConcurrentHashMap<>();

    public DuelManager(EloRanks plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.eloManager = plugin.getEloManager();
    }

    /**
     * Send a duel request from one player to another.
     */
    public boolean sendDuelRequest(Player requester, Player target) {
        if (requester.equals(target)) {
            requester.sendMessage(DANGER + "✖ " + MUTED + "You cannot duel yourself!");
            return false;
        }
        
        if (hasActiveDuel(requester.getUniqueId()) || hasActiveDuel(target.getUniqueId())) {
            requester.sendMessage(DANGER + "✖ " + MUTED + "One of the players is already in a duel!");
            return false;
        }
        
        // Check cooldown
        var playerData = eloManager.getPlayerData(requester.getUniqueId());
        if (playerData == null) {
            playerData = eloManager.getOrCreatePlayerData(requester.getUniqueId(), requester.getName());
        }
        
        long lastDuel = playerData.getLastDuelTime();
        long cooldown = configManager.getDuelCooldown() * 1000L;
        if (System.currentTimeMillis() - lastDuel < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - lastDuel)) / 1000;
            requester.sendMessage(DANGER + "⏳ " + MUTED + "Cooldown: " + remaining + "s remaining");
            return false;
        }
        
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
     */
    public void startDuel(Player player1, Player player2) {
        plugin.getLogger().info("=== DUEL START ===");
        plugin.getLogger().info("Player 1: " + player1.getName() + " | Player 2: " + player2.getName());
        
        // Get available arena
        var arenaOpt = plugin.getArenaManager().getAvailableArena();
        
        if (arenaOpt.isEmpty()) {
            plugin.getLogger().warning("No available arenas for duel!");
            player1.sendMessage(DANGER + "✖ " + MUTED + "No arenas available! Try again later.");
            player2.sendMessage(DANGER + "✖ " + MUTED + "No arenas available! Try again later.");
            return;
        }
        
        var arena = arenaOpt.get();
        plugin.getLogger().info("Using Arena #" + arena.getId() + " at offset X: " + arena.getOffsetX());
        
        // Get arena spawn locations
        Location spawn1 = arena.getSpawn1();
        Location spawn2 = arena.getSpawn2();
        
        if (spawn1 == null || spawn2 == null) {
            player1.sendMessage(DANGER + "✖ " + MUTED + "Arena spawn points not configured! Contact an admin.");
            player2.sendMessage(DANGER + "✖ " + MUTED + "Arena spawn points not configured! Contact an admin.");
            return;
        }
        
        // Save inventories
        savePlayerInventory(player1);
        savePlayerInventory(player2);
        
        // Apply UHC kit
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
        
        // Set as active duel (with arena info)
        activeDuels.put(player1.getUniqueId(), player2.getUniqueId());
        activeDuels.put(player2.getUniqueId(), player1.getUniqueId());
        
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

    private void savePlayerInventory(Player player) {
        // Save inventory and armor
        playerInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
        playerArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
        
        // Save location (create a copy to avoid reference issues)
        playerLocations.put(player.getUniqueId(), player.getLocation().clone());
        
        plugin.getLogger().info("Saved player " + player.getName() + " location: " + 
            player.getLocation().getWorld().getName() + " at (" + 
            player.getLocation().getX() + ", " + 
            player.getLocation().getY() + ", " + 
            player.getLocation().getZ() + ")");
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
                plugin.getLogger().info("Restored player " + player.getName() + " to: " + 
                    restoreLoc.getWorld().getName() + " at (" + 
                    (int)restoreLoc.getX() + ", " + 
                    (int)restoreLoc.getY() + ", " + 
                    (int)restoreLoc.getZ() + ")");
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
        
        plugin.getLogger().info("=== DUEL END ===");
        plugin.getLogger().info("Winner: " + (winner != null ? winner.getName() : winnerUuid) + " (+" + 
            (eloManager.getPlayerData(winnerUuid) != null ? eloManager.getPlayerData(winnerUuid).getElo() : "?") + " Elo)");
        plugin.getLogger().info("Loser: " + (loser != null ? loser.getName() : loserUuid) + " (-" + 
            (eloManager.getPlayerData(loserUuid) != null ? eloManager.getPlayerData(loserUuid).getElo() : "?") + " Elo)");
        
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
    }

    public boolean hasActiveDuel(UUID uuid) {
        return activeDuels.containsKey(uuid);
    }

    /**
     * Cancel a duel without a winner (e.g., when admin ends it).
     */
    public void cancelDuel(UUID playerUuid) {
        // Remove from active duels
        activeDuels.remove(playerUuid);
        // Clean up saved data (in case it wasn't cleaned up)
        playerInventories.remove(playerUuid);
        playerArmor.remove(playerUuid);
        playerLocations.remove(playerUuid);
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