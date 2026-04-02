package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * Listener for preventing player actions during duel countdown phases.
 * - Prevents damage during duel countdown (20s frozen phase)
 * - Prevents movement during duel countdown (freeze)
 * - Prevents item pickup during duel countdown
 * 
 * Note: During teleport countdown (5s), players can move and fight freely.
 */
public class PlayerActionListener implements Listener {

    private final EloRanks plugin;

    public PlayerActionListener(EloRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        DuelManager duelManager = plugin.getDuelManager();
        
        // Only cancel damage during duel countdown (isInPendingDuel) - NOT during teleport countdown
        // During teleport countdown, players can fight
        if (duelManager.isInPendingDuel(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        DuelManager duelManager = plugin.getDuelManager();
        
        // Freeze player ONLY during duel countdown (isInPendingDuel) - not during teleport countdown
        // During teleport countdown, players can move freely
        if (duelManager.isInPendingDuel(player.getUniqueId())) {
            // Check if position actually changed (not just camera rotation)
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                // Cancel movement - teleport back to from location
                player.teleport(event.getFrom());
            }
            // If only rotation changed (looking around), allow it
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        DuelManager duelManager = plugin.getDuelManager();
        
        // Prevent item pickup ONLY during duel countdown - not during teleport countdown
        if (duelManager.isInPendingDuel(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}