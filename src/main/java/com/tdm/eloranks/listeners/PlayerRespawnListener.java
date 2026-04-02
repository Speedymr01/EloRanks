package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listener for handling player respawn in duel arena.
 * When player clicks respawn, verify they were in a duel, then restore.
 */
public class PlayerRespawnListener implements Listener {

    private final EloRanks plugin;

    public PlayerRespawnListener(EloRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DuelManager duelManager = plugin.getDuelManager();
        
        // Only process if player was in an active duel (not just in arena world)
        if (!duelManager.hasActiveDuel(player.getUniqueId())) {
            return; // Not a duel respawn - don't touch anything
        }
        
        // Player was in a duel - restore inventory and teleport back
        duelManager.restorePlayerInventory(player);
        player.setHealth(player.getMaxHealth());
        
        // Note: We don't set the respawn location here because we want the player
        // to click the respawn button naturally. The restore happens when they do.
    }
}