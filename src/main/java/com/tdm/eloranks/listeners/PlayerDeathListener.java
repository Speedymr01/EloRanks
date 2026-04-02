package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.ChatColor;
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
        
        // Check if the dead player is in an active duel
        if (duelManager.hasActiveDuel(deadPlayer.getUniqueId())) {
            // Get the duel opponent
            var opponentUuid = duelManager.getDuelOpponent(deadPlayer.getUniqueId());
            var opponent = plugin.getServer().getPlayer(opponentUuid);
            
            if (opponent != null && opponent.isOnline()) {
                // End the duel - opponent wins
                duelManager.endDuel(opponentUuid, deadPlayer.getUniqueId());
                
                // Cancel death message for duel arena
                event.setDeathMessage(null);
                
                // Heal both players (restoration will handle location)
                deadPlayer.setHealth(deadPlayer.getMaxHealth());
                opponent.setHealth(opponent.getMaxHealth());
            }
        }
    }
}
