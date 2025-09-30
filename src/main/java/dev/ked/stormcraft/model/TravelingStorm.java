package dev.ked.stormcraft.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a storm that moves across the map toward the Stormlands.
 */
public class TravelingStorm {
    private final StormProfile profile;
    private final long startTimeMillis;
    private final int originalDurationSeconds;
    private final double actualDamagePerSecond;
    private final int rampUpSeconds; // Time to reach full damage

    private Location currentLocation;
    private Location targetLocation; // Center of Stormlands
    private double movementSpeed; // Blocks per second
    private int remainingSeconds;

    public TravelingStorm(StormProfile profile, int durationSeconds, double actualDamagePerSecond,
                         Location spawnLocation, Location targetLocation, double movementSpeed, int rampUpSeconds) {
        this.profile = profile;
        this.startTimeMillis = System.currentTimeMillis();
        this.originalDurationSeconds = durationSeconds;
        this.actualDamagePerSecond = actualDamagePerSecond;
        this.rampUpSeconds = rampUpSeconds;
        this.remainingSeconds = durationSeconds;
        this.currentLocation = spawnLocation.clone();
        this.targetLocation = targetLocation;
        this.movementSpeed = movementSpeed;
    }

    /**
     * Moves the storm toward the target location.
     * @param deltaSeconds Time elapsed since last update
     * @return true if storm moved, false if already at target
     */
    public boolean move(double deltaSeconds) {
        double distanceToTarget = currentLocation.distance(targetLocation);

        // If already at target, don't move
        if (distanceToTarget < 1.0) {
            return false;
        }

        // Calculate how far to move this tick
        double moveDistance = movementSpeed * deltaSeconds;

        // Don't overshoot target
        if (moveDistance >= distanceToTarget) {
            currentLocation = targetLocation.clone();
            return true;
        }

        // Calculate direction vector
        double dx = targetLocation.getX() - currentLocation.getX();
        double dz = targetLocation.getZ() - currentLocation.getZ();
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
        return currentLocation.distance(targetLocation) < 1.0;
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
     * Damage starts at 0 and increases to full over rampUpSeconds.
     */
    public double getCurrentDamagePerSecond() {
        if (rampUpSeconds <= 0) {
            return actualDamagePerSecond;
        }

        int elapsedSeconds = originalDurationSeconds - remainingSeconds;
        if (elapsedSeconds >= rampUpSeconds) {
            return actualDamagePerSecond; // Full damage
        }

        // Linear ramp: 0 → actualDamagePerSecond over rampUpSeconds
        double rampProgress = (double) elapsedSeconds / rampUpSeconds;
        return actualDamagePerSecond * rampProgress;
    }

    public Location getCurrentLocation() {
        return currentLocation.clone();
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public void setCurrentLocation(Location location) {
        this.currentLocation = location.clone();
    }
}