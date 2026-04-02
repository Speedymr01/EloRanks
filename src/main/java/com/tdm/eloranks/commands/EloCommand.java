package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.data.PlayerData;
import com.tdm.eloranks.manager.EloManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for /er command.
 */
public class EloCommand implements CommandExecutor, TabCompleter {

    private final EloRanks plugin;
    
    // Color scheme
    private final ChatColor PRIMARY = ChatColor.AQUA;
    private final ChatColor SECONDARY = ChatColor.DARK_AQUA;
    private final ChatColor ACCENT = ChatColor.GOLD;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor DANGER = ChatColor.RED;
    private final ChatColor INFO = ChatColor.YELLOW;
    private final ChatColor MUTED = ChatColor.GRAY;

    public EloCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        EloManager eloManager = plugin.getEloManager();

        if (args.length == 0) {
            showPlayerStats(player, eloManager);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats", "me" -> showPlayerStats(player, eloManager);
            case "top" -> showTopPlayers(player, eloManager, args);
            case "help" -> showHelp(player);
            default -> {
                if (args.length >= 1) {
                    showOtherPlayerStats(player, args[0], eloManager);
                } else {
                    showPlayerStats(player, eloManager);
                }
            }
        }

        return true;
    }

    private void showPlayerStats(Player player, EloManager eloManager) {
        PlayerData pd = eloManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        
        player.sendMessage("");
        player.sendMessage(ACCENT + "╔═══════════════════════════════╗");
        player.sendMessage(ACCENT + "║" + PRIMARY + "   Your Elo Statistics   " + ACCENT + "║");
        player.sendMessage(ACCENT + "╚═══════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(INFO + "  🏆 Rank: " + ACCENT + "#" + pd.getRank() + MUTED + " / " + eloManager.getTotalPlayers());
        player.sendMessage(INFO + "  ⚡ Elo: " + ACCENT + pd.getElo());
        player.sendMessage("");
        player.sendMessage(INFO + "  ✅ Wins:    " + SUCCESS + pd.getWins());
        player.sendMessage(INFO + "  ❌ Losses: " + DANGER + pd.getLosses());
        player.sendMessage(INFO + "  ➖ Draws:  " + MUTED + pd.getDraws());
        player.sendMessage("");
        player.sendMessage(INFO + "  📊 Win Rate: " + getWinRateColor(pd.getWinRate()) + String.format("%.1f%%", pd.getWinRate()));
        player.sendMessage("");
    }

    private void showOtherPlayerStats(Player player, String targetName, EloManager eloManager) {
        Player target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Player not found or not online!");
            return;
        }
        
        PlayerData pd = eloManager.getPlayerData(target.getUniqueId());
        
        if (pd == null) {
            player.sendMessage(DANGER + "✖ " + MUTED + "Player has no stats yet!");
            return;
        }
        
        player.sendMessage("");
        player.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        player.sendMessage(ACCENT + "║" + PRIMARY + " " + target.getName() + "'s Statistics  " + ACCENT + "║");
        player.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(INFO + "  🏆 Rank: " + ACCENT + "#" + pd.getRank() + MUTED + " / " + eloManager.getTotalPlayers());
        player.sendMessage(INFO + "  ⚡ Elo: " + ACCENT + pd.getElo());
        player.sendMessage("");
        player.sendMessage(INFO + "  ✅ Wins:    " + SUCCESS + pd.getWins());
        player.sendMessage(INFO + "  ❌ Losses: " + DANGER + pd.getLosses());
        player.sendMessage(INFO + "  ➖ Draws:  " + MUTED + pd.getDraws());
        player.sendMessage("");
        player.sendMessage(INFO + "  📊 Win Rate: " + getWinRateColor(pd.getWinRate()) + String.format("%.1f%%", pd.getWinRate()));
        player.sendMessage("");
    }

    private void showTopPlayers(Player player, EloManager eloManager, String[] args) {
        int count = 10;
        if (args.length > 1) {
            try {
                count = Math.min(Integer.parseInt(args[1]), 50);
            } catch (NumberFormatException e) {
                player.sendMessage(DANGER + "✖ " + MUTED + "Invalid number!");
                return;
            }
        }

        List<PlayerData> top = eloManager.getTopPlayers(count);
        
        player.sendMessage("");
        player.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        player.sendMessage(ACCENT + "║" + PRIMARY + "     Top " + count + " Players    " + ACCENT + "║");
        player.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        player.sendMessage("");
        
        for (int i = 0; i < top.size(); i++) {
            PlayerData pd = top.get(i);
            String rankStr = getRankEmoji(i + 1) + " #" + (i + 1);
            ChatColor rankCol = getRankColor(i + 1);
            player.sendMessage(rankCol + rankStr + " " + pd.getPlayerName() + 
                INFO + " → " + ACCENT + pd.getElo() + " " + MUTED + "Elo");
        }
        
        player.sendMessage("");
    }

    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        player.sendMessage(ACCENT + "║" + PRIMARY + "      EloRanks Help     " + ACCENT + "║");
        player.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(INFO + "  /er " + MUTED + "- View your stats");
        player.sendMessage(INFO + "  /er stats " + MUTED + "- View your stats");
        player.sendMessage(INFO + "  /er <name> " + MUTED + "- View player stats");
        player.sendMessage(INFO + "  /er top " + MUTED + "- View top players");
        player.sendMessage(INFO + "  /duel <player> " + MUTED + "- Challenge to duel");
        player.sendMessage(INFO + "  /leaderboard " + MUTED + "- Full leaderboard");
        player.sendMessage("");
    }

    private ChatColor getRankColor(int rank) {
        switch (rank) {
            case 1: return ChatColor.GOLD;
            case 2: return ChatColor.GRAY;
            case 3: return ChatColor.YELLOW;
            default: return ChatColor.WHITE;
        }
    }
    
    private String getRankEmoji(int rank) {
        switch (rank) {
            case 1: return "👑";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return "  ";
        }
    }
    
    private ChatColor getWinRateColor(double winRate) {
        if (winRate >= 70) return SUCCESS;
        if (winRate >= 50) return INFO;
        return DANGER;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 0) {
            // Full subcommand list + player names
            List<String> options = new ArrayList<>();
            options.add("stats");
            options.add("me");
            options.add("top");
            options.add("help");
            // Add online player names
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
            return options;
        }

        String current = args[args.length - 1].toLowerCase();
        
        if (args.length == 1) {
            // Match subcommands and player names
            List<String> subcommands = List.of("stats", "me", "top", "help");
            List<String> matches = subcommands.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
            
            // Add online player names
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (current.isEmpty() || p.getName().toLowerCase().startsWith(current)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }

        // Second argument - number for top command
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            List<String> numbers = List.of("5", "10", "15", "25", "50");
            return numbers.stream()
                .filter(s -> current.isEmpty() || s.startsWith(current))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}