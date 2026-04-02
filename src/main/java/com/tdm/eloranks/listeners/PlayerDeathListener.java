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
                
                // Set health to full
                deadPlayer.setHealth(deadPlayer.getMaxHealth());
                opponent.setHealth(opponent.getMaxHealth());
                
                // Force respawn after a tick to ensure they're not stuck on death screen
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (deadPlayer.isOnline()) {
                            deadPlayer.spigot().respawn();
                        }
                    }
                }.runTask(plugin);
            }
        }
    }
}
