package dev.ked.stormcraft.zones;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.integration.WorldGuardIntegration;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages boss arena zones where players are protected from storms.
 */
public class BossArenaManager {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final WorldGuardIntegration worldGuard;

    // Arena definitions
    private final List<BossArena> arenas = new ArrayList<>();
    private final Map<String, BossArena> arenasByName = new HashMap<>();

    public BossArenaManager(StormcraftPlugin plugin, ConfigManager config, WorldGuardIntegration worldGuard) {
        this.plugin = plugin;
        this.config = config;
        this.worldGuard = worldGuard;
        loadArenas();
    }

    /**
     * Loads boss arena definitions from config.
     */
    private void loadArenas() {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection arenasSection = cfg.getConfigurationSection("bossArenas");

        if (arenasSection == null) {
            return;
        }

        for (String arenaName : arenasSection.getKeys(false)) {
            ConfigurationSection arenaSection = arenasSection.getConfigurationSection(arenaName);
            if (arenaSection == null) continue;

            BossArena arena = new BossArena(arenaName);

            // Load WorldGuard region if specified
            if (arenaSection.contains("worldGuardRegion")) {
                arena.setWorldGuardRegion(arenaSection.getString("worldGuardRegion"));
            }

            // Load coordinate bounds if specified
            if (arenaSection.contains("bounds")) {
                ConfigurationSection boundsSection = arenaSection.getConfigurationSection("bounds");
                if (boundsSection != null) {
                    String world = boundsSection.getString("world", "world");
                    double minX = boundsSection.getDouble("minX");
                    double minY = boundsSection.getDouble("minY");
                    double minZ = boundsSection.getDouble("minZ");
                    double maxX = boundsSection.getDouble("maxX");
                    double maxY = boundsSection.getDouble("maxY");
                    double maxZ = boundsSection.getDouble("maxZ");

                    arena.setBounds(world, minX, minY, minZ, maxX, maxY, maxZ);
                }
            }

            // Load protection settings
            arena.setStormProtection(arenaSection.getBoolean("stormProtection", true));
            arena.setShowWarnings(arenaSection.getBoolean("showWarnings", true));
            arena.setDescription(arenaSection.getString("description", "Boss Arena"));

            arenas.add(arena);
            arenasByName.put(arenaName.toLowerCase(), arena);

            plugin.getLogger().info("Loaded boss arena: " + arenaName);
        }
    }

    /**
     * Checks if a location is within any boss arena.
     */
    public boolean isInBossArena(Location location) {
        for (BossArena arena : arenas) {
            if (arena.contains(location, worldGuard)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the boss arena at a location, if any.
     */
    public BossArena getArenaAt(Location location) {
        for (BossArena arena : arenas) {
            if (arena.contains(location, worldGuard)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Gets a boss arena by name.
     */
    public BossArena getArena(String name) {
        return arenasByName.get(name.toLowerCase());
    }

    /**
     * Gets all registered boss arenas.
     */
    public List<BossArena> getArenas() {
        return new ArrayList<>(arenas);
    }

    /**
     * Represents a boss arena zone.
     */
    public static class BossArena {
        private final String name;
        private String worldGuardRegion;
        private String world;
        private double minX, minY, minZ;
        private double maxX, maxY, maxZ;
        private boolean hasBounds = false;
        private boolean stormProtection = true;
        private boolean showWarnings = true;
        private String description;

        public BossArena(String name) {
            this.name = name;
        }

        public void setWorldGuardRegion(String region) {
            this.worldGuardRegion = region;
        }

        public void setBounds(String world, double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {
            this.world = world;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.hasBounds = true;
        }

        /**
         * Checks if a location is within this arena.
         */
        public boolean contains(Location loc, WorldGuardIntegration worldGuard) {
            // Check WorldGuard region first if available
            if (worldGuardRegion != null && worldGuard != null && worldGuard.isEnabled()) {
                return worldGuard.isInRegion(loc, worldGuardRegion);
            }

            // Check coordinate bounds
            if (hasBounds) {
                if (!loc.getWorld().getName().equals(world)) {
                    return false;
                }

                double x = loc.getX();
                double y = loc.getY();
                double z = loc.getZ();

                return x >= minX && x <= maxX &&
                       y >= minY && y <= maxY &&
                       z >= minZ && z <= maxZ;
            }

            return false;
        }

        // Getters and setters
        public String getName() { return name; }
        public String getWorldGuardRegion() { return worldGuardRegion; }
        public boolean hasStormProtection() { return stormProtection; }
        public void setStormProtection(boolean protection) { this.stormProtection = protection; }
        public boolean shouldShowWarnings() { return showWarnings; }
        public void setShowWarnings(boolean show) { this.showWarnings = show; }
        public String getDescription() { return description; }
        public void setDescription(String desc) { this.description = desc; }
    }
}