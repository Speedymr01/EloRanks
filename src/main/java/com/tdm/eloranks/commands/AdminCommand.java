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
            case "createduel" -> handleMakeDuel(sender, args);
            case "addarena" -> handleAddArena(sender);
            case "reload" -> handleReload(sender);
            case "resetall" -> handleResetAll(sender);
            case "resetplayer" -> handleResetPlayer(sender, args);
            case "setelo" -> handleSetElo(sender, args);
            case "addelo" -> handleAddElo(sender, args);
            case "arenainfo" -> handleArenaInfo(sender);
            case "resetarena" -> handleResetArena(sender, args);
            case "stats" -> handleStats(sender);
            case "debug" -> handleDebug(sender);
            case "endduel" -> handleEndDuel(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "getpos" -> handleGetPosition(sender, args);
            case "heal" -> handleHeal(sender, args);
            case "feed" -> handleFeed(sender, args);
            case "help" -> showHelp(sender);
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

        sender.sendMessage("----------------");
        sender.sendMessage("  Arena Information  ");
        sender.sendMessage("----------------");
        sender.sendMessage("");
        sender.sendMessage(INFO + "  Total:    " + ACCENT + total);
        sender.sendMessage(INFO + "  Available: " + SUCCESS + available);
        sender.sendMessage(INFO + "  In Use:   " + DANGER + inUse);
        sender.sendMessage(INFO + "  World:    " + ACCENT + plugin.getArenaManager().getWorldName());
        sender.sendMessage("");

        // Show individual arenas
        sender.sendMessage(INFO + "  Arena Details:");
        for (ArenaManager.Arena arena : arenas) {
            String status = arena.isInUse() ? DANGER + "In Use" : SUCCESS + "Available";
            sender.sendMessage(MUTED + "    [" + ACCENT + "Arena " + arena.getId() + MUTED + "] " + status);
        }
        sender.sendMessage("");
    }

    private void handleStats(CommandSender sender) {
        int totalPlayers = plugin.getEloManager().getTotalPlayers();
        int totalArenas = plugin.getArenaManager().getArenas().size();
        int activeDuels = plugin.getDuelManager().getActiveDuelCount();

        sender.sendMessage("----------------");
        sender.sendMessage("  Plugin Stats  ");
        sender.sendMessage("----------------");
        sender.sendMessage("");
        sender.sendMessage(INFO + "  Total Players: " + ACCENT + totalPlayers);
        sender.sendMessage(INFO + "  Total Arenas:  " + ACCENT + totalArenas);
        sender.sendMessage(INFO + "  Active Duels: " + ACCENT + activeDuels);
        sender.sendMessage(INFO + "  Version:      " + ACCENT + plugin.getDescription().getVersion());
        sender.sendMessage("");
    }

    private void handleResetArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin resetarena <id>");
            return;
        }

        int arenaId;
        try {
            arenaId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid arena ID!");
            return;
        }

        plugin.getArenaManager().resetArena(arenaId);
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Reset arena " + ACCENT + arenaId);
    }

    private void handleDebug(CommandSender sender) {
        sender.sendMessage("=== EloRanks Debug Info ===");
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
        plugin.getDuelManager().cancelDuel(target.getUniqueId());

        sender.sendMessage(SUCCESS + "✓ " + INFO + "Ended duel for " + ACCENT + target.getName());
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(DANGER + "✖ " + MUTED + "Usage: /eradmin tp <place> [player]");
            sender.sendMessage(MUTED + "  Places: <arenaId>, overworld, nether, end");
            return;
        }

        String place = args[1].toLowerCase();
        Player target;
        
        // Determine target player
        if (args.length >= 3) {
            // Player specified
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Player not found!");
                return;
            }
        } else {
            // Use executor
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Specify a player!");
                return;
            }
        }

        // Check if it's an arena or world
        try {
            int arenaId = Integer.parseInt(place);
            // It's an arena
            Collection<ArenaManager.Arena> arenas = plugin.getArenaManager().getArenas();
            ArenaManager.Arena arena = null;

            for (var a : arenas) {
                if (a.getId() == arenaId) {
                    arena = a;
                    break;
                }
            }

            if (arena == null) {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Arena not found!");
                return;
            }

            Location spawn = arena.getSpawn1() != null ? arena.getSpawn1() : arena.getSpawn2();
            if (spawn == null) {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Arena has no spawn points!");
                return;
            }

            target.teleport(spawn);
            sender.sendMessage(SUCCESS + "✓ " + INFO + "Teleported " + ACCENT + target.getName() + INFO + " to arena " + ACCENT + arenaId);
            return;
        } catch (NumberFormatException e) {
            // It's not an arena ID, check for worlds
        }

        // Check for world names
        World targetWorld;
        switch (place) {
            case "overworld", "world" -> {
                targetWorld = Bukkit.getWorld("world");
                if (targetWorld == null) {
                    sender.sendMessage(DANGER + "✖ " + MUTED + "Overworld not found!");
                    return;
                }
            }
            case "nether" -> {
                targetWorld = Bukkit.getWorld("world_nether");
                if (targetWorld == null) {
                    sender.sendMessage(DANGER + "✖ " + MUTED + "Nether not found!");
                    return;
                }
            }
            case "end", "the_end" -> {
                targetWorld = Bukkit.getWorld("world_the_end");
                if (targetWorld == null) {
                    sender.sendMessage(DANGER + "✖ " + MUTED + "End not found!");
                    return;
                }
            }
            default -> {
                sender.sendMessage(DANGER + "✖ " + MUTED + "Invalid place! Use: arena ID, overworld, nether, or end");
                return;
            }
        }

        // Teleport to world spawn
        target.teleport(targetWorld.getSpawnLocation());
        sender.sendMessage(SUCCESS + "✓ " + INFO + "Teleported " + ACCENT + target.getName() + INFO + " to " + place);
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
        sender.sendMessage("----------------");
        sender.sendMessage("  Admin Help  ");
        sender.sendMessage("----------------");
        sender.sendMessage("");
        sender.sendMessage(INFO + "  ⚔️  createduel <p1> <p2> [f]" + MUTED + "  - Create duel (f=true instant)");
        sender.sendMessage(INFO + "  🏟️  addarena" + MUTED + "                  - Add new arena");
        sender.sendMessage(INFO + "  🔄 reload" + MUTED + "                 - Reload config");
        sender.sendMessage(INFO + "  🗑️  resetall" + MUTED + "                - Reset all player data");
        sender.sendMessage(INFO + "  👤 resetplayer <p>" + MUTED + "         - Reset player stats");
        sender.sendMessage(INFO + "  ⚡ setelo <p> <elo>" + MUTED + "          - Set player Elo");
        sender.sendMessage(INFO + "  ➕ addelo <p> <amt>" + MUTED + "           - Add Elo to player");
        sender.sendMessage(INFO + "  ℹ️  arenainfo" + MUTED + "                - Show arena info");
        sender.sendMessage(INFO + "  🔧 resetarena <id>" + MUTED + "          - Reset arena");
        sender.sendMessage(INFO + "  ⏹️  endduel <p>" + MUTED + "              - End player's duel");
        sender.sendMessage(INFO + "  📍 tp <place> [p]" + MUTED + "            - Teleport (arena/overworld/nether/end)");
        sender.sendMessage(INFO + "  📊 stats" + MUTED + "                  - Plugin statistics");
        sender.sendMessage(INFO + "  🐛 debug" + MUTED + "                  - Debug info");
        sender.sendMessage(INFO + "  💚 heal <p>" + MUTED + "                 - Heal player");
        sender.sendMessage(INFO + "  🍖 feed <p>" + MUTED + "                - Feed player");
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("er.admin")) {
            return List.of();
        }

        // Return full subcommand list for first argument
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> subcommands = List.of(
                "createduel",
                "addarena",
                "reload",
                "resetall",
                "resetplayer",
                "setelo",
                "addelo",
                "arenainfo",
                "resetarena",
                "stats",
                "debug",
                "endduel",
                "tp",
                "getpos",
                "heal",
                "feed",
                "help"
            );
            if (current.isEmpty()) {
                return subcommands;
            }
            return subcommands.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
        }

        // Second argument - different based on subcommand
        String sub = args[0].toLowerCase();
        String current = args[args.length - 1].toLowerCase();
        List<String> matches = new ArrayList<>();

        // Commands that need player argument
        if (sub.equals("createduel") ||
            sub.equals("resetplayer") ||
            sub.equals("setelo") ||
            sub.equals("addelo") ||
            sub.equals("endduel") ||
            sub.equals("heal") ||
            sub.equals("feed")) {
            if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (current.isEmpty() || p.getName().toLowerCase().startsWith(current)) {
                        matches.add(p.getName());
                    }
                }
            }
            // Add "true" for createduel third arg
            if (sub.equals("createduel") && args.length == 3) {
                if (current.isEmpty() || "true".startsWith(current)) {
                    matches.add("true");
                }
            }
        }

        // Commands that need arena ID or world
        if (sub.equals("resetarena") ||
            sub.equals("getpos")) {
            if (args.length == 2) {
                for (var arena : plugin.getArenaManager().getArenas()) {
                    String id = String.valueOf(arena.getId());
                    if (current.isEmpty() || id.startsWith(current)) {
                        matches.add(id);
                    }
                }
            }
        }

        // TP command - args[1] = place (arena ID or world), args[2] = optional player
        if (sub.equals("tp") && args.length == 2) {
            // Add arena IDs
            for (var arena : plugin.getArenaManager().getArenas()) {
                String id = String.valueOf(arena.getId());
                if (current.isEmpty() || id.startsWith(current)) {
                    matches.add(id);
                }
            }
            // Add world names
            List.of("overworld", "nether", "end").forEach(world -> {
                if (current.isEmpty() || world.startsWith(current)) {
                    matches.add(world);
                }
            });
        }

        // For tp with 2 args where first arg is not a number - add player names
        if (sub.equals("tp") && args.length == 3) {
            // args[2] should be player name
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (current.isEmpty() || p.getName().toLowerCase().startsWith(current)) {
                    matches.add(p.getName());
                }
            }
        }

        // Also for tparena backwards compatibility
        if (sub.equals("tparena")) {
            if (args.length == 2) {
                for (var arena : plugin.getArenaManager().getArenas()) {
                    String id = String.valueOf(arena.getId());
                    if (current.isEmpty() || id.startsWith(current)) {
                        matches.add(id);
                    }
                }
            } else if (args.length == 3) {
                for (var arena : plugin.getArenaManager().getArenas()) {
                    String id = String.valueOf(arena.getId());
                    if (current.isEmpty() || id.startsWith(current)) {
                        matches.add(id);
                    }
                }
            }
            if (args.length == 2) {
                try {
                    Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (current.isEmpty() || p.getName().toLowerCase().startsWith(current)) {
                            matches.add(p.getName());
                        }
                    }
                }
            }
        }

        return matches;
    }
}
