package com.tdm.eloranks.listeners;

import com.tdm.eloranks.EloRanks;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Handles block breaking during duels.
 * Players can only break OAK_PLANKS during duels.
 */
public class BlockBreakListener implements Listener {

    private final EloRanks plugin;

    public BlockBreakListener(EloRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Check if player is in a duel
        if (!plugin.getDuelManager().hasActiveDuel(event.getPlayer().getUniqueId())) {
            return;
        }

        // Player is in a duel - check what they're breaking
        Material blockType = event.getBlock().getType();

        // Allow OAK_PLANKS to be broken
        if (blockType == Material.OAK_PLANKS) {
            return;
        }

        // For any other block, cancel the event (no message as per requirements)
        event.setCancelled(true);
    }
}
