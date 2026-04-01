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
        // Get arena spawn locations
        var spawn1Opt = plugin.getWorldManager().getArenaSpawnLocation(1);
        var spawn2Opt = plugin.getWorldManager().getArenaSpawnLocation(2);
        
        if (spawn1Opt.isEmpty() || spawn2Opt.isEmpty()) {
            player1.sendMessage(DANGER + "✖ " + MUTED + "Arena not configured! Contact an admin.");
            player2.sendMessage(DANGER + "✖ " + MUTED + "Arena not configured! Contact an admin.");
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
        
        // Clear health and food
        player1.setHealth(player1.getMaxHealth());
        player2.setHealth(player2.getMaxHealth());
        player1.setFoodLevel(20);
        player2.setFoodLevel(20);
        
        // Set as active duel
        activeDuels.put(player1.getUniqueId(), player2.getUniqueId());
        activeDuels.put(player2.getUniqueId(), player1.getUniqueId());
        
        // Notify players
        player1.sendMessage("");
        player1.sendMessage(ACCENT + "╔══════════════════════════════════╗");
        player1.sendMessage(ACCENT + "║" + SUCCESS + "      ⚔️ DUEL STARTED! ⚔️     " + ACCENT + "║");
        player1.sendMessage(ACCENT + "╚══════════════════════════════════╝");
        player1.sendMessage("");
        player1.sendMessage(INFO + "  🎯 vs " + ACCENT + player2.getName());
        player1.sendMessage(PRIMARY + "  ⚔️  FIGHT!");
        player1.sendMessage("");
        
        player2.sendMessage("");
        player2.sendMessage(ACCENT + "╔══════════════════════════════════╗");
        player2.sendMessage(ACCENT + "║" + SUCCESS + "      ⚔️ DUEL STARTED! ⚔️     " + ACCENT + "║");
        player2.sendMessage(ACCENT + "╚══════════════════════════════════╝");
        player2.sendMessage("");
        player2.sendMessage(INFO + "  🎯 vs " + ACCENT + player1.getName());
        player2.sendMessage(PRIMARY + "  ⚔️  FIGHT!");
        player2.sendMessage("");
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
                winner.sendMessage(SUCCESS + "╔══════════════════════════════════╗");
                winner.sendMessage(SUCCESS + "║" + ACCENT + "         🎉 YOU WON! 🎉        " + SUCCESS + "║");
                winner.sendMessage(SUCCESS + "╚══════════════════════════════════╝");
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
                loser.sendMessage(DANGER + "╔══════════════════════════════════╗");
                loser.sendMessage(DANGER + "║" + MUTED + "         💀 YOU LOST 💀        " + DANGER + "║");
                loser.sendMessage(DANGER + "╚══════════════════════════════════╝");
                loser.sendMessage("");
                loser.sendMessage(MUTED + "  ⚡ " + result.loserChange + " Elo");
                loser.sendMessage(INFO + "  🏆 Rank: #" + loserRank + " / " + eloManager.getTotalPlayers());
                loser.sendMessage("");
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