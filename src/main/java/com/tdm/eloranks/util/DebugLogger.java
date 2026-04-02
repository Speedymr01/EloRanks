package com.tdm.eloranks.util;

import com.tdm.eloranks.EloRanks;

/**
 * Debug logging utility that respects debug-mode config setting.
 */
public class DebugLogger {

    private final EloRanks plugin;
    
    public DebugLogger(EloRanks plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Log a debug message only if debug-mode is enabled in config.
     */
    public void log(String message) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
    
    /**
     * Log a debug warning.
     */
    public void warning(String message) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().warning("[DEBUG] " + message);
        }
    }
    
    /**
     * Log a debug severe message.
     */
    public void severe(String message) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().severe("[DEBUG] " + message);
        }
    }
}