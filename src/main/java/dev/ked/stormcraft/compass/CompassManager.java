package dev.ked.stormcraft.compass;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.schedule.StormManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Manages compass targeting to point players away from storms.
 * Updates every second to keep compass pointing toward safety.
 */
public class CompassManager extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormManager stormManager;

    public CompassManager(StormcraftPlugin plugin, ConfigManager config, StormManager stormManager) {
        this.plugin = plugin;
        this.config = config;
        this.stormManager = stormManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip if player not in enabled world
            if (!config.getEnabledWorlds().contains(player.getWorld().getName())) {
                continue;
            }

            updatePlayerCompass(player);
        }
    }

    /**
     * Updates a player's compass to point away from the nearest storm.
     */
    private void updatePlayerCompass(Player player) {
        List<TravelingStorm> activeStorms = stormManager.getActiveStorms();

        if (activeStorms.isEmpty()) {
            // No storms - reset compass to spawn
            player.setCompassTarget(player.getWorld().getSpawnLocation());
            return;
        }

        Location playerLoc = player.getLocation();
        TravelingStorm closestStorm = findClosestStorm(player, activeStorms);

        if (closestStorm == null) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
            return;
        }

        Location stormLoc = closestStorm.getCurrentLocation();
        double distanceToCenter = playerLoc.distance(stormLoc);
        double damageRadius = closestStorm.getDamageRadius();

        // Calculate escape direction
        Location escapeTarget = calculateEscapeLocation(playerLoc, stormLoc, distanceToCenter, damageRadius);

        player.setCompassTarget(escapeTarget);
    }

    /**
     * Finds the closest storm to a player.
     */
    private TravelingStorm findClosestStorm(Player player, List<TravelingStorm> storms) {
        TravelingStorm closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (TravelingStorm storm : storms) {
            Location stormLoc = storm.getCurrentLocation();
            if (!stormLoc.getWorld().equals(player.getWorld())) {
                continue;
            }

            double distance = player.getLocation().distance(stormLoc);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = storm;
            }
        }

        return closest;
    }

    /**
     * Calculates the location the compass should point to for escape.
     * Points directly away from storm center.
     */
    private Location calculateEscapeLocation(Location playerLoc, Location stormLoc, double distanceToCenter, double damageRadius) {
        // Calculate direction away from storm
        double dx = playerLoc.getX() - stormLoc.getX();
        double dz = playerLoc.getZ() - stormLoc.getZ();

        // Normalize the direction
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.01) {
            // Player at exact storm center - pick arbitrary direction (north)
            dx = 0;
            dz = -1;
        } else {
            dx /= distance;
            dz /= distance;
        }

        // Point compass to edge of storm (or further away if already outside)
        double targetDistance = Math.max(damageRadius + 100, distanceToCenter + 500);

        double targetX = stormLoc.getX() + (dx * targetDistance);
        double targetZ = stormLoc.getZ() + (dz * targetDistance);

        return new Location(playerLoc.getWorld(), targetX, playerLoc.getY(), targetZ);
    }

    /**
     * Starts the compass update task.
     */
    public void start() {
        // Update every 20 ticks (1 second)
        this.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Stops the compass update task.
     */
    public void shutdown() {
        this.cancel();

        // Reset all player compasses to spawn
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (config.getEnabledWorlds().contains(player.getWorld().getName())) {
                player.setCompassTarget(player.getWorld().getSpawnLocation());
            }
        }
    }
}
