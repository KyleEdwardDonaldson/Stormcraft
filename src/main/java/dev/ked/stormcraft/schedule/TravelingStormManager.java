package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.integration.DynmapIntegration;
import dev.ked.stormcraft.model.StormProfile;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
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
    private final DynmapIntegration dynmapIntegration;
    private final Random random = new Random();

    private TravelingStorm activeStorm;
    private Runnable onStormEndCallback;

    public TravelingStormManager(StormcraftPlugin plugin, ConfigManager config,
                                ZoneManager zoneManager, DynmapIntegration dynmapIntegration) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
        this.dynmapIntegration = dynmapIntegration;
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

        // Create traveling storm
        activeStorm = new TravelingStorm(
            profile,
            durationSeconds,
            actualDamage,
            spawnLocation,
            targetLocation,
            config.getStormMovementSpeed()
        );

        // Start movement task (runs every second)
        this.runTaskTimer(plugin, 0L, 20L);

        // Update Dynmap marker
        if (dynmapIntegration != null && dynmapIntegration.isEnabled()) {
            dynmapIntegration.updateStormMarker(activeStorm);
        }

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Traveling storm started at (" +
                                  (int)spawnLocation.getX() + ", " + (int)spawnLocation.getZ() +
                                  ") moving toward Stormlands");
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

        // Update Dynmap marker
        if (dynmapIntegration != null && dynmapIntegration.isEnabled()) {
            dynmapIntegration.updateStormMarker(activeStorm);
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

        // Remove Dynmap marker
        if (dynmapIntegration != null && dynmapIntegration.isEnabled()) {
            dynmapIntegration.removeStormMarker();
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
     * If zones enabled, spawns outside Stormlands.
     * Otherwise, spawns anywhere within map bounds.
     */
    private Location getRandomSpawnLocation(World world) {
        if (!zoneManager.isEnabled()) {
            // Random location within 5000 blocks of spawn
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * 5000;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            return new Location(world, x, 64, z);
        }

        // Spawn outside Storm Zone, moving toward Stormlands
        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();
        double minRadius = zoneManager.getStormZoneRadius() + 500; // 500 blocks outside
        double maxRadius = minRadius + 2000; // Up to 2000 blocks further out

        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = minRadius + (random.nextDouble() * (maxRadius - minRadius));

        double x = centerX + (Math.cos(angle) * distance);
        double z = centerZ + (Math.sin(angle) * distance);

        return new Location(world, x, 64, z);
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