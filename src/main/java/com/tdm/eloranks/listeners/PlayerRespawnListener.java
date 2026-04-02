package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listener for handling player respawn in duel arena.
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
        
        // Check if player was in a duel and is in the duel arena world
        if (duelManager.hasActiveDuel(player.getUniqueId())) {
            String arenaWorld = plugin.getConfigManager().getArenaWorld();
            if (player.getWorld().getName().equals(arenaWorld)) {
                // Player respawned in arena world - set immediate respawn
                // The respawn location will be handled by the death listener
                // But we can also ensure they don't get stuck on death screen
                
                // Get arena spawn location for the player
                var arena = plugin.getArenaManager().getPlayerArena(player);
                if (arena != null) {
                    // Set respawn to arena spawn (gives immediate feel)
                    Location spawn = duelManager.getDuelOpponent(player.getUniqueId()) != null ? 
                        arena.getSpawn1() : arena.getSpawn2();
                    if (spawn != null) {
                        event.setRespawnLocation(spawn);
                    }
                }
            }
        }
    }
}