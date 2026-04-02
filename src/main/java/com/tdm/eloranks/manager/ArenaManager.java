package com.tdm.eloranks.manager;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extension.platform.PlatformManager;
import com.sk89q.worldedit.extension.platform.Platform;
import com.tdm.eloranks.EloRanks;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages arena instances using FAWE for schematic pasting.
 * Handles arena creation, spawn point detection, and arena resetting.
 */
public class ArenaManager {

    private final EloRanks plugin;
    private final String worldName;
    private final String schematicName;
    private final int initialArenas;
    private final int arenaSpacing;
    
    // Active arena instances
    private final Map<Integer, Arena> arenas = new ConcurrentHashMap<>();
    private AtomicInteger nextArenaId = new AtomicInteger(0);
    
    // Track which arena a player is currently in
    private final Map<UUID, Integer> playerArenaMap = new ConcurrentHashMap<>();
    
    // Loaded schematic clipboard
    private Clipboard schematic;
    
    // Retry counter for FAWE initialization
    private final int maxRetries = 10;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    
    // Track if arena system failed to initialize
    private boolean arenaSystemDisabled = false;
    private boolean arenasInitialized = false;
    
    public ArenaManager(EloRanks plugin, String schematicName, int initialArenas, int arenaSpacing) {
        this.plugin = plugin;
        this.worldName = plugin.getConfigManager().getArenaWorld();
        this.schematicName = schematicName;
        this.initialArenas = initialArenas;
        this.arenaSpacing = arenaSpacing;
        
        plugin.getLogger().info("=== ArenaManager Starting ===");
        plugin.getLogger().info("Schema: " + schematicName + " | Arenas: " + initialArenas + " | Spacing: " + arenaSpacing);
        
        // Create the void world
        createVoidWorld();
        
        // Schedule schematic loading with retry logic
        scheduleArenaInitialization();
    }
    
    /**
     * Check if arena system is available.
     */
    public boolean isArenaSystemAvailable() {
        return arenasInitialized && !arenaSystemDisabled;
    }
    
    /**
     * Schedule arena initialization with retry logic for FAWE.
     */
    private void scheduleArenaInitialization() {
        plugin.getLogger().info("Scheduling arena initialization, waiting for FAWE...");
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Checking FAWE status...");
            
            if (isFAWELoaded()) {
                plugin.getLogger().info("FAWE detected! Initializing arenas...");
                try {
                    initializeArenas();
                    plugin.getLogger().info("=== Arenas initialized successfully! ===");
                } catch (Exception e) {
                    plugin.getLogger().severe("CRITICAL: Failed to initialize arenas: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // FAWE not ready yet, retry
                plugin.getLogger().warning("FAWE not ready, will retry...");
                
                // Log what's available
                var fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
                var we = Bukkit.getPluginManager().getPlugin("WorldEdit");
                plugin.getLogger().info("  FAWE Plugin: " + (fawe != null ? "LOADED (" + fawe.getDescription().getVersion() + ")" : "NOT FOUND"));
                plugin.getLogger().info("  WorldEdit: " + (we != null ? "LOADED (" + we.getDescription().getVersion() + ")" : "NOT FOUND"));
                
                if (retryCount.get() < maxRetries) {
                    retryCount.incrementAndGet();
                    plugin.getLogger().warning("Retry " + retryCount.get() + "/" + maxRetries + " in 1 second...");
                    scheduleArenaInitialization();
                } else {
                    plugin.getLogger().severe("=== FAILED: Max retries reached! FAWE may not be installed ===");
                    plugin.getLogger().severe("EloRanks will run in LIMTED MODE without arena system!");
                    plugin.getLogger().severe("Install FastAsyncWorldEdit and restart to enable full functionality.");
                    arenaSystemDisabled = true;
                }
            }
        }, 20L); // Check every 1 second initially, then retry
    }
    
    /**
     * Check if FAWE is loaded and ready.
     * FAWE should be installed as a separate plugin on the server.
     */
    private boolean isFAWELoaded() {
        plugin.getLogger().info("Checking if FAWE is loaded...");
        
        // Check if FAWE plugin is loaded (external plugin)
        var fawePlugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (fawePlugin == null) {
            plugin.getLogger().info("  FastAsyncWorldEdit NOT FOUND");
            // Also check for regular WorldEdit as fallback
            var wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
            if (wePlugin == null) {
                plugin.getLogger().info("  WorldEdit NOT FOUND EITHER");
                return false;
            }
            plugin.getLogger().info("  WorldEdit found: " + wePlugin.getDescription().getVersion());
        } else {
            plugin.getLogger().info("  FastAsyncWorldEdit found: " + fawePlugin.getDescription().getVersion());
        }
        
        // Try to access WorldEdit API
        try {
            var we = com.sk89q.worldedit.WorldEdit.getInstance();
            if (we == null) {
                plugin.getLogger().info("  WorldEdit.getInstance() returned null");
                return false;
            }
            plugin.getLogger().info("  WorldEdit instance: " + we.getClass().getSimpleName());
            
            var pm = we.getPlatformManager();
            if (pm == null) {
                plugin.getLogger().info("  PlatformManager is null");
                return false;
            }
            
            var platforms = pm.getPlatforms();
            plugin.getLogger().info("  Registered platforms: " + (platforms != null ? platforms.size() : "null"));
            
            return platforms != null && !platforms.isEmpty();
        } catch (Exception e) {
            plugin.getLogger().warning("  Exception checking FAWE: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Initialize arenas - called after FAWE has loaded.
     */
    private void initializeArenas() {
        // Load schematic
        loadSchematic();
        
        // Generate initial arenas
        generateArenas(initialArenas);
        
        // Mark as initialized
        arenasInitialized = true;
        plugin.getLogger().info("Arena system ready!");
    }
    
    /**
     * Create a void world for arenas by generating flat chunks.
     */
    private void createVoidWorld() {
        World existingWorld = Bukkit.getWorld(worldName);
        
        if (existingWorld != null) {
            plugin.getLogger().info("Arena world '" + worldName + "' already exists");
            return;
        }
        
        // Create flat world with no blocks (void)
        WorldCreator creator = WorldCreator.name(worldName)
                .type(WorldType.FLAT)
                .generator("air");
        
        World world = creator.createWorld();
        
        if (world != null) {
            world.setAutoSave(false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_TILE_DROPS, false);
            world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.FALL_DAMAGE, false);
            world.setGameRule(GameRule.FIRE_DAMAGE, false);
            
            plugin.getLogger().info("Created void arena world: " + worldName);
        } else {
            plugin.getLogger().severe("Failed to create arena world: " + worldName);
        }
    }
    
    /**
     * Load the schematic file from resources.
     */
    private void loadSchematic() {
        File schematicFile = new File(plugin.getDataFolder(), "arenas/" + schematicName);
        
        // Check if file exists in data folder first
        if (!schematicFile.exists()) {
            // Try loading from plugins folder
            schematicFile = new File(new File(Bukkit.getPluginsFolder(), "EloRanks"), "arenas/" + schematicName);
        }
        
        // Try to copy from resources if not found
        if (!schematicFile.exists()) {
            var resourceStream = plugin.getResource("arenas/" + schematicName);
            if (resourceStream != null) {
                try {
                    plugin.getDataFolder().mkdirs();
                    File arenasDir = new File(plugin.getDataFolder(), "arenas");
                    arenasDir.mkdirs();
                    schematicFile = new File(arenasDir, schematicName);
                    java.nio.file.Files.copy(resourceStream, schematicFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Extracted schematic from resources");
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to extract schematic: " + e.getMessage());
                    return;
                }
            }
        }
        
        if (!schematicFile.exists() || !schematicFile.isFile()) {
            plugin.getLogger().severe("Schematic file not found: " + schematicName);
            return;
        }
        
        try (FileInputStream fis = new FileInputStream(schematicFile)) {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format != null) {
                schematic = format.load(fis);
                plugin.getLogger().info("Loaded schematic: " + schematicName + 
                        " (Size: " + schematic.getDimensions().x() + "x" + 
                        schematic.getDimensions().y() + "x" + schematic.getDimensions().z() + ")");
            } else {
                plugin.getLogger().severe("Unknown schematic format: " + schematicName);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load schematic: " + e.getMessage());
        }
    }
    
    /**
     * Generate arena instances by pasting the schematic.
     */
    public void generateArenas(int count) {
        if (schematic == null) {
            plugin.getLogger().severe("Cannot generate arenas - schematic not loaded");
            return;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Cannot generate arenas - world not loaded");
            return;
        }
        
        for (int i = 0; i < count; i++) {
            generateArena();
        }
        
        plugin.getLogger().info("Generated " + count + " arena instances");
    }
    
    /**
     * Generate a single arena instance.
     */
    private void generateArena() {
        int arenaId = nextArenaId.getAndIncrement();
        
        // Calculate offset position
        int offsetX = arenaId * arenaSpacing;
        
        // Paste schematic at offset
        try {
            var editSession = com.sk89q.worldedit.WorldEdit.getInstance()
                    .getEditSessionFactory().getEditSession(
                            new BukkitWorld(Bukkit.getWorld(worldName)), -1);
            
            // Create clipboard holder and operation
            Operation operation = new ClipboardHolder(schematic)
                    .createPaste(editSession)
                    .to(BlockVector3.at(offsetX, 0, 0))
                    .build();
            
            Operations.complete(operation);
            editSession.close();
            
            // Find spawn points
            Location spawn1 = findSpawnPoint(worldName, offsetX, Material.RED_WOOL);
            Location spawn2 = findSpawnPoint(worldName, offsetX, Material.BLUE_WOOL);
            
            // Store arena
            Arena arena = new Arena(arenaId, offsetX, spawn1, spawn2);
            arenas.put(arenaId, arena);
            
            plugin.getLogger().info("Generated arena " + arenaId + 
                    " (Spawn1: " + (spawn1 != null ? "found" : "not found") + 
                    ", Spawn2: " + (spawn2 != null ? "found" : "not found") + ")");
            
        } catch (WorldEditException e) {
            plugin.getLogger().severe("Failed to paste schematic for arena " + arenaId + ": " + e.getMessage());
        }
    }
    
    /**
     * Find a spawn point by searching for a specific colored wool block.
     */
    private Location findSpawnPoint(String worldName, int offsetX, Material woolColor) {
        World world = Bukkit.getWorld(worldName);
        if (world == null || schematic == null) return null;
        
        // Search from low to high - the wool should be at arena floor level
        int searchStartY = 1;
        int searchHeight = 100;
        
        for (int y = searchStartY; y < searchStartY + searchHeight; y++) {
            for (int x = 0; x < schematic.getDimensions().x(); x++) {
                for (int z = 0; z < schematic.getDimensions().z(); z++) {
                    Block block = world.getBlockAt(offsetX + x, y, z);
                    if (block.getType() == woolColor) {
                        // Get the block above the wool to stand on
                        Block blockAbove = world.getBlockAt(offsetX + x, y + 1, z);
                        // If the block above is air, stand at y+1, otherwise find the next air block
                        int standY = y + 1;
                        if (blockAbove.getType() != Material.AIR) {
                            // Find the first air block above the wool
                            for (int dy = y + 1; dy < y + 10; dy++) {
                                if (world.getBlockAt(offsetX + x, dy, z).getType() == Material.AIR) {
                                    standY = dy;
                                    break;
                                }
                            }
                        }
                        plugin.getLogger().info("Found " + woolColor + " at (" + (offsetX + x) + ", " + y + ", " + z + "), teleport to Y=" + standY);
                        return new Location(world, offsetX + x, standY, z);
                    }
                }
            }
        }
        
        plugin.getLogger().warning("Could not find " + woolColor + " for spawn point, using default");
        // Default positions if wool not found (use center of arena at ground level)
        if (woolColor == Material.RED_WOOL) {
            return new Location(world, offsetX + 5, 1, 5);
        } else {
            return new Location(world, offsetX + schematic.getDimensions().x() - 5, 1, schematic.getDimensions().z() - 5);
        }
    }
    
    /**
     * Get an available arena for a duel.
     */
    public Optional<Arena> getAvailableArena() {
        for (Arena arena : arenas.values()) {
            if (!arena.isInUse()) {
                return Optional.of(arena);
            }
        }
        
        // No available arenas - generate more
        plugin.getLogger().info("All arenas in use, generating more...");
        generateArenas(5);
        
        // Try again
        for (Arena arena : arenas.values()) {
            if (!arena.isInUse()) {
                return Optional.of(arena);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Mark an arena as in use by a player.
     */
    public void occupyArena(int arenaId, Player player) {
        Arena arena = arenas.get(arenaId);
        if (arena != null) {
            arena.setInUse(true);
            playerArenaMap.put(player.getUniqueId(), arenaId);
        }
    }
    
    /**
     * Free an arena after a duel ends.
     */
    public void freeArena(int arenaId) {
        Arena arena = arenas.get(arenaId);
        if (arena != null) {
            arena.setInUse(false);
            
            // Reset the arena
            resetArena(arenaId);
        }
    }
    
    /**
     * Reset an arena by clearing items, entities, and specific blocks.
     */
    public void resetArena(int arenaId) {
        Arena arena = arenas.get(arenaId);
        if (arena == null) return;
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        
        int offsetX = arena.getOffsetX();
        
        // Get the schematic dimensions
        int width = schematic != null ? schematic.getDimensions().x() : 50;
        int height = schematic != null ? schematic.getDimensions().y() : 20;
        int depth = schematic != null ? schematic.getDimensions().z() : 50;
        
        // Clear entities in the arena area
        world.getEntities().forEach(entity -> {
            if (entity instanceof Player) return;
            
            Location loc = entity.getLocation();
            if (loc.getWorld() == world && 
                    loc.getBlockX() >= offsetX && loc.getBlockX() < offsetX + width &&
                    loc.getBlockY() < height + 20 && 
                    loc.getBlockZ() >= 0 && loc.getBlockZ() < depth) {
                entity.remove();
            }
        });
        
        // Clear specific blocks in the arena
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height + 20; y++) {
                for (int z = 0; z < depth; z++) {
                    Block block = world.getBlockAt(offsetX + x, y, z);
                    Material type = block.getType();
                    
                    // Remove water, lava, cobwebs, items, arrows, and oak planks
                    if (type == Material.WATER ||
                        type == Material.LAVA ||
                        type == Material.COBWEB ||
                        type == Material.ARROW ||
                        type == Material.OAK_PLANKS ||
                        type == Material.ITEM_FRAME ||
                        type == Material.FILLED_MAP ||
                        isItemBlock(type)) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        
        plugin.getLogger().info("Reset arena " + arenaId);
    }
    
    private boolean isItemBlock(Material type) {
        // Check if it's a dropped item (simplified check)
        return type.name().contains("SPAWN_EGG") ||
               type.name().contains("_HEAD") ||
               type.name().contains("_BANNER");
    }
    
    /**
     * Get the arena a player is currently in.
     */
    public Arena getPlayerArena(Player player) {
        Integer arenaId = playerArenaMap.get(player.getUniqueId());
        if (arenaId != null) {
            return arenas.get(arenaId);
        }
        return null;
    }
    
    /**
     * Remove player from arena tracking.
     */
    public void removePlayer(Player player) {
        Integer arenaId = playerArenaMap.remove(player.getUniqueId());
        if (arenaId != null) {
            Arena arena = arenas.get(arenaId);
            if (arena != null) {
                arena.setInUse(false);
            }
        }
    }
    
    /**
     * Get the arena world.
     */
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }
    
    /**
     * Get the world name.
     */
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Get all arenas.
     */
    public Collection<Arena> getArenas() {
        return arenas.values();
    }
    
    /**
     * Arena data class.
     */
    public static class Arena {
        private final int id;
        private final int offsetX;
        private final Location spawn1;
        private final Location spawn2;
        private boolean inUse;
        
        public Arena(int id, int offsetX, Location spawn1, Location spawn2) {
            this.id = id;
            this.offsetX = offsetX;
            this.spawn1 = spawn1;
            this.spawn2 = spawn2;
            this.inUse = false;
        }
        
        public int getId() {
            return id;
        }
        
        public int getOffsetX() {
            return offsetX;
        }
        
        public Location getSpawn1() {
            return spawn1;
        }
        
        public Location getSpawn2() {
            return spawn2;
        }
        
        public boolean isInUse() {
            return inUse;
        }
        
        public void setInUse(boolean inUse) {
            this.inUse = inUse;
        }
    }
}
