package dev.ked.stormcraft.worldgen;

import dev.ked.stormcraft.StormcraftPlugin;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.List;
import java.util.Random;

/**
 * Custom biome provider that distributes biomes based on distance from world center.
 *
 * Ring system:
 * - Stormlands (0-2500): Mountain biomes
 * - Storm Zone (2500-5000): Plains and open biomes
 * - Safe Zone (5000-10000): Forest biomes
 * - Outer Wilds (10000+): All vanilla biomes
 */
public class StormcraftBiomeProvider extends BiomeProvider {
    private final StormcraftPlugin plugin;
    private final SimplexOctaveGenerator noiseGen;
    private final long worldSeed;

    // Transition zone width in blocks
    private static final double TRANSITION_WIDTH = 300.0;

    // Biome arrays for each ring
    private static final Biome[] STORMLANDS_BIOMES = {
        Biome.JAGGED_PEAKS,
        Biome.FROZEN_PEAKS,
        Biome.STONY_PEAKS,
        Biome.WINDSWEPT_GRAVELLY_HILLS,
        Biome.WINDSWEPT_HILLS,
        Biome.WINDSWEPT_FOREST
    };

    private static final Biome[] STORM_ZONE_BIOMES = {
        Biome.PLAINS,
        Biome.SUNFLOWER_PLAINS,
        Biome.SAVANNA,
        Biome.WINDSWEPT_SAVANNA,
        Biome.MEADOW,
        Biome.SPARSE_JUNGLE,
        Biome.BIRCH_FOREST
    };

    private static final Biome[] SAFE_ZONE_BIOMES = {
        Biome.DARK_FOREST,
        Biome.JUNGLE,
        Biome.BAMBOO_JUNGLE,
        Biome.OLD_GROWTH_SPRUCE_TAIGA,
        Biome.TAIGA,
        Biome.MANGROVE_SWAMP,
        Biome.SWAMP,
        Biome.FOREST
    };

    // Vanilla biomes for outer wilds (common ones)
    private static final Biome[] VANILLA_BIOMES = {
        Biome.PLAINS,
        Biome.FOREST,
        Biome.BIRCH_FOREST,
        Biome.DARK_FOREST,
        Biome.TAIGA,
        Biome.WINDSWEPT_HILLS,
        Biome.SWAMP,
        Biome.DESERT,
        Biome.SAVANNA,
        Biome.JUNGLE
    };

    public StormcraftBiomeProvider(StormcraftPlugin plugin, WorldInfo worldInfo) {
        this.plugin = plugin;
        this.worldSeed = worldInfo.getSeed();
        this.noiseGen = new SimplexOctaveGenerator(new Random(worldSeed), 4);
        this.noiseGen.setScale(0.001); // Large-scale noise for natural variation
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        double centerX = getCenterX();
        double centerZ = getCenterZ();

        // Calculate distance from center (2D, ignore Y)
        double dx = x - centerX;
        double dz = z - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Get radii from config
        double stormlandsRadius = getStormlandsRadius();
        double stormZoneRadius = getStormZoneRadius();
        double safeZoneRadius = getSafeZoneRadius();

        // Create deterministic random based on world seed and position
        Random random = new Random(worldSeed + ((long)x << 32) + z);

        // Get noise value for variation (-1 to 1)
        double noise = noiseGen.noise(x, z, 0.5, 0.5);

        // Check if we're in a transition zone and blend biomes
        if (isInTransition(distance, stormlandsRadius)) {
            // Transition between Stormlands and Storm Zone
            return blendBiomes(
                selectStormlandsBiome(noise, random),
                selectStormZoneBiome(noise, random),
                getTransitionBlend(distance, stormlandsRadius),
                random
            );
        } else if (isInTransition(distance, stormZoneRadius)) {
            // Transition between Storm Zone and Safe Zone
            return blendBiomes(
                selectStormZoneBiome(noise, random),
                selectSafeZoneBiome(noise, random),
                getTransitionBlend(distance, stormZoneRadius),
                random
            );
        } else if (isInTransition(distance, safeZoneRadius)) {
            // Transition between Safe Zone and Vanilla
            return blendBiomes(
                selectSafeZoneBiome(noise, random),
                selectVanillaBiome(noise, random),
                getTransitionBlend(distance, safeZoneRadius),
                random
            );
        }

        // Core zone selection (not in transition)
        if (distance < stormlandsRadius) {
            return selectStormlandsBiome(noise, random);
        } else if (distance < stormZoneRadius) {
            return selectStormZoneBiome(noise, random);
        } else if (distance < safeZoneRadius) {
            return selectSafeZoneBiome(noise, random);
        } else {
            return selectVanillaBiome(noise, random);
        }
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all possible biomes this provider can generate
        return List.of(
            // Stormlands
            Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.STONY_PEAKS,
            Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_FOREST,
            // Storm Zone
            Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.SAVANNA, Biome.WINDSWEPT_SAVANNA,
            Biome.MEADOW, Biome.SPARSE_JUNGLE, Biome.BIRCH_FOREST,
            // Safe Zone
            Biome.DARK_FOREST, Biome.JUNGLE, Biome.BAMBOO_JUNGLE, Biome.OLD_GROWTH_SPRUCE_TAIGA,
            Biome.TAIGA, Biome.MANGROVE_SWAMP, Biome.SWAMP, Biome.FOREST,
            // Vanilla
            Biome.DESERT
        );
    }

    /**
     * Selects a biome for the Stormlands using noise-based weighted random.
     */
    private Biome selectStormlandsBiome(double noise, Random random) {
        // Use noise to weight selection toward certain biomes
        if (noise > 0.4) {
            // High noise = more jagged/frozen peaks
            return random.nextDouble() < 0.7 ? Biome.JAGGED_PEAKS : Biome.FROZEN_PEAKS;
        } else if (noise > 0.0) {
            // Medium noise = stony peaks and windswept hills
            return random.nextDouble() < 0.6 ? Biome.STONY_PEAKS : Biome.WINDSWEPT_GRAVELLY_HILLS;
        } else {
            // Low noise = windswept variants
            return random.nextDouble() < 0.5 ? Biome.WINDSWEPT_HILLS : Biome.WINDSWEPT_FOREST;
        }
    }

    /**
     * Selects a biome for the Storm Zone using noise-based weighted random.
     */
    private Biome selectStormZoneBiome(double noise, Random random) {
        if (noise > 0.3) {
            // Plains variants
            return random.nextDouble() < 0.7 ? Biome.PLAINS : Biome.SUNFLOWER_PLAINS;
        } else if (noise > -0.2) {
            // Savanna variants
            return random.nextDouble() < 0.6 ? Biome.SAVANNA : Biome.WINDSWEPT_SAVANNA;
        } else if (noise > -0.5) {
            return Biome.MEADOW;
        } else {
            // Sparse vegetation
            return random.nextDouble() < 0.6 ? Biome.SPARSE_JUNGLE : Biome.BIRCH_FOREST;
        }
    }

    /**
     * Selects a biome for the Safe Zone using noise-based weighted random.
     */
    private Biome selectSafeZoneBiome(double noise, Random random) {
        if (noise > 0.4) {
            // Dark/dense forests
            return random.nextDouble() < 0.6 ? Biome.DARK_FOREST : Biome.JUNGLE;
        } else if (noise > 0.0) {
            // Jungle variants
            return random.nextDouble() < 0.5 ? Biome.JUNGLE : Biome.BAMBOO_JUNGLE;
        } else if (noise > -0.4) {
            // Taiga variants
            return random.nextDouble() < 0.6 ? Biome.OLD_GROWTH_SPRUCE_TAIGA : Biome.TAIGA;
        } else {
            // Swamp variants
            return random.nextDouble() < 0.5 ? Biome.SWAMP : Biome.MANGROVE_SWAMP;
        }
    }

    /**
     * Selects a vanilla biome for the Outer Wilds.
     */
    private Biome selectVanillaBiome(double noise, Random random) {
        // Use noise to create variety
        int index = (int) ((noise + 1.0) * 0.5 * VANILLA_BIOMES.length);
        index = Math.max(0, Math.min(index, VANILLA_BIOMES.length - 1));
        return VANILLA_BIOMES[index];
    }

    /**
     * Checks if a distance is within the transition zone of a boundary.
     */
    private boolean isInTransition(double distance, double boundary) {
        return Math.abs(distance - boundary) < TRANSITION_WIDTH;
    }

    /**
     * Calculates blend factor (0.0 = inner biome, 1.0 = outer biome).
     */
    private double getTransitionBlend(double distance, double boundary) {
        double offset = distance - boundary;
        return (offset + TRANSITION_WIDTH) / (TRANSITION_WIDTH * 2.0);
    }

    /**
     * Blends between two biomes based on blend factor.
     */
    private Biome blendBiomes(Biome inner, Biome outer, double blend, Random random) {
        // Use probabilistic blending - smoother than hard switch
        return random.nextDouble() < blend ? outer : inner;
    }

    // Config getters with fallbacks
    private double getCenterX() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getZoneCenterX()
            : 0;
    }

    private double getCenterZ() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getZoneCenterZ()
            : 0;
    }

    private double getStormlandsRadius() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getStormlandsRadius()
            : 2500;
    }

    private double getStormZoneRadius() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getStormZoneRadius()
            : 5000;
    }

    private double getSafeZoneRadius() {
        return plugin.getConfigManager() != null && plugin.getConfigManager().isZoneSystemEnabled()
            ? plugin.getConfigManager().getSafeZoneRadius()
            : 10000;
    }
}
