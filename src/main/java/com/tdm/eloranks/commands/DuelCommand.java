package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for /duel command.
 */
public class DuelCommand implements CommandExecutor, TabCompleter {

    private final EloRanks plugin;
    
    // Color scheme
    private final ChatColor PRIMARY = ChatColor.AQUA;
    private final ChatColor SECONDARY = ChatColor.DARK_AQUA;
    private final ChatColor ACCENT = ChatColor.GOLD;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor DANGER = ChatColor.RED;
    private final ChatColor INFO = ChatColor.YELLOW;
    private final ChatColor MUTED = ChatColor.GRAY;

    public DuelCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle /surrender command separately
        if (label.equalsIgnoreCase("surrender") || label.equalsIgnoreCase("forfeit") || label.equalsIgnoreCase("sq")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(DANGER + "✖ " + MUTED + "This command can only be used by players!");
                return true;
            }
            Player player = (Player) sender;
            plugin.getDuelManager().surrender(player);
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "request" -> {
                if (args.length < 2) {
                    player.sendMessage(DANGER + "✖ " + MUTED + "Usage: /duel request <player>");
                    return true;
                }
                handleDuelRequest(player, args[1]);
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(DANGER + "✖ " + MUTED + "Usage: /duel accept <player>");
                    return true;
                }
                handleDuelAccept(player, args[1]);
            }
            case "decline" -> {
                if (args.length < 2) {
                    player.sendMessage(DANGER + "✖ " + MUTED + "Usage: /duel decline <player>");
                    return true;
                }
                handleDuelDecline(player, args[1]);
            }
            case "match", "queue" -> handleMatchmaking(player);
            case "cancel" -> handleCancelMatchmaking(player);
            case "surrender" -> handleSurrender(player);
            case "help" -> showHelp(player);
            default -> handleDuelRequest(player, args[0]);
        }

        return true;
    }

    private void handleDuelRequest(Player player, String targetName) {
        var target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Player not found: " + targetName);
            return;
        }
        
        plugin.getDuelManager().sendDuelRequest(player, target);
    }

    private void handleDuelAccept(Player player, String requesterName) {
        boolean success = plugin.getDuelManager().acceptDuelRequest(player, requesterName);
        
        if (success) {
            player.sendMessage(SUCCESS + "✓ " + INFO + "You accepted the duel! GLHF!");
        }
    }

    private void handleDuelDecline(Player player, String requesterName) {
        var requester = plugin.getServer().getPlayer(requesterName);
        
        if (requester == null) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Player not found: " + requesterName);
            return;
        }
        
        plugin.getDuelManager().removeDuelRequest(requester.getUniqueId());
        
        player.sendMessage(INFO + "➖ " + MUTED + "You declined " + ACCENT + requester.getName() + MUTED + "'s duel request");
        requester.sendMessage(DANGER + "✖ " + ACCENT + player.getName() + MUTED + " declined your duel request");
    }
    
    private void handleMatchmaking(Player player) {
        plugin.getDuelManager().toggleMatchmaking(player);
    }
    
    private void handleCancelMatchmaking(Player player) {
        plugin.getDuelManager().cancelMatchmaking(player);
    }
    
    private void handleSurrender(Player player) {
        boolean success = plugin.getDuelManager().surrender(player);
        if (!success) {
            // Error message handled by surrender method
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage("----------------");
        player.sendMessage("  Duel Commands  ");
        player.sendMessage("----------------");
        player.sendMessage("");
        player.sendMessage("");
        player.sendMessage(INFO + "  ⚔️  /duel <player> " + MUTED + "- Challenge to 1v1");
        player.sendMessage(INFO + "  ✅ /duel request <player> " + MUTED + "- Send duel request");
        player.sendMessage(INFO + "  ✅ /duel accept <player> " + MUTED + "- Accept challenge");
        player.sendMessage(INFO + "  ❌ /duel decline <player> " + MUTED + "- Decline challenge");
        player.sendMessage(INFO + "  🎯 /duel match " + MUTED + "- Enter matchmaking queue");
        player.sendMessage(INFO + "  ⏹️  /duel cancel " + MUTED + "- Cancel matchmaking");
        player.sendMessage(INFO + "  🏳️  /surrender " + MUTED + "- Forfeit (half Elo penalty)");
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 0) {
            // Full subcommand list for first argument
            return List.of("request", "accept", "decline", "match", "cancel", "surrender", "help");
        }

        String current = args[args.length - 1].toLowerCase();
        
        if (args.length == 1) {
            // Match subcommands
            List<String> subcommands = List.of("request", "accept", "decline", "match", "cancel", "surrender", "help");
            return subcommands.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
        }

        // Second argument - player names for request/accept/decline
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("request") || sub.equals("accept") || sub.equals("decline")) {
                List<String> matches = new java.util.ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (current.isEmpty() || p.getName().toLowerCase().startsWith(current)) {
                        matches.add(p.getName());
                    }
                }
                return matches;
            }
        }

        return List.of();
    }
}