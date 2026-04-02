package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for handling duel deaths and ending matches.
 */
public class PlayerDeathListener implements Listener {

    private final EloRanks plugin;

    public PlayerDeathListener(EloRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        
        DuelManager duelManager = plugin.getDuelManager();
        
        // Check if the dead player is in an active duel
        if (duelManager.hasActiveDuel(deadPlayer.getUniqueId())) {
            // Get the duel opponent
            var opponentUuid = duelManager.getDuelOpponent(deadPlayer.getUniqueId());
            var opponent = plugin.getServer().getPlayer(opponentUuid);
            
            if (opponent != null && opponent.isOnline()) {
                // Cancel death message for duel arena
                event.setDeathMessage(null);
                
                // Prevent keeping inventory/levels
                event.setKeepInventory(false);
                event.setKeepLevel(false);
                
                // End the duel - opponent wins (this restores inventories and teleports back)
                duelManager.endDuel(opponentUuid, deadPlayer.getUniqueId());
                
                // Set health to full (for winner)
                opponent.setHealth(opponent.getMaxHealth());
                
                // Schedule respawn and teleport back for loser after respawn
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (deadPlayer.isOnline()) {
                            // Force respawn
                            deadPlayer.spigot().respawn();
                            
                            // Schedule teleport after respawn (need to wait a bit for respawn to complete)
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (deadPlayer.isOnline()) {
                                        // Restore inventory and teleport back to original location
                                        duelManager.restorePlayerInventory(deadPlayer);
                                        deadPlayer.setHealth(deadPlayer.getMaxHealth());
                                    }
                                }
                            }.runTaskLater(plugin, 5L);
                        }
                    }
                }.runTask(plugin);
            }
        }
    }
}
