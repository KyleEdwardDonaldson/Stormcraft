package dev.ked.stormcraft.model;

public class StormProfile {
    private final StormType type;
    private final int durationSeconds;
    private final int minDurationSeconds;
    private final int maxDurationSeconds;
    private final double damagePerSecond;
    private final double minDamagePerSecond;
    private final double maxDamagePerSecond;
    private final double minMovementSpeed;
    private final double maxMovementSpeed;
    private final boolean blindness;
    private final int slownessAmplifier; // -1 = disabled
    private final double lightningStrikeChance; // 0.0-1.0

    public StormProfile(StormType type, int minDurationSeconds, int maxDurationSeconds,
                       double minDamagePerSecond, double maxDamagePerSecond,
                       double minMovementSpeed, double maxMovementSpeed,
                       boolean blindness, int slownessAmplifier, double lightningStrikeChance) {
        this.type = type;
        this.minDurationSeconds = minDurationSeconds;
        this.maxDurationSeconds = maxDurationSeconds;
        this.durationSeconds = (minDurationSeconds + maxDurationSeconds) / 2; // Used for display only
        this.minDamagePerSecond = minDamagePerSecond;
        this.maxDamagePerSecond = maxDamagePerSecond;
        this.damagePerSecond = (minDamagePerSecond + maxDamagePerSecond) / 2; // Used for display only
        this.minMovementSpeed = minMovementSpeed;
        this.maxMovementSpeed = maxMovementSpeed;
        this.blindness = blindness;
        this.slownessAmplifier = slownessAmplifier;
        this.lightningStrikeChance = lightningStrikeChance;
    }

    public StormType getType() {
        return type;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getMinDurationSeconds() {
        return minDurationSeconds;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public double getDamagePerSecond() {
        return damagePerSecond;
    }

    public double getMinDamagePerSecond() {
        return minDamagePerSecond;
    }

    public double getMaxDamagePerSecond() {
        return maxDamagePerSecond;
    }

    public boolean hasBlindness() {
        return blindness;
    }

    public int getSlownessAmplifier() {
        return slownessAmplifier;
    }

    public double getLightningStrikeChance() {
        return lightningStrikeChance;
    }

    public double getMinMovementSpeed() {
        return minMovementSpeed;
    }

    public double getMaxMovementSpeed() {
        return maxMovementSpeed;
    }
}