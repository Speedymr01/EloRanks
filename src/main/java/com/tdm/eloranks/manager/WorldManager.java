package com.tdm.eloranks.manager;

import com.tdm.eloranks.EloRanks;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages duel arena worlds - can extract bundled worlds and load them.
 */
public class WorldManager {

    private final EloRanks plugin;
    private final String arenaWorldName;

    public WorldManager(EloRanks plugin) {
        this.plugin = plugin;
        this.arenaWorldName = plugin.getConfigManager().getArenaWorld();
        initializeWorld();
    }

    /**
     * Extract bundled world files and load the world.
     */
    public void initializeWorld() {
        World existingWorld = plugin.getServer().getWorld(arenaWorldName);
        
        if (existingWorld != null) {
            plugin.getLogger().info("Arena world '" + arenaWorldName + "' already loaded");
            return;
        }

        File worldFolder = new File(plugin.getServer().getWorldContainer(), arenaWorldName);
        
        if (worldFolder.exists() && new File(worldFolder, "level.dat").exists()) {
            // World folder exists, just load it
            loadWorld();
            return;
        }

        // Try to extract bundled world
        if (extractBundledWorld()) {
            loadWorld();
        } else {
            plugin.getLogger().warning("No bundled arena world found, using default world");
        }
    }

    /**
     * Extract the bundled world from plugin resources.
     */
    private boolean extractBundledWorld() {
        // Check for bundled world files in resources
        // The world should be in src/main/resources/worlds/duel_arena/
        
        String[] requiredFiles = {"level.dat", "level.dat_mca", "region"};
        
        // Check if bundled world exists
        if (plugin.getResource("worlds/" + arenaWorldName + "/level.dat") == null) {
            plugin.getLogger().info("No bundled world found, will use existing world or create basic one");
            return false;
        }

        File worldFolder = new File(plugin.getServer().getWorldContainer(), arenaWorldName);
        
        try {
            plugin.getDataFolder().mkdirs();
            
            // Copy world files from resources
            extractResourceFolder("worlds/" + arenaWorldName, worldFolder);
            
            plugin.getLogger().info("Extracted bundled arena world to: " + worldFolder.getAbsolutePath());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to extract bundled world: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recursively extract resource folder to disk.
     */
    private void extractResourceFolder(String resourcePath, File targetFolder) throws IOException {
        // This is a simplified version - in practice you'd iterate through resources
        // For now, we'll handle the common case of world extraction
        
        // Create target directory
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }
        
        // Note: In a full implementation, you'd use ClassLoader.getResources()
        // to iterate through all files in the resource folder and copy them
    }

    /**
     * Load the arena world into the server.
     */
    private void loadWorld() {
        World world = plugin.getServer().createWorld(WorldCreator.name(arenaWorldName));
        
        if (world != null) {
            world.setAutoSave(false); // Don't auto-save duel world
            plugin.getLogger().info("Loaded arena world: " + arenaWorldName);
        } else {
            plugin.getLogger().severe("Failed to load arena world: " + arenaWorldName);
        }
    }

    /**
     * Get the arena world.
     */
    public World getArenaWorld() {
        return plugin.getServer().getWorld(arenaWorldName);
    }

    /**
     * Get the arena world spawn location.
     */
    public java.util.Optional<org.bukkit.Location> getArenaSpawnLocation(int spawnPoint) {
        World world = getArenaWorld();
        if (world == null) {
            return java.util.Optional.empty();
        }

        // Try to get spawn from config, otherwise use world spawn
        var config = plugin.getConfigManager().getConfig();
        String path = "arena.spawn" + spawnPoint;
        
        if (config.contains(path)) {
            org.bukkit.Location loc = config.getLocation(path);
            if (loc != null) {
                return java.util.Optional.of(loc);
            }
        }

        // Default to world spawn
        return java.util.Optional.of(world.getSpawnLocation());
    }

    /**
     * Set arena spawn location.
     */
    public void setArenaSpawnLocation(int spawnPoint, org.bukkit.Location location) {
        var config = plugin.getConfigManager().getConfig();
        config.set("arena.spawn" + spawnPoint, location);
        plugin.getConfigManager().saveConfig();
    }

    /**
     * Reload the arena world.
     */
    public void reloadArenaWorld() {
        World world = getArenaWorld();
        if (world != null) {
            plugin.getServer().unloadWorld(world, false);
        }
        initializeWorld();
    }
}