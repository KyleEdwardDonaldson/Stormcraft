package dev.ked.stormcraft.worldgen;

import dev.ked.stormcraft.StormcraftPlugin;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * Custom world generator for Stormcraft that creates ring-based biome distribution.
 *
 * Rings:
 * - Stormlands (0-2500): Mountain biomes, dramatic peaks (no oceans)
 * - Storm Zone (2500-5000): Open plains and hills
 * - Safe Zone (5000-10000): Dense forests and sheltered areas
 * - Outer Wilds (10000+): Normal vanilla generation
 */
public class StormcraftWorldGenerator extends ChunkGenerator {
    private final StormcraftPlugin plugin;
    private SimplexOctaveGenerator heightNoiseGen;

    public StormcraftWorldGenerator(StormcraftPlugin plugin) {
        this.plugin = plugin;
    }

    private void initNoiseIfNeeded(long seed) {
        if (heightNoiseGen == null) {
            heightNoiseGen = new SimplexOctaveGenerator(new Random(seed), 8);
            heightNoiseGen.setScale(0.005);
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new StormcraftBiomeProvider(plugin, worldInfo);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        initNoiseIfNeeded(worldInfo.getSeed());

        double centerX = getCenterX();
        double centerZ = getCenterZ();
        double stormlandsRadius = getStormlandsRadius();
        double transitionStart = stormlandsRadius - 500; // Start transition 500 blocks before edge

        int worldX = chunkX * 16;
        int worldZ = chunkZ * 16;

        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double blockX = worldX + x;
                double blockZ = worldZ + z;

                double dx = blockX - centerX;
                double dz = blockZ - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz);

                // Calculate height boost based on distance from center
                double heightBoost = 0;

                if (distance < transitionStart) {
                    // Full boost in core Stormlands
                    heightBoost = 10.0;
                } else if (distance < stormlandsRadius) {
                    // Gradual transition from full boost to zero
                    double transitionProgress = (distance - transitionStart) / 500.0;
                    heightBoost = 10.0 * (1.0 - transitionProgress);
                }
                // Outside Stormlands: no boost (vanilla generation)

                // Only generate custom terrain if there's a height boost
                if (heightBoost > 0.5) {
                    double noiseValue = heightNoiseGen.noise(blockX, blockZ, 0.5, 0.5);

                    // Base height above sea level (y=63) to prevent oceans
                    // Range: y=65 to y=85 base terrain (adjusted by distance)
                    int baseHeight = 65 + (int) ((noiseValue + 1.0) * 10) + (int) heightBoost;

                    // Fill from bottom to base height
                    for (int y = minY; y <= baseHeight && y < maxY; y++) {
                        if (y <= 0) {
                            chunkData.setBlock(x, y, z, Material.DEEPSLATE);
                        } else if (y <= baseHeight - 5) {
                            chunkData.setBlock(x, y, z, Material.STONE);
                        } else {
                            chunkData.setBlock(x, y, z, Material.DIRT);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false; // We're handling noise ourselves for Stormlands
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true; // Use vanilla surface generation
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return true; // Use vanilla bedrock
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true; // Use vanilla caves
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true; // Use vanilla decorations
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true; // Use vanilla mob spawning
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true; // Use vanilla structures
    }

    /**
     * Gets the center X coordinate from config.
     */
    private double getCenterX() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getZoneCenterX()
            : 0;
    }

    /**
     * Gets the center Z coordinate from config.
     */
    private double getCenterZ() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getZoneCenterZ()
            : 0;
    }

    /**
     * Gets the Stormlands radius from config.
     */
    private double getStormlandsRadius() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getStormlandsRadius()
            : 2500;
    }

    /**
     * Gets the Storm Zone radius from config.
     */
    private double getStormZoneRadius() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getStormZoneRadius()
            : 5000;
    }

    /**
     * Gets the Safe Zone radius from config.
     */
    private double getSafeZoneRadius() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getSafeZoneRadius()
            : 10000;
    }
}
