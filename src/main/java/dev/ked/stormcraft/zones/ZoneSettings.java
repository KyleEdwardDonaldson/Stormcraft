package dev.ked.stormcraft.zones;

import dev.ked.stormcraft.model.StormType;

import java.util.Map;

/**
 * Settings for a specific zone including storm frequency, damage, resources, etc.
 */
public class ZoneSettings {
    private final double stormFrequencyMultiplier;
    private final Map<StormType, Double> stormTypeWeights;
    private final double resourceMultiplier;
    private final double essenceMultiplier;
    private final boolean blockDamageEnabled;
    private final double blockDamageChance;
    private final boolean stormDropsEnabled;
    private final double stormDropChance;

    public ZoneSettings(double stormFrequencyMultiplier,
                       Map<StormType, Double> stormTypeWeights,
                       double resourceMultiplier,
                       double essenceMultiplier,
                       boolean blockDamageEnabled,
                       double blockDamageChance,
                       boolean stormDropsEnabled,
                       double stormDropChance) {
        this.stormFrequencyMultiplier = stormFrequencyMultiplier;
        this.stormTypeWeights = stormTypeWeights;
        this.resourceMultiplier = resourceMultiplier;
        this.essenceMultiplier = essenceMultiplier;
        this.blockDamageEnabled = blockDamageEnabled;
        this.blockDamageChance = blockDamageChance;
        this.stormDropsEnabled = stormDropsEnabled;
        this.stormDropChance = stormDropChance;
    }

    public double getStormFrequencyMultiplier() {
        return stormFrequencyMultiplier;
    }

    public Map<StormType, Double> getStormTypeWeights() {
        return stormTypeWeights;
    }

    public double getResourceMultiplier() {
        return resourceMultiplier;
    }

    public double getEssenceMultiplier() {
        return essenceMultiplier;
    }

    public boolean isBlockDamageEnabled() {
        return blockDamageEnabled;
    }

    public double getBlockDamageChance() {
        return blockDamageChance;
    }

    public boolean isStormDropsEnabled() {
        return stormDropsEnabled;
    }

    public double getStormDropChance() {
        return stormDropChance;
    }
}