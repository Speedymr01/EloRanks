package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.data.PlayerData;
import com.tdm.eloranks.manager.EloManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command handler for /leaderboard command.
 */
public class LeaderboardCommand implements CommandExecutor {

    private final EloRanks plugin;

    public LeaderboardCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EloManager eloManager = plugin.getEloManager();
        
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                page = Math.max(1, page);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number!");
                return true;
            }
        }
        
        int perPage = 10;
        int totalPlayers = eloManager.getTotalPlayers();
        int totalPages = (int) Math.ceil((double) totalPlayers / perPage);
        
        // Ensure page is valid
        page = Math.min(page, totalPages);
        
        List<PlayerData> leaderboard = eloManager.getLeaderboard();
        
        // Header
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "        EloRanks Leaderboard");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        sender.sendMessage(ChatColor.GRAY + "Page " + page + " of " + Math.max(1, totalPages));
        sender.sendMessage("");
        
        // Calculate start and end indices
        int startIndex = (page - 1) * perPage;
        int endIndex = Math.min(startIndex + perPage, leaderboard.size());
        
        // Send leaderboard entries
        for (int i = startIndex; i < endIndex; i++) {
            PlayerData pd = leaderboard.get(i);
            int rank = i + 1;
            
            ChatColor rankColor = getRankColor(rank);
            String rankSymbol = getRankSymbol(rank);
            
            sender.sendMessage(rankColor + rankSymbol + " #" + rank + " " + ChatColor.WHITE + pd.getPlayerName() + 
                ChatColor.YELLOW + " - " + ChatColor.GOLD + pd.getElo() + ChatColor.YELLOW + " Elo" +
                ChatColor.GRAY + " [" + pd.getWins() + "W/" + pd.getLosses() + "L]");
        }
        
        sender.sendMessage("");
        
        // Navigation hints
        if (page < totalPages) {
            sender.sendMessage(ChatColor.GRAY + "Use /leaderboard " + (page + 1) + " for more");
        } else if (page > 1) {
            sender.sendMessage(ChatColor.GRAY + "Use /leaderboard " + (page - 1) + " for previous");
        }
        
        return true;
    }

    private ChatColor getRankColor(int rank) {
        switch (rank) {
            case 1: return ChatColor.GOLD;
            case 2: return ChatColor.GRAY;
            case 3: return ChatColor.YELLOW;
            case 4:
            case 5:
                return ChatColor.WHITE;
            default:
                return ChatColor.GRAY;
        }
    }

    private String getRankSymbol(int rank) {
        switch (rank) {
            case 1: return "★";
            case 2: return "☆";
            case 3: return "◈";
            default: return "●";
        }
    }
}
