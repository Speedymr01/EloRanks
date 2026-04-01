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
 * Command handler for /elo and /elostats commands.
 */
public class EloCommand implements CommandExecutor {

    private final EloRanks plugin;

    public EloCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        EloManager eloManager = plugin.getEloManager();

        // /elo - show own stats
        if (args.length == 0) {
            showPlayerStats(player, eloManager);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats":
            case "me":
                showPlayerStats(player, eloManager);
                break;
                
            case "top":
                showTopPlayers(player, eloManager, args);
                break;
                
            case "help":
                showHelp(player);
                break;
                
            default:
                // Check if viewing another player's stats
                if (args.length >= 1) {
                    showOtherPlayerStats(player, args[0], eloManager);
                } else {
                    showPlayerStats(player, eloManager);
                }
                break;
        }

        return true;
    }

    private void showPlayerStats(Player player, EloManager eloManager) {
        PlayerData pd = eloManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        
        player.sendMessage(ChatColor.GOLD + "═══ Your Elo Stats ═══");
        player.sendMessage(ChatColor.YELLOW + "Rank: #" + pd.getRank() + " / " + eloManager.getTotalPlayers());
        player.sendMessage(ChatColor.YELLOW + "Elo: " + pd.getElo());
        player.sendMessage(ChatColor.YELLOW + "Wins: " + ChatColor.GREEN + pd.getWins());
        player.sendMessage(ChatColor.YELLOW + "Losses: " + ChatColor.RED + pd.getLosses());
        player.sendMessage(ChatColor.YELLOW + "Draws: " + ChatColor.GRAY + pd.getDraws());
        player.sendMessage(ChatColor.YELLOW + "Win Rate: " + String.format("%.1f%%", pd.getWinRate()));
    }

    private void showOtherPlayerStats(Player player, String targetName, EloManager eloManager) {
        Player target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            // Try to find by name in data
            // For now, just say not found
            player.sendMessage(ChatColor.RED + "Player not found or not online!");
            return;
        }
        
        PlayerData pd = eloManager.getPlayerData(target.getUniqueId());
        
        if (pd == null) {
            player.sendMessage(ChatColor.RED + "Player has no stats yet!");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "═══ " + pd.getPlayerName() + "'s Stats ═══");
        player.sendMessage(ChatColor.YELLOW + "Rank: #" + pd.getRank() + " / " + eloManager.getTotalPlayers());
        player.sendMessage(ChatColor.YELLOW + "Elo: " + pd.getElo());
        player.sendMessage(ChatColor.YELLOW + "Wins: " + ChatColor.GREEN + pd.getWins());
        player.sendMessage(ChatColor.YELLOW + "Losses: " + ChatColor.RED + pd.getLosses());
        player.sendMessage(ChatColor.YELLOW + "Win Rate: " + String.format("%.1f%%", pd.getWinRate()));
    }

    private void showTopPlayers(Player player, EloManager eloManager, String[] args) {
        int count = 10;
        if (args.length > 1) {
            try {
                count = Math.min(Integer.parseInt(args[1]), 50);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number!");
                return;
            }
        }

        List<PlayerData> top = eloManager.getTopPlayers(count);
        
        player.sendMessage(ChatColor.GOLD + "═══ Top " + count + " Players ═══");
        
        for (int i = 0; i < top.size(); i++) {
            PlayerData pd = top.get(i);
            ChatColor rankColor = getRankColor(i + 1);
            player.sendMessage(rankColor + "#" + (i + 1) + " " + pd.getPlayerName() + 
                ChatColor.YELLOW + " - " + pd.getElo() + " Elo");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══ EloRanks Help ═══");
        player.sendMessage(ChatColor.YELLOW + "/elo " + ChatColor.GRAY + "- View your stats");
        player.sendMessage(ChatColor.YELLOW + "/elo stats " + ChatColor.GRAY + "- View your stats");
        player.sendMessage(ChatColor.YELLOW + "/elo <player> " + ChatColor.GRAY + "- View another player's stats");
        player.sendMessage(ChatColor.YELLOW + "/elo top [count] " + ChatColor.GRAY + "- View top players");
        player.sendMessage(ChatColor.YELLOW + "/duel <player> " + ChatColor.GRAY + "- Challenge a player");
        player.sendMessage(ChatColor.YELLOW + "/leaderboard " + ChatColor.GRAY + "- View full leaderboard");
    }

    private ChatColor getRankColor(int rank) {
        if (rank == 1) return ChatColor.GOLD;
        if (rank == 2) return ChatColor.GRAY;
        if (rank == 3) return ChatColor.YELLOW;
        return ChatColor.WHITE;
    }
}
