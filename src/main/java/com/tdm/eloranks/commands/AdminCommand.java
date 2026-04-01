package com.tdm.eloranks.commands;

import com.tdm.eloranks.EloRanks;
import com.tdm.eloranks.manager.ArenaManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin commands for managing EloRanks plugin.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final EloRanks plugin;
    
    // Color scheme
    private final ChatColor PRIMARY = ChatColor.AQUA;
    private final ChatColor ACCENT = ChatColor.GOLD;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor DANGER = ChatColor.RED;
    private final ChatColor INFO = ChatColor.YELLOW;
    private final ChatColor MUTED = ChatColor.GRAY;
    private final ChatColor DEBUG = ChatColor.LIGHT_PURPLE;

    public AdminCommand(EloRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("er.admin")) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "makeduel", "forceduel", "startduel" -> handleMakeDuel(sender, args);
            case "addarena", "newarena", "createarena" -> handleAddArena(sender);
            case "reload", "rl", "reloadconfig" -> handleReload(sender);
            case "resetall", "resettop", "cleardata", "wipedata" -> handleResetAll(sender);
            case "resetplayer", "resetstats", "resetp" -> handleResetPlayer(sender, args);
            case "setelo", "setrating" -> handleSetElo(sender, args);
            case "adde", "addelo", "addrating" -> handleAddElo(sender, args);
            case "arenainfo", "arenas", "arenastatus" -> handleArenaInfo(sender);
            case "forcereset", "resetarena", "cleararena" -> handleForceResetArena(sender, args);
            case "stats", "statistics", "plstats" -> handleStats(sender);
            case "debug", "dbg", "diag" -> handleDebug(sender);
            case "endduel", "stopduel", "cancelduel" -> handleEndDuel(sender, args);
            case "tparena", "gotoarena", "arenatp" -> handleTeleport(sender, args);
            case "getpos", "position", "arenapos" -> handleGetPosition(sender, args);
            case "heal", "healplayer" -> handleHeal(sender, args);
            case "feed", "feedplayer" -> handleFeed(sender, args);
            case "help", "?", "commands" -> showHelp(sender);
            default -> {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Unknown subcommand. Use /eradmin help");
                return true;
            }
        }

        return true;
    }

    private void handleMakeDuel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin makeduel <player1> <player2> [force]");
            return;
        }

        Player player1 = Bukkit.getPlayer(args[1]);
        Player player2 = Bukkit.getPlayer(args[2]);

        if (player1 == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player '" + args[1] + "' not found!");
            return;
        }

        if (player2 == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player '" + args[2] + "' not found!");
            return;
        }

        boolean force = args.length > 3 && args[3].equalsIgnoreCase("true");

        if (force) {
            // Force start duel immediately
            plugin.getDuelManager().startDuel(player1, player2);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Forced duel started between " + ACCENT + player1.getName() + INFO + " and " + ACCENT + player2.getName());
        } else {
            // Send duel request
            plugin.getDuelManager().sendDuelRequest(player1, player2);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Duel request sent between " + ACCENT + player1.getName() + INFO + " and " + ACCENT + player2.getName());
        }
    }

    private void handleAddArena(CommandSender sender) {
        // Generate new arena
        plugin.getArenaManager().generateArenas(1);
        
        int arenaCount = plugin.getArenaManager().getArenas().size();
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Added new arena! Total arenas: " + ACCENT + arenaCount);
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfig();
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Configuration reloaded!");
    }

    private void handleResetAll(CommandSender sender) {
        plugin.getEloManager().resetAllStats();
        sender.sendMessage(SUCCESS + "✓ " + INFO + "All player statistics have been reset!");
    }

    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin resetplayer <player>");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        
        if (target != null) {
            plugin.getEloManager().resetPlayerStats(target.getUniqueId());
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Reset stats for " + ACCENT + target.getName());
        } else {
            // Try to find by name in data
            plugin.getEloManager().resetPlayerStatsByName(playerName);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Reset stats for " + ACCENT + playerName);
        }
    }

    private void handleSetElo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin setelo <player> <elo>");
            return;
        }

        int elo;
        try {
            elo = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid Elo value!");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target != null) {
            plugin.getEloManager().setPlayerElo(target.getUniqueId(), elo);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Set " + ACCENT + target.getName() + "'s" + INFO + " Elo to " + ACCENT + elo);
        } else {
            plugin.getEloManager().setPlayerEloByName(args[1], elo);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Set " + ACCENT + args[1] + "'s" + INFO + " Elo to " + ACCENT + elo);
        }
    }

    private void handleAddElo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin adde <player> <amount>");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid amount!");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target != null) {
            var pd = plugin.getEloManager().getOrCreatePlayerData(target.getUniqueId(), target.getName());
            int newElo = pd.getElo() + amount;
            plugin.getEloManager().setPlayerElo(target.getUniqueId(), newElo);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Added " + ACCENT + amount + INFO + " Elo to " + ACCENT + target.getName() + INFO + " (New: " + ACCENT + newElo + ")");
        } else {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
        }
    }

    private void handleArenaInfo(CommandSender sender) {
        Collection<ArenaManager.Arena> arenas = plugin.getArenaManager().getArenas();
        int total = arenas.size();
        int inUse = (int) arenas.stream().filter(ArenaManager.Arena::isInUse).count();
        int available = total - inUse;

        sender.sendMessage("");
        sender.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        sender.sendMessage(ACCENT + "║" + PRIMARY + "       Arena Information      " + ACCENT + "║");
        sender.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        sender.sendMessage("");
        sender.sendMessage(INFO + "  📊 Total:    " + ACCENT + total);
        sender.sendMessage(INFO + "  ✅ Available: " + SUCCESS + available);
        sender.sendMessage(INFO + "  ❌ In Use:   " + DANGER + inUse);
        sender.sendMessage(INFO + "  🌍 World:    " + ACCENT + plugin.getArenaManager().getWorldName());
        sender.sendMessage("");

        // Show individual arenas
        sender.sendMessage(INFO + "  Arena Details:");
        for (ArenaManager.Arena arena : arenas) {
            String status = arena.isInUse() ? DANGER + "In Use" : SUCCESS + "Available";
            sender.sendMessage(MUTED + "    [" + ACCENT + "Arena " + arena.getId() + MUTED + "] " + status);
        }
        sender.sendMessage("");
    }

    private void handleForceResetArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin forcereset <arenaId>");
            return;
        }

        int arenaId;
        try {
            arenaId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid arena ID!");
            return;
        }

        plugin.getArenaManager().freeArena(arenaId);
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Force reset arena " + ACCENT + arenaId);
    }

    private void handleStats(CommandSender sender) {
        int totalPlayers = plugin.getEloManager().getTotalPlayers();
        var arenas = plugin.getArenaManager().getArenas();
        int totalArenas = arenas.size();
        long activeDuels = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getDuelManager().hasActiveDuel(p.getUniqueId()))
                .count() / 2; // Each duel has 2 players

        sender.sendMessage("");
        sender.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        sender.sendMessage(ACCENT + "║" + PRIMARY + "        Plugin Stats         " + ACCENT + "║");
        sender.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        sender.sendMessage("");
        sender.sendMessage(INFO + "  👥 Total Players: " + ACCENT + totalPlayers);
        sender.sendMessage(INFO + "  🏟️  Total Arenas:  " + ACCENT + totalArenas);
        sender.sendMessage(INFO + "  ⚔️  Active Duels: " + ACCENT + activeDuels);
        sender.sendMessage(INFO + "  🌍 Version:      " + ACCENT + plugin.getDescription().getVersion());
        sender.sendMessage("");
    }

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(DEBUG + "═══ EloRanks Debug Info ═══");
        sender.sendMessage(INFO + "Plugin: " + plugin.getDescription().getName());
        sender.sendMessage(INFO + "Version: " + plugin.getDescription().getVersion());
        sender.sendMessage(INFO + "Data Folder: " + plugin.getDataFolder().getAbsolutePath());
        sender.sendMessage(INFO + "Arena World: " + plugin.getArenaManager().getWorldName());
        
        World arenaWorld = plugin.getArenaManager().getWorld();
        if (arenaWorld != null) {
            sender.sendMessage(INFO + "Arena World Loaded: true");
        } else {
            sender.sendMessage(INFO + "Arena World Loaded: false");
        }
        
        sender.sendMessage(INFO + "Config: " + plugin.getConfigManager().getConfig().getKeys(false).size() + " keys");
    }

    private void handleEndDuel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin endduel <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
            return;
        }

        if (!plugin.getDuelManager().hasActiveDuel(target.getUniqueId())) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player is not in a duel!");
            return;
        }

        UUID opponentUuid = plugin.getDuelManager().getDuelOpponent(target.getUniqueId());
        Player opponent = Bukkit.getPlayer(opponentUuid);

        // End duel without winner (cancel)
        if (opponent != null) {
            plugin.getDuelManager().restorePlayerInventory(target);
            plugin.getDuelManager().restorePlayerInventory(opponent);
            target.clearActivePotionEffects();
            opponent.clearActivePotionEffects();
            plugin.getArenaManager().removePlayer(target);
            plugin.getArenaManager().removePlayer(opponent);
            
            // Remove from active duels
            plugin.getDuelManager().cancelDuel(target.getUniqueId());
            plugin.getDuelManager().cancelDuel(opponent.getUniqueId());
        }

        sender.sendMessage(SUCCESS + "✓ " + INFO + "Ended duel for " + ACCENT + target.getName());
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin tp [player] [arenaId]");
            return;
        }

        Player target;
        int arenaId = -1;

        if (args.length >= 3) {
            try {
                arenaId = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid arena ID!");
                return;
            }
            target = Bukkit.getPlayer(args[1]);
        } else {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Specify a player!");
                return;
            }
        }

        if (target == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
            return;
        }

        Collection<ArenaManager.Arena> arenas = plugin.getArenaManager().getArenas();
        ArenaManager.Arena arena = null;

        if (arenaId >= 0) {
            for (var a : arenas) {
                if (a.getId() == arenaId) {
                    arena = a;
                    break;
                }
            }
        } else {
            // Get random available arena
            for (var a : arenas) {
                if (!a.isInUse()) {
                    arena = a;
                    break;
                }
            }
        }

        if (arena == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "No available arenas!");
            return;
        }

        Location spawn = arena.getSpawn1() != null ? arena.getSpawn1() : arena.getSpawn2();
        if (spawn == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Arena has no spawn points!");
            return;
        }

        target.teleport(spawn);
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Teleported " + ACCENT + target.getName() + INFO + " to arena " + ACCENT + arena.getId());
    }

    private void handleGetPosition(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin getpos <arenaId>");
            return;
        }

        int arenaId;
        try {
            arenaId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid arena ID!");
            return;
        }

        ArenaManager.Arena arena = null;
        for (var a : plugin.getArenaManager().getArenas()) {
            if (a.getId() == arenaId) {
                arena = a;
                break;
            }
        }

        if (arena == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Arena not found!");
            return;
        }

        sender.sendMessage(INFO + "Arena " + arena.getId() + " Position:");
        sender.sendMessage(INFO + "  Offset X: " + ACCENT + arena.getOffsetX());
        if (arena.getSpawn1() != null) {
            sender.sendMessage(INFO + "  Spawn 1: " + ACCENT + 
                (int)arena.getSpawn1().getX() + ", " + 
                (int)arena.getSpawn1().getY() + ", " + 
                (int)arena.getSpawn1().getZ());
        }
        if (arena.getSpawn2() != null) {
            sender.sendMessage(INFO + "  Spawn 2: " + ACCENT + 
                (int)arena.getSpawn2().getX() + ", " + 
                (int)arena.getSpawn2().getY() + ", " + 
                (int)arena.getSpawn2().getZ());
        }
    }

    private void handleHeal(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin heal <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
            return;
        }

        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Healed " + ACCENT + target.getName());
    }

    private void handleFeed(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin feed <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
            return;
        }

        target.setFoodLevel(20);
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Fed " + ACCENT + target.getName());
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ACCENT + "╔═════════════════════════════════╗");
        sender.sendMessage(ACCENT + "║" + PRIMARY + "     EloRanks Admin Help    " + ACCENT + "║");
        sender.sendMessage(ACCENT + "╚═════════════════════════════════╝");
        sender.sendMessage("");
        sender.sendMessage(INFO + "  ⚔️  makeduel <p1> <p2> [f]" + MUTED + "    - Create duel (f=true instant)");
        sender.sendMessage(INFO + "  🏟️  addarena" + MUTED + "                    - Add new arena");
        sender.sendMessage(INFO + "  🔄 reload" + MUTED + "                   - Reload config");
        sender.sendMessage(INFO + "  🗑️  resetall" + MUTED + "                  - Reset all player data");
        sender.sendMessage(INFO + "  👤 resetplayer <p>" + MUTED + "           - Reset player stats");
        sender.sendMessage(INFO + "  ⚡ setelo <p> <elo>" + MUTED + "            - Set player Elo");
        sender.sendMessage(INFO + "  ➕ adde <p> <amt>" + MUTED + "              - Add Elo to player");
        sender.sendMessage(INFO + "  ℹ️  arenainfo" + MUTED + "                 - Show arena info");
        sender.sendMessage(INFO + "  🔧 forcereset <id>" + MUTED + "           - Force reset arena");
        sender.sendMessage(INFO + "  ⏹️  endduel <p>" + MUTED + "                - End player's duel");
        sender.sendMessage(INFO + "  📍 tp <p>" + MUTED + "                    - Teleport to arena");
        sender.sendMessage(INFO + "  📊 stats" + MUTED + "                    - Plugin statistics");
        sender.sendMessage(INFO + "  🐛 debug" + MUTED + "                    - Debug info");
        sender.sendMessage(INFO + "  💚 heal <p>" + MUTED + "                   - Heal player");
        sender.sendMessage(INFO + "  🍖 feed <p>" + MUTED + "                  - Feed player");
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("er.admin")) {
            return List.of();
        }

        List<String> subcommands = List.of(
            "makeduel", "forceduel", "startduel",
            "addarena", "newarena", "createarena",
            "reload", "rl", "reloadconfig",
            "resetall", "resettop", "cleardata", "wipedata",
            "resetplayer", "resetstats", "resetp",
            "setelo", "setrating",
            "adde", "addelo", "addrating",
            "arenainfo", "arenas", "arenastatus",
            "forcereset", "resetarena", "cleararena",
            "stats", "statistics", "plstats",
            "debug", "dbg", "diag",
            "endduel", "stopduel", "cancelduel",
            "tparena", "gotoarena", "arenatp",
            "getpos", "position", "arenapos",
            "heal", "healplayer",
            "feed", "feedplayer",
            "help", "?", "commands"
        );

        if (args.length == 0) {
            return subcommands;
        }

        String current = args[args.length - 1].toLowerCase();
        
        // Match subcommands
        List<String> matches = subcommands.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .collect(Collectors.toList());

        // Add player names for certain subcommands
        if (args.length >= 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("makeduel") || sub.equals("forceduel") || sub.equals("startduel") ||
                sub.equals("resetplayer") || sub.equals("resetstats") || sub.equals("resetp") ||
                sub.equals("setelo") || sub.equals("setrating") ||
                sub.equals("adde") || sub.equals("addelo") || sub.equals("addrating") ||
                sub.equals("endduel") || sub.equals("stopduel") || sub.equals("cancelduel") ||
                sub.equals("tparena") || sub.equals("gotoarena") || sub.equals("arenatp") ||
                sub.equals("heal") || sub.equals("healplayer") ||
                sub.equals("feed") || sub.equals("feedplayer")) {
                
                // Add online player names
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(current)) {
                        matches.add(p.getName());
                    }
                }
            }
            
            // Add arena IDs for forcereset
            if (sub.equals("forcereset") || sub.equals("resetarena") || sub.equals("cleararena") ||
                sub.equals("getpos") || sub.equals("position") || sub.equals("arenapos")) {
                for (var arena : plugin.getArenaManager().getArenas()) {
                    String id = String.valueOf(arena.getId());
                    if (id.startsWith(current)) {
                        matches.add(id);
                    }
                }
            }
            
            // Add "true" for makeduel force argument
            if ((sub.equals("makeduel") || sub.equals("forceduel") || sub.equals("startduel")) && args.length == 3) {
                if ("true".startsWith(current)) {
                    matches.add("true");
                }
            }
        }

        return matches;
    }
}
