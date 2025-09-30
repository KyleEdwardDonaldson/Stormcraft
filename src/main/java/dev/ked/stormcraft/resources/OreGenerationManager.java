package dev.ked.stormcraft.resources;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages passive ore regeneration in the Stormlands.
 * Slowly spawns valuable ores in stone blocks within the Stormlands zone.
 */
public class OreGenerationManager extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final ZoneManager zoneManager;
    private final Random random = new Random();

    // Ore generation weights for Stormlands
    private static final Map<Material, Integer> STORMLANDS_ORES = new HashMap<>();
    private static final Map<Material, Integer> STORM_ZONE_ORES = new HashMap<>();

    static {
        // Stormlands ore weights (better ores, higher chances)
        STORMLANDS_ORES.put(Material.COAL_ORE, 100);
        STORMLANDS_ORES.put(Material.DEEPSLATE_COAL_ORE, 80);
        STORMLANDS_ORES.put(Material.IRON_ORE, 80);
        STORMLANDS_ORES.put(Material.DEEPSLATE_IRON_ORE, 70);
        STORMLANDS_ORES.put(Material.COPPER_ORE, 70);
        STORMLANDS_ORES.put(Material.DEEPSLATE_COPPER_ORE, 60);
        STORMLANDS_ORES.put(Material.GOLD_ORE, 40);
        STORMLANDS_ORES.put(Material.DEEPSLATE_GOLD_ORE, 35);
        STORMLANDS_ORES.put(Material.REDSTONE_ORE, 50);
        STORMLANDS_ORES.put(Material.DEEPSLATE_REDSTONE_ORE, 45);
        STORMLANDS_ORES.put(Material.LAPIS_ORE, 35);
        STORMLANDS_ORES.put(Material.DEEPSLATE_LAPIS_ORE, 30);
        STORMLANDS_ORES.put(Material.DIAMOND_ORE, 15);
        STORMLANDS_ORES.put(Material.DEEPSLATE_DIAMOND_ORE, 12);
        STORMLANDS_ORES.put(Material.EMERALD_ORE, 10);
        STORMLANDS_ORES.put(Material.DEEPSLATE_EMERALD_ORE, 8);
        STORMLANDS_ORES.put(Material.ANCIENT_DEBRIS, 3); // Only in Stormlands!

        // Storm Zone ore weights (moderate)
        STORM_ZONE_ORES.put(Material.COAL_ORE, 100);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_COAL_ORE, 80);
        STORM_ZONE_ORES.put(Material.IRON_ORE, 70);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_IRON_ORE, 60);
        STORM_ZONE_ORES.put(Material.COPPER_ORE, 65);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_COPPER_ORE, 55);
        STORM_ZONE_ORES.put(Material.GOLD_ORE, 25);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_GOLD_ORE, 20);
        STORM_ZONE_ORES.put(Material.REDSTONE_ORE, 40);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_REDSTONE_ORE, 35);
        STORM_ZONE_ORES.put(Material.LAPIS_ORE, 30);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_LAPIS_ORE, 25);
        STORM_ZONE_ORES.put(Material.DIAMOND_ORE, 8);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_DIAMOND_ORE, 6);
        STORM_ZONE_ORES.put(Material.EMERALD_ORE, 5);
        STORM_ZONE_ORES.put(Material.DEEPSLATE_EMERALD_ORE, 4);
    }

    // Valid host blocks that can be replaced with ore
    private static final Set<Material> HOST_BLOCKS = Set.of(
        Material.STONE,
        Material.DEEPSLATE,
        Material.GRANITE,
        Material.DIORITE,
        Material.ANDESITE,
        Material.TUFF,
        Material.NETHERRACK
    );

    public OreGenerationManager(StormcraftPlugin plugin, ConfigManager config, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
    }

    @Override
    public void run() {
        if (!config.isOreGenerationEnabled() || !zoneManager.isEnabled()) {
            return;
        }

        // Get enabled worlds
        for (String worldName : config.getEnabledWorlds()) {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                continue;
            }

            // Sample random chunks in Stormlands and Storm Zone
            int chunksToCheck = config.getOreGenerationChunksPerTick();
            for (int i = 0; i < chunksToCheck; i++) {
                Chunk chunk = getRandomChunkInZone(world);
                if (chunk != null) {
                    attemptOreGeneration(chunk);
                }
            }
        }
    }

    /**
     * Gets a random chunk within the Stormlands or Storm Zone.
     * Only returns chunks that are already loaded to avoid blocking the main thread.
     */
    private Chunk getRandomChunkInZone(World world) {
        // Get all loaded chunks in the world
        Chunk[] loadedChunks = world.getLoadedChunks();
        if (loadedChunks.length == 0) {
            return null;
        }

        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();
        double maxRadius = zoneManager.getStormZoneRadius();

        // Try to find a loaded chunk within the zone (max 10 attempts)
        for (int attempt = 0; attempt < 10; attempt++) {
            Chunk chunk = loadedChunks[random.nextInt(loadedChunks.length)];

            // Check if chunk is within the Storm Zone radius
            double chunkCenterX = (chunk.getX() << 4) + 8;
            double chunkCenterZ = (chunk.getZ() << 4) + 8;
            double distance = Math.sqrt(
                Math.pow(chunkCenterX - centerX, 2) +
                Math.pow(chunkCenterZ - centerZ, 2)
            );

            if (distance <= maxRadius) {
                return chunk;
            }
        }

        // If no chunk in zone found after 10 attempts, return null
        return null;
    }

    /**
     * Attempts to generate ore in a chunk based on zone.
     */
    private void attemptOreGeneration(Chunk chunk) {
        World world = chunk.getWorld();

        // Sample a few random blocks in this chunk
        int attemptsPerChunk = config.getOreGenerationAttemptsPerChunk();

        for (int i = 0; i < attemptsPerChunk; i++) {
            // Random block in chunk
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            int y = random.nextInt(world.getMaxHeight() - world.getMinHeight()) + world.getMinHeight();

            Block block = world.getBlockAt(x, y, z);

            // Check if this location is in a zone
            ZoneManager.ZoneType zone = zoneManager.getZoneAt(block.getLocation());

            // Only generate in Stormlands or Storm Zone
            if (zone != ZoneManager.ZoneType.STORMLANDS && zone != ZoneManager.ZoneType.STORM_ZONE) {
                continue;
            }

            // Check if block can host ore
            if (!HOST_BLOCKS.contains(block.getType())) {
                continue;
            }

            // Roll for ore generation
            double baseChance = config.getOreGenerationChance();
            double zoneMultiplier = zone == ZoneManager.ZoneType.STORMLANDS ? 2.0 : 1.0;
            double finalChance = baseChance * zoneMultiplier;

            if (random.nextDouble() < finalChance) {
                Material ore = selectRandomOre(zone, y);
                if (ore != null) {
                    block.setType(ore);

                    if (config.isLogScheduling()) {
                        plugin.getLogger().info("Generated " + ore.name() + " at " +
                                              x + ", " + y + ", " + z + " in " + zone.getDisplayName());
                    }
                }
            }
        }
    }

    /**
     * Selects a random ore based on zone and Y level.
     */
    private Material selectRandomOre(ZoneManager.ZoneType zone, int y) {
        Map<Material, Integer> oreTable = (zone == ZoneManager.ZoneType.STORMLANDS)
                                          ? STORMLANDS_ORES
                                          : STORM_ZONE_ORES;

        // Filter ores by Y level (deepslate variants below Y=0)
        Map<Material, Integer> validOres = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : oreTable.entrySet()) {
            Material ore = entry.getKey();
            boolean isDeepslate = ore.name().contains("DEEPSLATE");

            // Deepslate ores only below Y=0
            if (isDeepslate && y < 0) {
                validOres.put(ore, entry.getValue());
            }
            // Regular ores only above Y=0
            else if (!isDeepslate && y >= 0) {
                validOres.put(ore, entry.getValue());
            }
        }

        if (validOres.isEmpty()) {
            return null;
        }

        return selectWeightedRandom(validOres);
    }

    /**
     * Selects a random material from a weighted map.
     */
    private Material selectWeightedRandom(Map<Material, Integer> weightedMap) {
        int totalWeight = weightedMap.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(totalWeight);

        int cumulative = 0;
        for (Map.Entry<Material, Integer> entry : weightedMap.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }

        return null;
    }
}