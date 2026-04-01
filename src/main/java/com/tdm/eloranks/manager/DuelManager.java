package com.tdm.eloranks.manager;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.config.ConfigManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages duel requests, matchmaking, and arena handling.
 */
public class DuelManager {

    private final EloRanks plugin;
    private final ConfigManager configManager;
    private final EloManager eloManager;
    
    // Active duel requests (requester -> target)
    private final Map<UUID, UUID> duelRequests = new ConcurrentHashMap<>();
    
    // Active duels (player1 -> player2)
    private final Map<UUID, UUID> activeDuels = new ConcurrentHashMap<>();
    
    // Stored inventories for when duel ends
    private final Map<UUID, ItemStack[]> playerInventories = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> playerArmor = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, ItemStack>> playerExtraItems = new ConcurrentHashMap<>();
    
    // Arena locations
    private Location arenaSpawn1;
    private Location arenaSpawn2;

    public DuelManager(EloRanks plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.eloManager = plugin.getEloManager();
        
        loadArenas();
    }

    private void loadArenas() {
        // Load arena locations from config
        if (configManager.getConfig().contains("arena.spawn1")) {
            arenaSpawn1 = configManager.getConfig().getLocation("arena.spawn1");
        }
        if (configManager.getConfig().contains("arena.spawn2")) {
            arenaSpawn2 = configManager.getConfig().getLocation("arena.spawn2");
        }
    }

    public void saveArenas() {
        if (arenaSpawn1 != null) {
            configManager.getConfig().set("arena.spawn1", arenaSpawn1);
        }
        if (arenaSpawn2 != null) {
            configManager.getConfig().set("arena.spawn2", arenaSpawn2);
        }
        configManager.saveConfig();
    }

    public void setArenaSpawn1(Location loc) {
        this.arenaSpawn1 = loc;
        saveArenas();
    }

    public void setArenaSpawn2(Location loc) {
        this.arenaSpawn2 = loc;
        saveArenas();
    }

    public Location getArenaSpawn1() {
        return arenaSpawn1;
    }

    public Location getArenaSpawn2() {
        return arenaSpawn2;
    }

    /**
     * Send a duel request from one player to another.
     */
    public boolean sendDuelRequest(Player requester, Player target) {
        if (requester.equals(target)) {
            requester.sendMessage(ChatColor.RED + "You cannot duel yourself!");
            return false;
        }
        
        if (hasActiveDuel(requester) || hasActiveDuel(target)) {
            requester.sendMessage(ChatColor.RED + "One of the players is already in a duel!");
            return false;
        }
        
        // Check cooldown
        long lastDuel = eloManager.getPlayerData(requester.getUniqueId()).getLastDuelTime();
        long cooldown = configManager.getDuelCooldown() * 1000L;
        if (System.currentTimeMillis() - lastDuel < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - lastDuel)) / 1000;
            requester.sendMessage(ChatColor.RED + "You must wait " + remaining + " seconds before dueling again!");
            return false;
        }
        
        // Send request
        duelRequests.put(requester.getUniqueId(), target.getUniqueId());
        
        requester.sendMessage(ChatColor.GREEN + "Duel request sent to " + target.getName() + "!");
        target.sendMessage(ChatColor.YELLOW + requester.getName() + " has challenged you to a duel!");
        target.sendMessage(ChatColor.GREEN + "/duel accept " + requester.getName() + " to accept");
        
        // Auto-expire after timeout
        final UUID reqUuid = requester.getUniqueId();
        final UUID tarUuid = target.getUniqueId();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duelRequests.containsKey(reqUuid) && duelRequests.get(reqUuid).equals(tarUuid)) {
                duelRequests.remove(reqUuid);
                if (requester.isOnline()) {
                    requester.sendMessage(ChatColor.GRAY + "Your duel request to " + target.getName() + " has expired.");
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
            acceptor.sendMessage(ChatColor.RED + "Player not found!");
            return false;
        }
        
        UUID reqUuid = requester.getUniqueId();
        UUID accUuid = acceptor.getUniqueId();
        
        if (!duelRequests.containsKey(reqUuid) || !duelRequests.get(reqUuid).equals(accUuid)) {
            acceptor.sendMessage(ChatColor.RED + "No pending duel request from that player!");
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
        if (arenaSpawn1 == null || arenaSpawn2 == null) {
            player1.sendMessage(ChatColor.RED + "Arena not configured! Contact an admin.");
            player2.sendMessage(ChatColor.RED + "Arena not configured! Contact an admin.");
            return;
        }
        
        // Save inventories
        savePlayerInventory(player1);
        savePlayerInventory(player2);
        
        // Apply kit
        applyDuelKit(player1);
        applyDuelKit(player2);
        
        // Teleport to arena
        player1.teleport(arenaSpawn1);
        player2.teleport(arenaSpawn2);
        
        // Set as active duel
        activeDuels.put(player1.getUniqueId(), player2.getUniqueId());
        activeDuels.put(player2.getUniqueId(), player1.getUniqueId());
        
        // Notify players
        player1.sendMessage(ChatColor.GOLD + "═══ Duel Started! ═══");
        player1.sendMessage(ChatColor.YELLOW + "vs " + player2.getName());
        player1.sendMessage(ChatColor.GRAY + "Fight!");
        
        player2.sendMessage(ChatColor.GOLD + "═══ Duel Started! ═══");
        player2.sendMessage(ChatColor.YELLOW + "vs " + player1.getName());
        player2.sendMessage(ChatColor.GRAY + "Fight!");
        
        // Clear their health
        player1.setHealth(player1.getMaxHealth());
        player2.setHealth(player2.getMaxHealth());
        player1.setFoodLevel(20);
        player2.setFoodLevel(20);
    }

    private void savePlayerInventory(Player player) {
        playerInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
        playerArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
    }

    public void restorePlayerInventory(Player player) {
        if (playerInventories.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(playerInventories.remove(player.getUniqueId()));
        }
        if (playerArmor.containsKey(player.getUniqueId())) {
            player.getInventory().setArmorContents(playerArmor.remove(player.getUniqueId()));
        }
    }

    private void applyDuelKit(Player player) {
        player.getInventory().clear();
        
        // Give diamond sword
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setUnbreakable(true);
        sword.setItemMeta(meta);
        player.getInventory().setItem(0, sword);
        
        // Give armor
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
        
        // Give food
        ItemStack food = new ItemStack(Material.COOKED_BEEF, 64);
        player.getInventory().setItem(8, food);
    }

    /**
     * End a duel and apply Elo changes.
     */
    public void endDuel(UUID winnerUuid, UUID loserUuid) {
        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = Bukkit.getPlayer(loserUuid);
        
        // Apply Elo changes
        EloManager.EloChangeResult result = eloManager.applyDuelResult(winnerUuid, loserUuid);
        
        if (result != null) {
            if (winner != null && winner.isOnline()) {
                restorePlayerInventory(winner);
                winner.sendMessage(ChatColor.GREEN + "You won! +" + result.winnerChange + " Elo!");
                
                int rank = eloManager.getPlayerRank(winnerUuid);
                winner.sendMessage(ChatColor.YELLOW + "Your new rank: #" + rank);
            }
            
            if (loser != null && loser.isOnline()) {
                restorePlayerInventory(loser);
                loser.sendMessage(ChatColor.RED + "You lost! " + result.loserChange + " Elo!");
                
                int rank = eloManager.getPlayerRank(loserUuid);
                loser.sendMessage(ChatColor.YELLOW + "Your new rank: #" + rank);
            }
        }
        
        // Clear active duel
        activeDuels.remove(winnerUuid);
        activeDuels.remove(loserUuid);
    }

    public boolean hasActiveDuel(UUID uuid) {
        return activeDuels.containsKey(uuid);
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
