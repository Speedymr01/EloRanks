package com.tdm.eloranks.manager;

import com.tdm.eloranks.EloRanks;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Manages countdown timers and titles for duels.
 */
public class CountdownManager {

    private final EloRanks plugin;
    
    public CountdownManager(EloRanks plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Show countdown before teleporting to arena.
     */
    public void startTeleportCountdown(Player player1, Player player2, Runnable onComplete) {
        int seconds = plugin.getConfigManager().getTeleportCountdownSeconds();
        
        if (plugin.getConfigManager().isCountdownChatEnabled()) {
            player1.sendMessage("§e═══════════════════════════════════════");
            player2.sendMessage("§e═══════════════════════════════════════");
        }
        
        // Simple countdown for teleport
        runSimpleCountdown(player1, player2, seconds, onComplete);
    }
    
    /**
     * Run a simple countdown without color progression.
     */
    private void runSimpleCountdown(Player player1, Player player2, int seconds, Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            private int currentSecond = seconds;
            
            @Override
            public void run() {
                if (currentSecond > 0) {
                    if (player1.isOnline()) {
                        Title title = Title.title(
                            LegacyComponentSerializer.legacyAmpersand().deserialize("§e§l" + currentSecond),
                            LegacyComponentSerializer.legacyAmpersand().deserialize("§eTeleporting to arena...")
                        );
                        player1.showTitle(title);
                        player1.sendMessage("§e⏳ " + currentSecond + "...");
                    }
                    if (player2.isOnline()) {
                        Title title = Title.title(
                            LegacyComponentSerializer.legacyAmpersand().deserialize("§e§l" + currentSecond),
                            LegacyComponentSerializer.legacyAmpersand().deserialize("§eTeleporting to arena...")
                        );
                        player2.showTitle(title);
                        player2.sendMessage("§e⏳ " + currentSecond + "...");
                    }
                    
                    currentSecond--;
                    Bukkit.getScheduler().runTaskLater(plugin, this, 20L);
                } else {
                    // Done - run the callback
                    if (onComplete != null) {
                        Bukkit.getScheduler().runTask(plugin, onComplete);
                    }
                }
            }
        });
    }
    
    /**
     * Show countdown before duel starts (after teleport).
     * Color progression based on config settings.
     */
    public void startDuelCountdown(Player player1, Player player2, Runnable onComplete) {
        int seconds = plugin.getConfigManager().getDuelStartCountdownSeconds();
        
        if (plugin.getConfigManager().isCountdownChatEnabled()) {
            // Send initial header
            player1.sendMessage("§b═══════════════════════════════════════════════════");
            player2.sendMessage("§b═══════════════════════════════════════════════════");
        }
        
        // Start the countdown with color progression
        runColoredCountdown(player1, player2, seconds, onComplete);
    }
    
    /**
     * Run countdown with specific colors for each second.
     */
    private void runColoredCountdown(Player player1, Player player2, int seconds, Runnable onComplete) {
        // Use scheduler to run each second
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            private int currentSecond = seconds;
            
            @Override
            public void run() {
                if (currentSecond > 0) {
                    // Show countdown to both players
                    showColoredSecond(player1, currentSecond);
                    showColoredSecond(player2, currentSecond);
                    
                    currentSecond--;
                    
                    // Schedule next second
                    Bukkit.getScheduler().runTaskLater(plugin, this, 20L);
                } else {
                    // Show GO!
                    showGoMessage(player1);
                    showGoMessage(player2);
                    
                    // Run the completion callback
                    if (onComplete != null) {
                        Bukkit.getScheduler().runTask(plugin, onComplete);
                    }
                }
            }
        });
    }
    
    /**
     * Show a specific second with the appropriate color.
     */
    private void showColoredSecond(Player player, int second) {
        if (!player.isOnline()) return;
        
        String colorCode;
        String number;
        
        switch (second) {
            case 20:
            case 19:
            case 18:
            case 17:
            case 16:
            case 15:
            case 14:
            case 13:
            case 12:
            case 11:
            case 10:
            case 9:
            case 8:
            case 7:
            case 6:
                // Blue
                colorCode = "§9";
                number = "§l" + second;
                break;
            case 5:
                // Dark Red
                colorCode = "§4";
                number = "§l" + second;
                break;
            case 4:
                // Red
                colorCode = "§c";
                number = "§l" + second;
                break;
            case 3:
                // Orange
                colorCode = "§6";
                number = "§l" + second;
                break;
            case 2:
                // Yellow
                colorCode = "§e";
                number = "§l" + second;
                break;
            case 1:
                // Full green (0, 255, 0)
                colorCode = "§a";
                number = "§l" + second;
                break;
            default:
                colorCode = "§f";
                number = "§l" + second;
        }
        
        // Show title
        Title title = Title.title(
            LegacyComponentSerializer.legacyAmpersand().deserialize(number),
            LegacyComponentSerializer.legacyAmpersand().deserialize(colorCode + "DUEL STARTS IN...")
        );
        player.showTitle(title);
        
        // Send chat message
        player.sendMessage(colorCode + "⏳ " + number + " " + colorCode + "...");
    }
    
    /**
     * Show GO! message when countdown ends.
     */
    private void showGoMessage(Player player) {
        if (!player.isOnline()) return;
        
        // Full green: 0, 255, 0 - using §a which is the closest standard Minecraft color
        Title goTitle = Title.title(
            LegacyComponentSerializer.legacyAmpersand().deserialize("§a§lGO!"),
            LegacyComponentSerializer.legacyAmpersand().deserialize("§e§l⚔️ FIGHT! ⚔️")
        );
        player.showTitle(goTitle);
        
        player.sendMessage("§a§l▶ GO! ⚔️");
        player.sendMessage("§b═══════════════════════════════════════════════════");
    }
    
    /**
     * Show a simple title to a player.
     */
    public void showTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title t = Title.title(
            LegacyComponentSerializer.legacyAmpersand().deserialize(title),
            LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
            Title.Times.of(java.time.Duration.ofSeconds(fadeIn), java.time.Duration.ofSeconds(stay), java.time.Duration.ofSeconds(fadeOut))
        );
        player.showTitle(t);
    }
}