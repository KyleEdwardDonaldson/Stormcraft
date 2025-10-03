package dev.ked.stormcraft.zones;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.integration.WorldGuardIntegration;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Manages zone boundaries and determines which zone a location is in.
 * Prioritizes WorldGuard regions (stormlands, stormzone, safezone) over circular zones.
 * Circular zones are used as fallback if WorldGuard is not available or regions not found.
 */
public class ZoneManager {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final WorldGuardIntegration worldGuardIntegration;

    // Zone center coordinates
    private double centerX;
    private double centerZ;

    // Zone radii (squared for performance)
    private double stormlandsRadiusSquared;
    private double stormZoneRadiusSquared;
    private double safeZoneRadiusSquared;

    // Zone settings
    private ZoneSettings stormlandsSettings;
    private ZoneSettings stormZoneSettings;
    private ZoneSettings safeZoneSettings;

    private boolean enabled;
    private boolean usingWorldGuardRegions = false;

    public ZoneManager(StormcraftPlugin plugin, ConfigManager config, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.config = config;
        this.worldGuardIntegration = worldGuardIntegration;
        loadZoneConfig();
    }

    public void loadZoneConfig() {
        this.enabled = config.isZoneSystemEnabled();

        // Load zone settings regardless (used for damage multipliers, etc.)
        this.stormlandsSettings = config.getStormlandsSettings();
        this.stormZoneSettings = config.getStormZoneSettings();
        this.safeZoneSettings = config.getSafeZoneSettings();

        // Check if WorldGuard regions are available (priority)
        if (worldGuardIntegration.isEnabled()) {
            World world = plugin.getServer().getWorlds().get(0); // Primary world
            boolean hasStormlands = worldGuardIntegration.hasRegion(world, "stormlands");
            boolean hasStormZone = worldGuardIntegration.hasRegion(world, "stormzone");
            boolean hasSafeZone = worldGuardIntegration.hasRegion(world, "safezone");

            if (hasStormlands && hasStormZone && hasSafeZone) {
                usingWorldGuardRegions = true;
                plugin.getLogger().info("Zone system: Using WorldGuard regions (stormlands, stormzone, safezone)");
                return;
            } else if (hasStormlands || hasStormZone || hasSafeZone) {
                plugin.getLogger().warning("Zone system: Some WorldGuard regions found but not all three (stormlands, stormzone, safezone). Falling back to circular zones.");
            }
        }

        // Fallback to circular zones
        usingWorldGuardRegions = false;

        if (!enabled) {
            plugin.getLogger().info("Zone system: Disabled (circular zones not enabled, WorldGuard regions not found)");
            return;
        }

        this.centerX = config.getZoneCenterX();
        this.centerZ = config.getZoneCenterZ();

        // Load zone radii and square them for distance checks
        double stormlandsRadius = config.getStormlandsRadius();
        double stormZoneRadius = config.getStormZoneRadius();
        double safeZoneRadius = config.getSafeZoneRadius();

        this.stormlandsRadiusSquared = stormlandsRadius * stormlandsRadius;
        this.stormZoneRadiusSquared = stormZoneRadius * stormZoneRadius;
        this.safeZoneRadiusSquared = safeZoneRadius * safeZoneRadius;

        plugin.getLogger().info("Zone system: Using circular zones - Stormlands=" + stormlandsRadius +
                               ", StormZone=" + stormZoneRadius +
                               ", SafeZone=" + safeZoneRadius);
    }

    /**
     * Determines which zone a location is in.
     * Prioritizes WorldGuard regions, falls back to circular zones.
     * @param location The location to check
     * @return The zone type
     */
    public ZoneType getZoneAt(Location location) {
        // Use WorldGuard regions if available
        if (usingWorldGuardRegions) {
            if (worldGuardIntegration.isInRegion(location, "stormlands")) {
                return ZoneType.STORMLANDS;
            } else if (worldGuardIntegration.isInRegion(location, "stormzone")) {
                return ZoneType.STORM_ZONE;
            } else if (worldGuardIntegration.isInRegion(location, "safezone")) {
                return ZoneType.SAFE_ZONE;
            }
            // If not in any defined region, default to safe zone
            return ZoneType.SAFE_ZONE;
        }

        // Fallback to circular zones
        if (!enabled) {
            return ZoneType.SAFE_ZONE; // Default to safe if zones disabled
        }

        double distanceSquared = getDistanceSquaredFromCenter(location);

        if (distanceSquared <= stormlandsRadiusSquared) {
            return ZoneType.STORMLANDS;
        } else if (distanceSquared <= stormZoneRadiusSquared) {
            return ZoneType.STORM_ZONE;
        } else {
            return ZoneType.SAFE_ZONE;
        }
    }

    /**
     * Gets the distance squared from a location to the zone center.
     * @param location The location
     * @return Distance squared (avoiding expensive sqrt)
     */
    public double getDistanceSquaredFromCenter(Location location) {
        double dx = location.getX() - centerX;
        double dz = location.getZ() - centerZ;
        return dx * dx + dz * dz;
    }

    /**
     * Gets the actual distance from a location to the zone center.
     * @param location The location
     * @return Distance in blocks
     */
    public double getDistanceFromCenter(Location location) {
        return Math.sqrt(getDistanceSquaredFromCenter(location));
    }

    /**
     * Checks if a location is in the Stormlands.
     */
    public boolean isInStormlands(Location location) {
        return enabled && getZoneAt(location) == ZoneType.STORMLANDS;
    }

    /**
     * Checks if a location is in the Storm Zone.
     */
    public boolean isInStormZone(Location location) {
        return enabled && getZoneAt(location) == ZoneType.STORM_ZONE;
    }

    /**
     * Checks if a location is in a Safe Zone.
     */
    public boolean isInSafeZone(Location location) {
        return !enabled || getZoneAt(location) == ZoneType.SAFE_ZONE;
    }

    /**
     * Gets the settings for a specific zone type.
     */
    public ZoneSettings getSettingsForZone(ZoneType zoneType) {
        return switch (zoneType) {
            case STORMLANDS -> stormlandsSettings;
            case STORM_ZONE -> stormZoneSettings;
            case SAFE_ZONE -> safeZoneSettings;
        };
    }

    /**
     * Gets the settings for the zone at a location.
     */
    public ZoneSettings getSettingsAt(Location location) {
        return getSettingsForZone(getZoneAt(location));
    }

    // Getters
    public boolean isEnabled() {
        return enabled || usingWorldGuardRegions;
    }

    public boolean isUsingWorldGuardRegions() {
        return usingWorldGuardRegions;
    }

    public WorldGuardIntegration getWorldGuardIntegration() {
        return worldGuardIntegration;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getStormlandsRadius() {
        return Math.sqrt(stormlandsRadiusSquared);
    }

    public double getStormZoneRadius() {
        return Math.sqrt(stormZoneRadiusSquared);
    }

    public double getSafeZoneRadius() {
        return Math.sqrt(safeZoneRadiusSquared);
    }

    public ZoneSettings getStormlandsSettings() {
        return stormlandsSettings;
    }

    public ZoneSettings getStormZoneSettings() {
        return stormZoneSettings;
    }

    public ZoneSettings getSafeZoneSettings() {
        return safeZoneSettings;
    }

    /**
     * Zone types for the map.
     */
    public enum ZoneType {
        STORMLANDS("Stormlands", 0xFF4444),      // Red
        STORM_ZONE("Storm Zone", 0xFFAA00),       // Orange
        SAFE_ZONE("Safe Zone", 0x44FF44);         // Green

        private final String displayName;
        private final int color;

        ZoneType(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }
    }
}