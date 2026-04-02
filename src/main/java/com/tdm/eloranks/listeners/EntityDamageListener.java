package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Listener for preventing damage during pre-teleport countdown.
 */
public class EntityDamageListener implements Listener {

    private final EloRanks plugin;

    public EntityDamageListener(EloRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Check if entity is a player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        DuelManager duelManager = plugin.getDuelManager();
        
        // Check if player is in pending duel (during countdown)
        if (duelManager.isInPendingDuel(player.getUniqueId())) {
            // Cancel damage during countdown
            event.setCancelled(true);
        }
    }
}