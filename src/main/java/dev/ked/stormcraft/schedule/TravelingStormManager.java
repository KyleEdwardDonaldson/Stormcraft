package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
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
    private final Random random = new Random();

    private TravelingStorm activeStorm;
    private Runnable onStormEndCallback;

    // Performance optimization: track if players are near storm
    private int tickCounter = 0;
    private boolean wasActiveLastTick = false;

    // Phase tracking for warnings
    private dev.ked.stormcraft.model.StormPhase lastPhase = null;
    private boolean sentPhaseWarning = false;

    public TravelingStormManager(StormcraftPlugin plugin, ConfigManager config, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
    }

    /**
     * Starts a traveling storm.
     * @param profile Storm profile
     * @param durationSeconds Storm duration
     * @param actualDamage Actual damage per second
     * @param world World to spawn storm in
     * @param initialRemainingSeconds Initial time remaining (for seeding storms in progress)
     * @param onEnd Callback when storm ends
     */
    public void startTravelingStorm(StormProfile profile, int durationSeconds,
                                   double actualDamage, World world, int initialRemainingSeconds, Runnable onEnd) {
        this.onStormEndCallback = onEnd;

        // Get spawn location
        Location spawnLocation = getRandomSpawnLocation(world);

        // Randomize movement speed from profile's range
        double minSpeed = profile.getMinMovementSpeed();
        double maxSpeed = profile.getMaxMovementSpeed();
        double actualSpeed = minSpeed + (random.nextDouble() * (maxSpeed - minSpeed));

        // Generate path waypoints (straight, curved, or wandering)
        java.util.List<Location> waypoints = generateStormPath(world, spawnLocation, durationSeconds, actualSpeed);

        // Randomize radius from profile's range
        double minRadius = profile.getMinRadius();
        double maxRadius = profile.getMaxRadius();
        double actualRadius = minRadius + (random.nextDouble() * (maxRadius - minRadius));

        // Get ramp-up duration
        int rampUpSeconds = config.isDamageRampUpEnabled() ? config.getDamageRampUpSeconds() : 0;

        // Get phase configuration
        boolean phasesEnabled = config.isStormPhasesEnabled();
        double formingPercent = config.getFormingPercent();
        double peakPercent = config.getPeakPercent();
        double dissipatingPercent = config.getDissipatingPercent();

        // Create traveling storm
        activeStorm = new TravelingStorm(
            profile,
            durationSeconds,
            actualDamage,
            spawnLocation,
            waypoints,
            actualSpeed,
            actualRadius,
            rampUpSeconds,
            phasesEnabled,
            formingPercent,
            peakPercent,
            dissipatingPercent
        );

        // Set initial remaining time (for simulating storms already in progress)
        if (initialRemainingSeconds != durationSeconds) {
            int elapsedSeconds = durationSeconds - initialRemainingSeconds;
            activeStorm.setRemainingSeconds(initialRemainingSeconds);
            activeStorm.backdateStartTime(elapsedSeconds);
        }

        // Start movement task (runs every second)
        this.runTaskTimer(plugin, 0L, 20L);

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Traveling storm started at (" +
                                  (int)spawnLocation.getX() + ", " + (int)spawnLocation.getZ() +
                                  ") with " + waypoints.size() + " waypoints at " + String.format("%.2f", actualSpeed) + " blocks/sec");
        }
    }

    @Override
    public void run() {
        if (activeStorm == null) {
            cancel();
            return;
        }

        tickCounter++;

        // Check if any players are nearby
        boolean isActive = isAnyPlayerNearby();
        double activeRange = config.getStormActiveRange();
        int updateInterval = isActive ? config.getActiveUpdateInterval() : config.getDormantUpdateInterval();
        int ticksPerUpdate = updateInterval / 20; // Convert ticks to seconds (20 ticks = 1 second)

        // Log state transitions
        if (isActive != wasActiveLastTick && config.isLogScheduling()) {
            String state = isActive ? "ACTIVE" : "DORMANT";
            plugin.getLogger().info(String.format("Storm at (%d, %d) became %s (%s players within %.0f blocks)",
                (int)activeStorm.getCurrentLocation().getX(),
                (int)activeStorm.getCurrentLocation().getZ(),
                state,
                isActive ? "has" : "no",
                activeRange));
        }
        wasActiveLastTick = isActive;

        // Only update on configured interval
        if (tickCounter % ticksPerUpdate != 0) {
            return;
        }

        // Decrement remaining time (by number of seconds elapsed)
        activeStorm.decrementRemaining(ticksPerUpdate);

        // Check for phase changes and send warnings
        checkPhaseChange();

        // Only move and update map if active or on dormant update interval
        if (isActive || tickCounter % (config.getDormantUpdateInterval() / 20) == 0) {
            // Move storm toward target
            activeStorm.move(ticksPerUpdate); // Move based on elapsed seconds
        }

        // Check if storm expired
        if (activeStorm.isExpired()) {
            endStorm();
        }
    }

    /**
     * Checks for phase transitions and sends warnings to nearby players.
     */
    private void checkPhaseChange() {
        if (activeStorm == null || !config.isStormPhasesEnabled()) {
            return;
        }

        dev.ked.stormcraft.model.StormPhase currentPhase = activeStorm.getCurrentPhase();
        int remainingSeconds = activeStorm.getRemainingSeconds();
        int warningSeconds = config.getPhaseChangeWarningSeconds();

        // Check if phase changed
        if (lastPhase != currentPhase) {
            lastPhase = currentPhase;
            sentPhaseWarning = false;

            // Announce phase change to nearby players
            announcePhaseChange(currentPhase);
        }

        // Check if we need to send warning for upcoming phase change
        if (!sentPhaseWarning) {
            int elapsedSeconds = activeStorm.getOriginalDurationSeconds() - remainingSeconds;
            double progress = (double) elapsedSeconds / activeStorm.getOriginalDurationSeconds();

            double formingEnd = config.getFormingPercent();
            double peakEnd = formingEnd + config.getPeakPercent();

            // Calculate seconds until next phase
            int secondsUntilNextPhase = -1;
            if (currentPhase == dev.ked.stormcraft.model.StormPhase.FORMING) {
                int formingEndSeconds = (int)(activeStorm.getOriginalDurationSeconds() * formingEnd);
                secondsUntilNextPhase = formingEndSeconds - elapsedSeconds;
            } else if (currentPhase == dev.ked.stormcraft.model.StormPhase.PEAK) {
                int peakEndSeconds = (int)(activeStorm.getOriginalDurationSeconds() * peakEnd);
                secondsUntilNextPhase = peakEndSeconds - elapsedSeconds;
            }

            // Send warning if within warning window
            if (secondsUntilNextPhase > 0 && secondsUntilNextPhase <= warningSeconds) {
                sendPhaseWarning(secondsUntilNextPhase);
                sentPhaseWarning = true;
            }
        }
    }

    /**
     * Announces a phase change to players near the storm.
     */
    private void announcePhaseChange(dev.ked.stormcraft.model.StormPhase newPhase) {
        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text("⛈ Storm Phase: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(newPhase.getDisplayName(), getPhaseColor(newPhase)))
                .append(net.kyori.adventure.text.Component.text(" " + newPhase.getSymbol()));

        sendMessageToNearbyPlayers(message);
    }

    /**
     * Sends a warning about upcoming phase change to nearby players.
     */
    private void sendPhaseWarning(int secondsUntil) {
        dev.ked.stormcraft.model.StormPhase nextPhase = getNextPhase(activeStorm.getCurrentPhase());
        if (nextPhase == null) return;

        net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text("⚠ Storm entering ", net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .append(net.kyori.adventure.text.Component.text(nextPhase.getDisplayName(), getPhaseColor(nextPhase)))
                .append(net.kyori.adventure.text.Component.text(" phase in " + secondsUntil + "s", net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        sendMessageToNearbyPlayers(message);
    }

    /**
     * Sends a message to all players within the storm's radius.
     */
    private void sendMessageToNearbyPlayers(net.kyori.adventure.text.Component message) {
        if (activeStorm == null) return;

        org.bukkit.Location stormLoc = activeStorm.getCurrentLocation();
        double radius = activeStorm.getCurrentRadius();
        double radiusSquared = radius * radius;

        org.bukkit.Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getWorld().equals(stormLoc.getWorld()))
            .filter(p -> {
                org.bukkit.Location pLoc = p.getLocation();
                double dx = pLoc.getX() - stormLoc.getX();
                double dz = pLoc.getZ() - stormLoc.getZ();
                return (dx * dx + dz * dz) <= radiusSquared;
            })
            .forEach(p -> p.sendMessage(message));
    }

    /**
     * Gets the next phase in the storm lifecycle.
     */
    private dev.ked.stormcraft.model.StormPhase getNextPhase(dev.ked.stormcraft.model.StormPhase current) {
        return switch (current) {
            case FORMING -> dev.ked.stormcraft.model.StormPhase.PEAK;
            case PEAK -> dev.ked.stormcraft.model.StormPhase.DISSIPATING;
            case DISSIPATING -> null; // No next phase
        };
    }

    /**
     * Gets the color for a phase.
     */
    private net.kyori.adventure.text.format.NamedTextColor getPhaseColor(dev.ked.stormcraft.model.StormPhase phase) {
        return switch (phase) {
            case FORMING -> net.kyori.adventure.text.format.NamedTextColor.GRAY;
            case PEAK -> net.kyori.adventure.text.format.NamedTextColor.RED;
            case DISSIPATING -> net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
        };
    }

    /**
     * Checks if any players are within active range of the storm.
     */
    private boolean isAnyPlayerNearby() {
        if (activeStorm == null) {
            return false;
        }

        double activeRange = config.getStormActiveRange();
        double activeRangeSquared = activeRange * activeRange;
        Location stormLoc = activeStorm.getCurrentLocation();

        return Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getWorld().equals(stormLoc.getWorld()))
            .anyMatch(p -> {
                Location pLoc = p.getLocation();
                double dx = pLoc.getX() - stormLoc.getX();
                double dz = pLoc.getZ() - stormLoc.getZ();
                double distSquared = dx * dx + dz * dz;
                return distSquared <= activeRangeSquared;
            });
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

        activeStorm = null;
        cancel();

        // Trigger callback
        if (onStormEndCallback != null) {
            onStormEndCallback.run();
        }
    }

    /**
     * Generates a varied path for the storm.
     * Paths can be straight, curved, short, or long.
     */
    private java.util.List<Location> generateStormPath(World world, Location spawn, int duration, double speed) {
        java.util.List<Location> waypoints = new java.util.ArrayList<>();

        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();
        double stormlandsRadius = zoneManager.getStormlandsRadius();

        // Calculate total distance storm can travel
        double maxTravelDistance = speed * duration;

        // Random path type
        double pathType = random.nextDouble();

        if (pathType < 0.4) {
            // 40% chance: Straight line through Stormlands
            Location stormlandsPoint = getRandomPointInStormlands(world);
            double dx = stormlandsPoint.getX() - spawn.getX();
            double dz = stormlandsPoint.getZ() - spawn.getZ();

            // Extend line beyond Stormlands
            double targetX = stormlandsPoint.getX() + dx * 2;
            double targetZ = stormlandsPoint.getZ() + dz * 2;
            waypoints.add(new Location(world, targetX, 64, targetZ));

        } else if (pathType < 0.7) {
            // 30% chance: Curved path (arc) through Stormlands
            Location stormlandsPoint = getRandomPointInStormlands(world);

            // Create 3-point arc: spawn -> stormlands -> curved endpoint
            waypoints.add(stormlandsPoint);

            // Add curve point perpendicular to the line
            double dx = stormlandsPoint.getX() - spawn.getX();
            double dz = stormlandsPoint.getZ() - spawn.getZ();
            double perpX = -dz; // Perpendicular vector
            double perpZ = dx;
            double perpLen = Math.sqrt(perpX * perpX + perpZ * perpZ);

            // Random curve strength
            double curveStrength = (random.nextDouble() - 0.5) * 4000;
            double endX = stormlandsPoint.getX() + dx + (perpX / perpLen) * curveStrength;
            double endZ = stormlandsPoint.getZ() + dz + (perpZ / perpLen) * curveStrength;
            waypoints.add(new Location(world, endX, 64, endZ));

        } else {
            // 30% chance: Wandering path with multiple waypoints
            int numWaypoints = 3 + random.nextInt(4); // 3-6 waypoints
            double segmentDistance = maxTravelDistance / numWaypoints;

            Location current = spawn.clone();

            // At least one waypoint must be in Stormlands
            int stormlandsWaypoint = random.nextInt(numWaypoints);

            for (int i = 0; i < numWaypoints; i++) {
                Location next;
                if (i == stormlandsWaypoint) {
                    next = getRandomPointInStormlands(world);
                } else {
                    // Random direction from current point
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distance = segmentDistance * (0.5 + random.nextDouble()); // Vary segment length
                    double nextX = current.getX() + Math.cos(angle) * distance;
                    double nextZ = current.getZ() + Math.sin(angle) * distance;
                    next = new Location(world, nextX, 64, nextZ);
                }
                waypoints.add(next);
                current = next;
            }
        }

        return waypoints;
    }

    /**
     * Gets a random point within the Stormlands zone.
     */
    private Location getRandomPointInStormlands(World world) {
        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();
        double stormlandsRadius = zoneManager.getStormlandsRadius();

        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * stormlandsRadius;

        double x = centerX + Math.cos(angle) * distance;
        double z = centerZ + Math.sin(angle) * distance;

        return new Location(world, x, 64, z);
    }

    /**
     * Gets a random spawn location for the storm.
     * If using WorldGuard regions, spawns in stormzone or stormlands.
     * Otherwise uses circular zones with weighted distribution.
     */
    private Location getRandomSpawnLocation(World world) {
        // Priority: Use WorldGuard regions if available
        if (zoneManager.isUsingWorldGuardRegions()) {
            // Try stormzone first (70% chance), then stormlands (30% chance)
            String regionName = random.nextDouble() < 0.7 ? "stormzone" : "stormlands";
            Location location = zoneManager.getWorldGuardIntegration().getRandomLocationInRegion(world, regionName, random);

            if (location != null) {
                return location;
            }

            // Fallback: try the other region
            regionName = regionName.equals("stormzone") ? "stormlands" : "stormzone";
            location = zoneManager.getWorldGuardIntegration().getRandomLocationInRegion(world, regionName, random);

            if (location != null) {
                return location;
            }

            plugin.getLogger().warning("Failed to get spawn location from WorldGuard regions, using fallback");
        }

        // Fallback: Circular zones
        if (!zoneManager.isEnabled()) {
            // Random location within 5000 blocks of spawn
            return getBestBiomeLocation(world, 0, 0, 0, 5000, null);
        }

        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();

        // Spawn anywhere from center to edge of safe zone (0-12000 radius)
        double maxRadius = zoneManager.getSafeZoneRadius();

        // Random location across entire map
        return getBestBiomeLocation(world, centerX, centerZ, 0, maxRadius, null);
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

        return activeStorm.isLocationInStorm(location, activeStorm.getCurrentRadius());
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