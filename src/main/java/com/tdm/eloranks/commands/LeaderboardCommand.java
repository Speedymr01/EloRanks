package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.data.PlayerData;
import com.tdm.eloranks.manager.EloManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for /leaderboard command.
 */
public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final EloRanks plugin;
    
    // Color scheme
    private final ChatColor PRIMARY = ChatColor.AQUA;
    private final ChatColor ACCENT = ChatColor.GOLD;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor DANGER = ChatColor.RED;
    private final ChatColor INFO = ChatColor.YELLOW;
    private final ChatColor MUTED = ChatColor.GRAY;

    public LeaderboardCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EloManager eloManager = plugin.getEloManager();
        
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid page number!");
                return true;
            }
        }
        
        int perPage = 10;
        int totalPlayers = eloManager.getTotalPlayers();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalPlayers / perPage));
        page = Math.min(page, totalPages);
        
        List<PlayerData> leaderboard = eloManager.getLeaderboard();
        
        sender.sendMessage("----------------");
        sender.sendMessage("  EloRanks Leaderboard  ");
        sender.sendMessage("----------------");
        sender.sendMessage("");
        sender.sendMessage(MUTED + "   📖 Page " + INFO + page + MUTED + " of " + INFO + totalPages + MUTED + " | Total: " + INFO + totalPlayers + MUTED + " players");
        sender.sendMessage("");
        
        int startIndex = (page - 1) * perPage;
        int endIndex = Math.min(startIndex + perPage, leaderboard.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            PlayerData pd = leaderboard.get(i);
            int rank = i + 1;
            
            String rankStr = getRankDisplay(rank);
            ChatColor rankColor = getRankColor(rank);
            
            sender.sendMessage(rankColor + rankStr + " " + pd.getPlayerName() + 
                INFO + " → " + ACCENT + pd.getElo() + MUTED + " Elo " + MUTED + "[" + 
                SUCCESS + pd.getWins() + MUTED + "W/" + DANGER + pd.getLosses() + MUTED + "L]");
        }
        
        sender.sendMessage("");
        
        if (page < totalPages) {
            sender.sendMessage(MUTED + "   ➡️  Use " + INFO + "/leaderboard " + (page + 1) + MUTED + " for more");
        }
        if (page > 1) {
            sender.sendMessage(MUTED + "   ⬅️  Use " + INFO + "/leaderboard " + (page - 1) + MUTED + " for previous");
        }
        
        sender.sendMessage("");
        
        return true;
    }

    private ChatColor getRankColor(int rank) {
        switch (rank) {
            case 1: return ChatColor.GOLD;
            case 2: return ChatColor.GRAY;
            case 3: return ChatColor.YELLOW;
            case 4:
            case 5: return ChatColor.WHITE;
            default: return MUTED;
        }
    }

    private String getRankDisplay(int rank) {
        switch (rank) {
            case 1: return "👑 #1";
            case 2: return "🥈 #2";
            case 3: return "🥉 #3";
            default: return "   #" + rank;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> matches = new java.util.ArrayList<>();
            
            // Add page numbers
            for (int i = 1; i <= 10; i++) {
                if (String.valueOf(i).startsWith(current)) {
                    matches.add(String.valueOf(i));
                }
            }
            
            return matches;
        }
        
        return List.of();
    }
}