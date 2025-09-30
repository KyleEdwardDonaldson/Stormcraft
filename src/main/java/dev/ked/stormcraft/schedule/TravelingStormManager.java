package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.integration.MapIntegrationManager;
import dev.ked.stormcraft.model.StormProfile;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import dev.ked.stormcraft.zones.ZoneSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Manages a traveling storm that moves across the map toward the Stormlands.
 * Updates storm position, Dynmap marker, and handles storm lifecycle.
 */
public class TravelingStormManager extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final ZoneManager zoneManager;
    private final MapIntegrationManager mapIntegrationManager;
    private final Random random = new Random();

    private TravelingStorm activeStorm;
    private Runnable onStormEndCallback;

    public TravelingStormManager(StormcraftPlugin plugin, ConfigManager config,
                                ZoneManager zoneManager, MapIntegrationManager mapIntegrationManager) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
        this.mapIntegrationManager = mapIntegrationManager;
    }

    /**
     * Starts a traveling storm.
     * @param profile Storm profile
     * @param durationSeconds Storm duration
     * @param actualDamage Actual damage per second
     * @param world World to spawn storm in
     * @param onEnd Callback when storm ends
     */
    public void startTravelingStorm(StormProfile profile, int durationSeconds,
                                   double actualDamage, World world, Runnable onEnd) {
        this.onStormEndCallback = onEnd;

        // Get spawn location and target
        Location spawnLocation = getRandomSpawnLocation(world);
        Location targetLocation = getStormlandsCenter(world);

        // Randomize movement speed from profile's range
        double minSpeed = profile.getMinMovementSpeed();
        double maxSpeed = profile.getMaxMovementSpeed();
        double actualSpeed = minSpeed + (random.nextDouble() * (maxSpeed - minSpeed));

        // Get ramp-up duration
        int rampUpSeconds = config.isDamageRampUpEnabled() ? config.getDamageRampUpSeconds() : 0;

        // Create traveling storm
        activeStorm = new TravelingStorm(
            profile,
            durationSeconds,
            actualDamage,
            spawnLocation,
            targetLocation,
            actualSpeed,
            rampUpSeconds
        );

        // Start movement task (runs every second)
        this.runTaskTimer(plugin, 0L, 20L);

        // Update map markers
        if (mapIntegrationManager != null) {
            mapIntegrationManager.updateStormMarker(activeStorm);
        }

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Traveling storm started at (" +
                                  (int)spawnLocation.getX() + ", " + (int)spawnLocation.getZ() +
                                  ") moving toward Stormlands at " + String.format("%.2f", actualSpeed) + " blocks/sec");
        }
    }

    @Override
    public void run() {
        if (activeStorm == null) {
            cancel();
            return;
        }

        // Decrement remaining time
        activeStorm.decrementRemaining(1);

        // Move storm toward target
        activeStorm.move(1.0); // Move based on 1 second elapsed

        // Update map markers
        if (mapIntegrationManager != null) {
            mapIntegrationManager.updateStormMarker(activeStorm);
        }

        // Check if storm expired
        if (activeStorm.isExpired()) {
            endStorm();
        }
    }

    /**
     * Ends the traveling storm.
     */
    public void endStorm() {
        if (activeStorm == null) {
            return;
        }

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Traveling storm ended at (" +
                                  (int)activeStorm.getCurrentLocation().getX() + ", " +
                                  (int)activeStorm.getCurrentLocation().getZ() + ")");
        }

        // Remove map markers
        if (mapIntegrationManager != null) {
            mapIntegrationManager.removeStormMarker();
        }

        activeStorm = null;
        cancel();

        // Trigger callback
        if (onStormEndCallback != null) {
            onStormEndCallback.run();
        }
    }

    /**
     * Gets a random spawn location for the storm.
     * Can spawn at zone border or use old logic based on config.
     * Uses biome preferences to favor certain biomes in each zone.
     */
    private Location getRandomSpawnLocation(World world) {
        if (!zoneManager.isEnabled()) {
            // Random location within 5000 blocks of spawn
            return getBestBiomeLocation(world, 0, 0, 0, 5000, null);
        }

        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();

        // New spawn logic: at border between Storm Zone and Safe Zone
        if (config.isSpawnAtBorder()) {
            double borderRadius = zoneManager.getStormZoneRadius(); // Exact border
            double bias = config.getBorderBias(); // 0.0 = Safe Zone, 1.0 = Storm Zone
            double spread = config.getBorderSpread(); // +/- spread around border

            // Get zone at border for biome preferences
            Location borderLoc = new Location(world, centerX + borderRadius, 64, centerZ);
            dev.ked.stormcraft.zones.ZoneManager.ZoneType zone = zoneManager.getZoneAt(borderLoc);

            return getBestBiomeLocation(world, centerX, centerZ, borderRadius - spread, borderRadius + spread, zone);
        }

        // Old logic: Spawn outside Storm Zone, moving toward Stormlands
        double minRadius = zoneManager.getStormZoneRadius() + 500;
        double maxRadius = minRadius + 2000;

        Location testLoc = new Location(world, centerX + minRadius, 64, centerZ);
        dev.ked.stormcraft.zones.ZoneManager.ZoneType zone = zoneManager.getZoneAt(testLoc);

        return getBestBiomeLocation(world, centerX, centerZ, minRadius, maxRadius, zone);
    }

    /**
     * Generates multiple candidate locations and picks the best one based on biome preferences.
     * Tries up to 10 locations and picks the one with the highest biome weight.
     */
    private Location getBestBiomeLocation(World world, double centerX, double centerZ,
                                         double minRadius, double maxRadius, dev.ked.stormcraft.zones.ZoneManager.ZoneType zone) {
        Location bestLocation = null;
        double bestWeight = 0.0;
        int attempts = 10; // Try 10 candidates

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minRadius + (random.nextDouble() * (maxRadius - minRadius));
            double x = centerX + (Math.cos(angle) * distance);
            double z = centerZ + (Math.sin(angle) * distance);

            Location candidate = new Location(world, x, 64, z);
            org.bukkit.block.Biome biome = world.getBiome((int)x, 64, (int)z);

            // Get biome weight from zone settings
            double weight = 1.0; // Default neutral weight
            if (zone != null) {
                ZoneSettings settings = zoneManager.getSettingsForZone(zone);
                if (settings != null) {
                    weight = settings.getBiomeWeight(biome);
                }
            }

            // Pick this location if it's the best so far
            if (weight > bestWeight || bestLocation == null) {
                bestWeight = weight;
                bestLocation = candidate;
            }

            // If we found a highly preferred biome (2.5x+), use it immediately
            if (weight >= 2.5) {
                break;
            }
        }

        return bestLocation;
    }

    /**
     * Gets the center of the Stormlands (storm target).
     */
    private Location getStormlandsCenter(World world) {
        if (!zoneManager.isEnabled()) {
            return new Location(world, 0, 64, 0); // World spawn
        }

        return new Location(world, zoneManager.getCenterX(), 64, zoneManager.getCenterZ());
    }

    /**
     * Checks if a location is within the storm's damage radius.
     */
    public boolean isLocationInStorm(Location location) {
        if (activeStorm == null) {
            return false;
        }

        return activeStorm.isLocationInStorm(location, config.getStormDamageRadius());
    }

    public TravelingStorm getActiveStorm() {
        return activeStorm;
    }

    public boolean hasActiveStorm() {
        return activeStorm != null;
    }

    public void setActiveStorm(TravelingStorm storm) {
        this.activeStorm = storm;
    }
}