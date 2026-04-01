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
    
    // Active duel requests (requester -> target)
    private final Map<UUID, UUID> duelRequests = new ConcurrentHashMap<>();
    
    // Active duels (player1 -> player2)
    private final Map<UUID, UUID> activeDuels = new ConcurrentHashMap<>();
    
    // Stored inventories for when duel ends
    private final Map<UUID, ItemStack[]> playerInventories = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> playerArmor = new ConcurrentHashMap<>();

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
            requester.sendMessage(ChatColor.RED + "You cannot duel yourself!");
            return false;
        }
        
        if (hasActiveDuel(requester.getUniqueId()) || hasActiveDuel(target.getUniqueId())) {
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
        // Get arena spawn locations
        var spawn1Opt = plugin.getWorldManager().getArenaSpawnLocation(1);
        var spawn2Opt = plugin.getWorldManager().getArenaSpawnLocation(2);
        
        if (spawn1Opt.isEmpty() || spawn2Opt.isEmpty()) {
            player1.sendMessage(ChatColor.RED + "Arena not configured! Contact an admin.");
            player2.sendMessage(ChatColor.RED + "Arena not configured! Contact an admin.");
            return;
        }
        
        Location spawn1 = spawn1Opt.get();
        Location spawn2 = spawn2Opt.get();
        
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
        
        // Clear health and food after teleporting
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

    /**
     * Apply UHC-style PvP kit to a player.
     */
    private void applyUHCKit(Player player) {
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        
        ConfigManager kit = configManager;
        
        // Diamond Sword
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setUnbreakable(true);
        sword.setItemMeta(swordMeta);
        player.getInventory().setItem(0, sword);
        
        // Bow
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setUnbreakable(true);
        bow.setItemMeta(bowMeta);
        player.getInventory().setItem(1, bow);
        
        // Arrows (64)
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, kit.getArrows()));
        
        // Golden Apples (10)
        String[] foodParts = kit.getFood().split(":");
        int foodAmount = foodParts.length > 1 ? Integer.parseInt(foodParts[1]) : 10;
        player.getInventory().setItem(8, new ItemStack(Material.GOLDEN_APPLE, foodAmount));
        
        // Armor
        player.getInventory().setHelmet(createUnbreakable(Material.valueOf(kit.getHelmet())));
        player.getInventory().setChestplate(createUnbreakable(Material.valueOf(kit.getChestplate())));
        player.getInventory().setLeggings(createUnbreakable(Material.valueOf(kit.getLeggings())));
        player.getInventory().setBoots(createUnbreakable(Material.valueOf(kit.getBoots())));
        
        // Shield in offhand
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemMeta shieldMeta = shield.getItemMeta();
        shieldMeta.setUnbreakable(true);
        shield.setItemMeta(shieldMeta);
        player.getInventory().setItem(9, shield);
        
        // Cobwebs (16)
        player.getInventory().setItem(3, new ItemStack(Material.COBWEB, 16));
        
        // Oak Planks (64)
        player.getInventory().setItem(4, new ItemStack(Material.OAK_PLANKS, 64));
        
        // Water Bucket
        player.getInventory().setItem(5, new ItemStack(Material.WATER_BUCKET));
        
        // Lava Bucket
        player.getInventory().setItem(6, new ItemStack(Material.LAVA_BUCKET));
        
        // Speed II potions (2)
        player.getInventory().addItem(createPotion(PotionEffectType.SPEED, 2, 180));
        player.getInventory().addItem(createPotion(PotionEffectType.SPEED, 2, 180));
        
        // Strength II potions (2)
        player.getInventory().addItem(createPotion(PotionEffectType.STRENGTH, 2, 180));
        player.getInventory().addItem(createPotion(PotionEffectType.STRENGTH, 2, 180));
    }

    private ItemStack createUnbreakable(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void applyPotionEffects(Player player) {
        // Clear existing effects first
        player.clearActivePotionEffects();
        
        // Apply Speed II for 3 minutes (180 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 180, 1, true, false));
        
        // Apply Strength II for 3 minutes (180 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 180, 1, true, false));
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
            // Get new ranks
            int winnerRank = eloManager.getPlayerRank(winnerUuid);
            int loserRank = eloManager.getPlayerRank(loserUuid);
            
            if (winner != null && winner.isOnline()) {
                restorePlayerInventory(winner);
                // Clear potion effects
                winner.clearActivePotionEffects();
                
                // Send messages
                String eloMsg = configManager.getConfig().getString("messages.elo-gain", "&a+%elo% Elo! &7(Rank: #%rank%)");
                eloMsg = eloMsg.replace("%elo%", String.valueOf(result.winnerChange)).replace("%rank%", String.valueOf(winnerRank));
                winner.sendMessage(eloMsg);
            }
            
            if (loser != null && loser.isOnline()) {
                restorePlayerInventory(loser);
                // Clear potion effects
                loser.clearActivePotionEffects();
                
                // Send messages
                String eloMsg = configManager.getConfig().getString("messages.elo-lost", "&c%elo% Elo! &7(Rank: #%rank%)");
                eloMsg = eloMsg.replace("%elo%", String.valueOf(result.loserChange)).replace("%rank%", String.valueOf(loserRank));
                loser.sendMessage(eloMsg);
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