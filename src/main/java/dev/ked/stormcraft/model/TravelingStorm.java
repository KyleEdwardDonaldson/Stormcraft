package dev.ked.stormcraft.model;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.List;

/**
 * Represents a storm that moves across the map following a path.
 */
public class TravelingStorm {
    private final StormProfile profile;
    private long startTimeMillis;
    private final int originalDurationSeconds;
    private final double actualDamagePerSecond;
    private final double damageRadius; // Radius in blocks
    private final int rampUpSeconds; // Time to reach full damage

    private Location currentLocation;
    private List<Location> waypoints; // Path waypoints
    private int currentWaypointIndex; // Current target waypoint
    private double movementSpeed; // Blocks per second
    private int remainingSeconds;

    // Temporary speed boost (for Stormclear ability)
    private double tempSpeedBoost = 0;
    private long tempSpeedBoostExpiry = 0;

    public TravelingStorm(StormProfile profile, int durationSeconds, double actualDamagePerSecond,
                         Location spawnLocation, List<Location> waypoints, double movementSpeed,
                         double damageRadius, int rampUpSeconds) {
        this.profile = profile;
        this.startTimeMillis = System.currentTimeMillis();
        this.originalDurationSeconds = durationSeconds;
        this.actualDamagePerSecond = actualDamagePerSecond;
        this.damageRadius = damageRadius;
        this.rampUpSeconds = rampUpSeconds;
        this.remainingSeconds = durationSeconds;
        this.currentLocation = spawnLocation.clone();
        this.waypoints = waypoints;
        this.currentWaypointIndex = 0;
        this.movementSpeed = movementSpeed;
    }

    // Legacy constructor for backward compatibility
    public TravelingStorm(StormProfile profile, int durationSeconds, double actualDamagePerSecond,
                         Location spawnLocation, Location targetLocation, double movementSpeed,
                         double damageRadius, int rampUpSeconds) {
        this(profile, durationSeconds, actualDamagePerSecond, spawnLocation,
             List.of(targetLocation), movementSpeed, damageRadius, rampUpSeconds);
    }

    /**
     * Moves the storm toward the next waypoint in its path.
     * @param deltaSeconds Time elapsed since last update
     * @return true if storm moved, false if at end of path
     */
    public boolean move(double deltaSeconds) {
        if (waypoints.isEmpty() || currentWaypointIndex >= waypoints.size()) {
            return false; // No more waypoints
        }

        Location currentTarget = waypoints.get(currentWaypointIndex);
        double distanceToTarget = currentLocation.distance(currentTarget);

        // If reached current waypoint, move to next
        if (distanceToTarget < 1.0) {
            currentWaypointIndex++;
            if (currentWaypointIndex >= waypoints.size()) {
                return false; // Reached end of path
            }
            currentTarget = waypoints.get(currentWaypointIndex);
            distanceToTarget = currentLocation.distance(currentTarget);
        }

        // Calculate how far to move this tick (with temp speed boost if active)
        double effectiveSpeed = movementSpeed;
        if (tempSpeedBoostExpiry > System.currentTimeMillis()) {
            effectiveSpeed = tempSpeedBoost;
        }
        double moveDistance = effectiveSpeed * deltaSeconds;

        // Don't overshoot current waypoint
        if (moveDistance >= distanceToTarget) {
            currentLocation = currentTarget.clone();
            return true;
        }

        // Calculate direction vector
        double dx = currentTarget.getX() - currentLocation.getX();
        double dz = currentTarget.getZ() - currentLocation.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Normalize and scale by move distance
        double moveX = (dx / distance) * moveDistance;
        double moveZ = (dz / distance) * moveDistance;

        // Update location
        currentLocation.add(moveX, 0, moveZ);
        return true;
    }

    /**
     * Checks if a location is within the storm's damage radius.
     */
    public boolean isLocationInStorm(Location location, double damageRadius) {
        if (!location.getWorld().equals(currentLocation.getWorld())) {
            return false;
        }

        double distanceSquared = getDistanceSquaredXZ(location, currentLocation);
        return distanceSquared <= (damageRadius * damageRadius);
    }

    /**
     * Gets horizontal distance squared between two locations (ignoring Y).
     */
    private double getDistanceSquaredXZ(Location loc1, Location loc2) {
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return dx * dx + dz * dz;
    }

    public void decrementRemaining(int seconds) {
        this.remainingSeconds = Math.max(0, this.remainingSeconds - seconds);
    }

    public boolean isExpired() {
        return remainingSeconds <= 0;
    }

    public boolean hasReachedTarget() {
        return currentWaypointIndex >= waypoints.size();
    }

    // Getters
    public StormProfile getProfile() {
        return profile;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public int getOriginalDurationSeconds() {
        return originalDurationSeconds;
    }

    public double getActualDamagePerSecond() {
        return actualDamagePerSecond;
    }

    /**
     * Gets the current damage per second with ramp-up applied.
     * Damage starts at 0 and increases to full over first 10% of storm life.
     */
    public double getCurrentDamagePerSecond() {
        if (rampUpSeconds <= 0) {
            return actualDamagePerSecond;
        }

        int elapsedSeconds = originalDurationSeconds - remainingSeconds;
        int rampDuration = (int) (originalDurationSeconds * 0.1); // 10% of storm's total duration

        if (elapsedSeconds >= rampDuration) {
            return actualDamagePerSecond; // Full damage
        }

        // Prevent division by zero
        if (rampDuration == 0) {
            return actualDamagePerSecond;
        }

        // Linear ramp: 0 → actualDamagePerSecond over first 10% of life
        double rampProgress = (double) elapsedSeconds / rampDuration;
        return actualDamagePerSecond * rampProgress;
    }

    public Location getCurrentLocation() {
        return currentLocation.clone();
    }

    public Location getTargetLocation() {
        // Return current waypoint or last waypoint if at end
        if (waypoints.isEmpty()) {
            return currentLocation.clone();
        }
        int targetIndex = Math.min(currentWaypointIndex, waypoints.size() - 1);
        return waypoints.get(targetIndex);
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public double getDamageRadius() {
        return damageRadius;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    /**
     * Backdates the storm's start time to simulate it having been alive longer.
     * Used when seeding initial storms to make them appear already in progress.
     */
    public void backdateStartTime(int elapsedSeconds) {
        this.startTimeMillis = System.currentTimeMillis() - (elapsedSeconds * 1000L);
    }

    public void setCurrentLocation(Location location) {
        this.currentLocation = location.clone();
    }

    /**
     * Sets a temporary speed boost for Stormclear ability.
     * @param speed Speed in blocks/second
     * @param durationSeconds How long the boost lasts
     */
    public void setTempSpeedBoost(double speed, int durationSeconds) {
        this.tempSpeedBoost = speed;
        this.tempSpeedBoostExpiry = System.currentTimeMillis() + (durationSeconds * 1000L);
    }

    /**
     * Updates the target location (for Stormclear pushing storms away).
     */
    public void setTargetLocation(Location newTarget) {
        // Replace current waypoint with new target
        if (!waypoints.isEmpty() && currentWaypointIndex < waypoints.size()) {
            waypoints = List.of(newTarget);
            currentWaypointIndex = 0;
        }
    }
}