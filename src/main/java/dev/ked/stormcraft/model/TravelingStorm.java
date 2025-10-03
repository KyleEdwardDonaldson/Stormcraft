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

    // Storm phases
    private final boolean phasesEnabled;
    private final double formingPercent;
    private final double peakPercent;
    private final double dissipatingPercent;

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
                         double damageRadius, int rampUpSeconds,
                         boolean phasesEnabled, double formingPercent, double peakPercent, double dissipatingPercent) {
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
        this.phasesEnabled = phasesEnabled;
        this.formingPercent = formingPercent;
        this.peakPercent = peakPercent;
        this.dissipatingPercent = dissipatingPercent;
    }

    // Legacy constructor for backward compatibility
    public TravelingStorm(StormProfile profile, int durationSeconds, double actualDamagePerSecond,
                         Location spawnLocation, Location targetLocation, double movementSpeed,
                         double damageRadius, int rampUpSeconds) {
        this(profile, durationSeconds, actualDamagePerSecond, spawnLocation,
             List.of(targetLocation), movementSpeed, damageRadius, rampUpSeconds,
             false, 0.30, 0.50, 0.20); // Phases disabled for legacy
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
     * Gets the current storm phase based on elapsed time.
     */
    public StormPhase getCurrentPhase() {
        if (!phasesEnabled) {
            return StormPhase.PEAK; // No phases = always peak
        }

        int elapsedSeconds = originalDurationSeconds - remainingSeconds;
        double progress = (double) elapsedSeconds / originalDurationSeconds;

        if (progress < formingPercent) {
            return StormPhase.FORMING;
        } else if (progress < formingPercent + peakPercent) {
            return StormPhase.PEAK;
        } else {
            return StormPhase.DISSIPATING;
        }
    }

    /**
     * Gets the damage multiplier for the current phase.
     * FORMING: 0% → 100% over duration
     * PEAK: 100%
     * DISSIPATING: 100% → 0% over duration
     */
    public double getPhaseMultiplier() {
        if (!phasesEnabled) {
            return 1.0;
        }

        StormPhase phase = getCurrentPhase();
        int elapsedSeconds = originalDurationSeconds - remainingSeconds;
        double progress = (double) elapsedSeconds / originalDurationSeconds;

        switch (phase) {
            case FORMING:
                // Ramp up: 0% → 100% over FORMING phase
                double formingProgress = progress / formingPercent;
                return formingProgress;

            case PEAK:
                // Full damage
                return 1.0;

            case DISSIPATING:
                // Ramp down: 100% → 0% over DISSIPATING phase
                double dissipatingStart = formingPercent + peakPercent;
                double dissipatingProgress = (progress - dissipatingStart) / dissipatingPercent;
                return 1.0 - dissipatingProgress;

            default:
                return 1.0;
        }
    }

    /**
     * Gets the current damage per second with phase-based scaling applied.
     * Damage scales based on storm lifecycle phase.
     */
    public double getCurrentDamagePerSecond() {
        return actualDamagePerSecond * getPhaseMultiplier();
    }

    /**
     * Gets the radius multiplier for the current phase.
     * Storm starts small, grows to full size, then shrinks at the end.
     * FORMING: 20% → 100% over duration
     * PEAK: 100%
     * DISSIPATING: 100% (early) → 50% (late)
     */
    public double getRadiusMultiplier() {
        if (!phasesEnabled) {
            return 1.0; // No phases = always full size
        }

        StormPhase phase = getCurrentPhase();
        int elapsedSeconds = originalDurationSeconds - remainingSeconds;
        double progress = (double) elapsedSeconds / originalDurationSeconds;

        switch (phase) {
            case FORMING:
                // Grow from 20% to 100% over FORMING phase
                double formingProgress = progress / formingPercent;
                return 0.2 + (0.8 * formingProgress); // 20% → 100%

            case PEAK:
                // Full size throughout PEAK phase
                return 1.0;

            case DISSIPATING:
                // Stay full size for first 50% of dissipating, then shrink to 50%
                double dissipatingStart = formingPercent + peakPercent;
                double dissipatingProgress = (progress - dissipatingStart) / dissipatingPercent;

                if (dissipatingProgress < 0.5) {
                    return 1.0; // First half of dissipating = full size
                } else {
                    // Second half: shrink from 100% to 50%
                    double shrinkProgress = (dissipatingProgress - 0.5) / 0.5;
                    return 1.0 - (0.5 * shrinkProgress); // 100% → 50%
                }

            default:
                return 1.0;
        }
    }

    /**
     * Gets the current effective radius based on phase.
     * The radius grows as the storm forms and shrinks as it dissipates.
     */
    public double getCurrentRadius() {
        return damageRadius * getRadiusMultiplier();
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