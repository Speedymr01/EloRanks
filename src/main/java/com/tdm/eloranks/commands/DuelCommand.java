package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.DuelManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command handler for /duel command.
 */
public class DuelCommand implements CommandExecutor {

    private final EloRanks plugin;

    public DuelCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        DuelManager duelManager = plugin.getDuelManager();

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "request":
            case "challenge":
            case "send":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /duel request <player>");
                    return true;
                }
                handleDuelRequest(player, args[1], duelManager);
                break;
                
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /duel accept <player>");
                    return true;
                }
                handleDuelAccept(player, args[1], duelManager);
                break;
                
            case "decline":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /duel decline <player>");
                    return true;
                }
                handleDuelDecline(player, args[1], duelManager);
                break;
                
            case "help":
                showHelp(player);
                break;
                
            default:
                // Treat as direct challenge: /duel <player>
                handleDuelRequest(player, args[0], duelManager);
                break;
        }

        return true;
    }

    private void handleDuelRequest(Player player, String targetName, DuelManager duelManager) {
        var target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }
        
        boolean success = duelManager.sendDuelRequest(player, target);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Duel request sent to " + target.getName() + "!");
        }
    }

    private void handleDuelAccept(Player player, String requesterName, DuelManager duelManager) {
        boolean success = duelManager.acceptDuelRequest(player, requesterName);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "You accepted the duel!");
        }
    }

    private void handleDuelDecline(Player player, String requesterName, DuelManager duelManager) {
        var requester = plugin.getServer().getPlayer(requesterName);
        
        if (requester == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + requesterName);
            return;
        }
        
        duelManager.removeDuelRequest(requester.getUniqueId());
        
        player.sendMessage(ChatColor.YELLOW + "You declined the duel request from " + requester.getName());
        requester.sendMessage(ChatColor.RED + player.getName() + " declined your duel request!");
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══ Duel Commands ═══");
        player.sendMessage(ChatColor.YELLOW + "/duel <player> " + ChatColor.GRAY + "- Challenge a player to duel");
        player.sendMessage(ChatColor.YELLOW + "/duel accept <player> " + ChatColor.GRAY + "- Accept a duel request");
        player.sendMessage(ChatColor.YELLOW + "/duel decline <player> " + ChatColor.GRAY + "- Decline a duel request");
    }
}
