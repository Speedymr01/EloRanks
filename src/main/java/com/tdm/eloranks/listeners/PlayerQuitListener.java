package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player quit/disconnect during duels.
 */
public class PlayerQuitListener implements Listener {

    private final EloRanks plugin;

    public PlayerQuitListener(EloRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DuelManager duelManager = plugin.getDuelManager();
        
        // Check if player was in an active duel
        if (duelManager.hasActiveDuel(player.getUniqueId())) {
            var opponentUuid = duelManager.getDuelOpponent(player.getUniqueId());
            var opponent = plugin.getServer().getPlayer(opponentUuid);
            
            plugin.getLogger().info("Player " + player.getName() + " disconnected during duel. Ending duel...");
            
            if (opponent != null && opponent.isOnline()) {
                // Opponent wins by default (the disconnected player forfeits)
                duelManager.endDuel(opponentUuid, player.getUniqueId());
            } else {
                // No opponent online, just cancel
                duelManager.cancelDuel(player.getUniqueId());
            }
        }
        
        // Clean up any pending duel requests from this player
        duelManager.removeDuelRequest(player.getUniqueId());
    }
}