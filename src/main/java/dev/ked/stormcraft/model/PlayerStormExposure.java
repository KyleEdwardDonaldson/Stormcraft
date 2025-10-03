package dev.ked.stormcraft.model;

import java.util.UUID;

/**
 * Tracks a player's storm exposure state, including ramp-up time and cooldowns.
 */
public class PlayerStormExposure {
    private final UUID playerId;
    private long firstExposureTime; // When player first entered storm
    private long lastExposureTime; // Last time player was in storm
    private long lastDamageTime; // Last time player took storm damage
    private double accumulatedExposure; // Seconds of exposure (for ramp-up)
    private boolean inStorm; // Currently in a storm
    private boolean onCooldown; // In cooldown period after leaving storm

    // Immunity tracking
    private long immunityExpiry; // Timestamp when immunity expires
    private String immunitySource; // Source of immunity (boss, ability, etc.)

    public PlayerStormExposure(UUID playerId) {
        this.playerId = playerId;
        this.firstExposureTime = 0;
        this.lastExposureTime = 0;
        this.lastDamageTime = 0;
        this.accumulatedExposure = 0;
        this.inStorm = false;
        this.onCooldown = false;
        this.immunityExpiry = 0;
        this.immunitySource = null;
    }

    /**
     * Called when player enters a storm.
     */
    public void enterStorm() {
        long now = System.currentTimeMillis();

        if (!inStorm) {
            inStorm = true;

            // Check if we're still in cooldown
            if (!onCooldown || (now - lastExposureTime) > getCooldownMillis()) {
                // Grace period applies - reset accumulated exposure
                accumulatedExposure = 0;
                firstExposureTime = now;
                onCooldown = false;
            }
            // If on cooldown, keep accumulated exposure (no grace period)
        }

        lastExposureTime = now;
    }

    /**
     * Called when player leaves a storm.
     */
    public void leaveStorm() {
        if (inStorm) {
            inStorm = false;
            onCooldown = true;
            lastExposureTime = System.currentTimeMillis();
        }
    }

    /**
     * Updates exposure accumulation. Called every damage tick.
     * @param deltaSeconds Seconds since last update
     */
    public void updateExposure(double deltaSeconds) {
        if (inStorm && !hasImmunity()) {
            accumulatedExposure = Math.min(accumulatedExposure + deltaSeconds, getMaxExposureSeconds());
        }
    }

    /**
     * Gets the damage multiplier based on current exposure.
     * 0.0 during grace period, ramping up to 1.0 at full exposure.
     *
     * @param gracePeriodSeconds Duration of grace period ramp-up
     * @return Damage multiplier (0.0 to 1.0)
     */
    public double getDamageMultiplier(double gracePeriodSeconds) {
        if (hasImmunity()) {
            return 0.0; // No damage during immunity
        }

        if (accumulatedExposure >= gracePeriodSeconds) {
            return 1.0; // Full damage after grace period
        }

        // Linear ramp-up during grace period
        return accumulatedExposure / gracePeriodSeconds;
    }

    /**
     * Grants temporary immunity to storm damage.
     *
     * @param durationSeconds Duration of immunity in seconds
     * @param source Source of immunity (for tracking/display)
     */
    public void grantImmunity(int durationSeconds, String source) {
        long now = System.currentTimeMillis();
        this.immunityExpiry = now + (durationSeconds * 1000L);
        this.immunitySource = source;
    }

    /**
     * Checks if player currently has storm immunity.
     */
    public boolean hasImmunity() {
        return System.currentTimeMillis() < immunityExpiry;
    }

    /**
     * Gets remaining immunity time in seconds.
     */
    public int getRemainingImmunitySeconds() {
        if (!hasImmunity()) {
            return 0;
        }
        long remaining = immunityExpiry - System.currentTimeMillis();
        return (int)(remaining / 1000);
    }

    /**
     * Clears immunity.
     */
    public void clearImmunity() {
        this.immunityExpiry = 0;
        this.immunitySource = null;
    }

    /**
     * Records that damage was applied.
     */
    public void recordDamage() {
        this.lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Checks if cooldown has expired.
     */
    public boolean isCooldownExpired() {
        if (!onCooldown) return true;
        return (System.currentTimeMillis() - lastExposureTime) > getCooldownMillis();
    }

    /**
     * Resets exposure tracking (used when cooldown expires).
     */
    public void resetExposure() {
        this.accumulatedExposure = 0;
        this.firstExposureTime = 0;
        this.onCooldown = false;
    }

    // Configuration values (should come from config)
    private long getCooldownMillis() {
        return 60000; // 60 seconds default
    }

    private double getMaxExposureSeconds() {
        return 300; // Cap at 5 minutes of exposure
    }

    // Getters
    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isInStorm() {
        return inStorm;
    }

    public boolean isOnCooldown() {
        return onCooldown;
    }

    public double getAccumulatedExposure() {
        return accumulatedExposure;
    }

    public String getImmunitySource() {
        return immunitySource;
    }

    public long getLastExposureTime() {
        return lastExposureTime;
    }

    /**
     * Gets a display string for current exposure status.
     */
    public String getStatusDisplay() {
        if (hasImmunity()) {
            return "§aImmune §7(" + immunitySource + ", " + getRemainingImmunitySeconds() + "s)";
        } else if (inStorm) {
            double multiplier = getDamageMultiplier(15.0); // Assuming 15s grace period
            if (multiplier < 1.0) {
                return "§eGrace Period §7(" + String.format("%.0f%%", multiplier * 100) + ")";
            } else {
                return "§cFull Exposure";
            }
        } else if (onCooldown && !isCooldownExpired()) {
            int cooldownRemaining = (int)((getCooldownMillis() - (System.currentTimeMillis() - lastExposureTime)) / 1000);
            return "§7Cooldown (" + cooldownRemaining + "s)";
        } else {
            return "§7Not Exposed";
        }
    }
}