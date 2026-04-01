package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
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
            case "request":
            case "challenge":
            case "send":
                if (args.length < 2) {
                    player.sendMessage(DANGER + "✖ " + MUTED + "Usage: /duel request <player>");
                    return true;
                }
                handleDuelRequest(player, args[1]);
                break;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(DANGER + "✖ " + MUTED + "Usage: /duel accept <player>");
                    return true;
                }
                handleDuelAccept(player, args[1]);
                break;
            case "decline":
                if (args.length < 2) {
                    player.sendMessage(DANGER + "✖ " + MUTED + "Usage: /duel decline <player>");
                    return true;
                }
                handleDuelDecline(player, args[1]);
                break;
            case "help":
                showHelp(player);
                break;
            default:
                handleDuelRequest(player, args[0]);
                break;
        }

        return true;
    }

    private void handleDuelRequest(Player player, String targetName) {
        var target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Player not found: " + targetName);
            return;
        }
        
        boolean success = plugin.getDuelManager().sendDuelRequest(player, target);
        
        if (success) {
            player.sendMessage(SUCCESS + "✓ " + INFO + "Duel request sent to " + ACCENT + target.getName() + INFO + "!");
        }
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

    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ACCENT + "╔══════════════════════════════════╗");
        player.sendMessage(ACCENT + "║" + PRIMARY + "        Duel Commands       " + ACCENT + "║");
        player.sendMessage(ACCENT + "╚══════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(INFO + "  ⚔️  /duel <player> " + MUTED + "- Challenge to 1v1");
        player.sendMessage(INFO + "  ✅ /duel accept <player> " + MUTED + "- Accept challenge");
        player.sendMessage(INFO + "  ❌ /duel decline <player> " + MUTED + "- Decline challenge");
        player.sendMessage("");
    }
}