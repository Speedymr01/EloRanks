package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

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
        
        // Check if player is in countdown (either pendingDuels OR countdownPairs)
        // countdownPairs covers the teleport countdown phase (before pendingDuels is populated)
        if (duelManager.isInPendingDuel(deadPlayer.getUniqueId()) || 
            duelManager.isInCountdown(deadPlayer.getUniqueId())) {
            plugin.getLogger().info("Player " + deadPlayer.getName() + " died during countdown. Cancelling duel...");
            
            // Cancel the duel countdown
            duelManager.cancelDuel(deadPlayer.getUniqueId());
            
            // Allow death screen but keep inventory
            event.setDeathMessage(null);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            // DO NOT set health back to full - that causes stuck death screen
            return;
        }
        
        // Check if the dead player is in an active duel (after countdown)
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
                
                // DON'T force respawn - let player click the respawn button naturally
            }
        }
    }
}