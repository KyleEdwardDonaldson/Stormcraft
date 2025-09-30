package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.integration.WorldGuardIntegration;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles block damage during storms in the Stormlands.
 * Buildings slowly degrade, creating a maintenance cost for living there.
 */
public class BlockDamageTask extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final ZoneManager zoneManager;
    private final WorldGuardIntegration worldGuardIntegration;
    private final Random random = new Random();

    private TravelingStorm activeStorm;
    private List<TravelingStorm> activeStorms = new ArrayList<>();

    // Block degradation chains (block -> degraded version)
    private static final Map<Material, Material> DEGRADATION_MAP = new HashMap<>();

    static {
        // Wood degradation
        DEGRADATION_MAP.put(Material.OAK_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.SPRUCE_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.BIRCH_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.JUNGLE_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.ACACIA_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.DARK_OAK_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.MANGROVE_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.CHERRY_PLANKS, Material.AIR);
        DEGRADATION_MAP.put(Material.BAMBOO_PLANKS, Material.AIR);

        // Stone degradation chain: stone -> cobblestone -> gravel -> air
        DEGRADATION_MAP.put(Material.STONE, Material.COBBLESTONE);
        DEGRADATION_MAP.put(Material.COBBLESTONE, Material.GRAVEL);
        DEGRADATION_MAP.put(Material.GRAVEL, Material.AIR);
        DEGRADATION_MAP.put(Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS);
        DEGRADATION_MAP.put(Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE);

        // Brick degradation
        DEGRADATION_MAP.put(Material.BRICKS, Material.COBBLESTONE);
        DEGRADATION_MAP.put(Material.NETHER_BRICKS, Material.NETHERRACK);

        // Wool and fabric
        DEGRADATION_MAP.put(Material.WHITE_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.ORANGE_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.MAGENTA_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.LIGHT_BLUE_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.YELLOW_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.LIME_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.PINK_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.GRAY_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.LIGHT_GRAY_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.CYAN_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.PURPLE_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.BLUE_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.BROWN_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.GREEN_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.RED_WOOL, Material.AIR);
        DEGRADATION_MAP.put(Material.BLACK_WOOL, Material.AIR);

        // Glass degradation
        DEGRADATION_MAP.put(Material.GLASS, Material.AIR);
        DEGRADATION_MAP.put(Material.GLASS_PANE, Material.AIR);

        // Dirt/Grass
        DEGRADATION_MAP.put(Material.GRASS_BLOCK, Material.DIRT);
        DEGRADATION_MAP.put(Material.DIRT, Material.COARSE_DIRT);
    }

    // Block damage chance multipliers by material category
    private static final Map<Material, Double> DAMAGE_CHANCE_MULTIPLIERS = new HashMap<>();

    static {
        // Weak blocks - high damage chance
        for (Material mat : DEGRADATION_MAP.keySet()) {
            if (mat.name().contains("WOOL") || mat.name().contains("PLANKS")) {
                DAMAGE_CHANCE_MULTIPLIERS.put(mat, 3.0); // 3x base chance
            } else if (mat.name().contains("GLASS")) {
                DAMAGE_CHANCE_MULTIPLIERS.put(mat, 2.5);
            } else if (mat.name().contains("STONE")) {
                DAMAGE_CHANCE_MULTIPLIERS.put(mat, 0.5); // Half base chance
            } else {
                DAMAGE_CHANCE_MULTIPLIERS.put(mat, 1.0); // Normal chance
            }
        }
    }

    // Protected block types that never degrade
    private static final Set<Material> PROTECTED_BLOCKS = Set.of(
        Material.BEDROCK,
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.NETHERITE_BLOCK,
        Material.ANCIENT_DEBRIS,
        Material.CHEST,
        Material.BARREL,
        Material.SHULKER_BOX,
        Material.ENDER_CHEST,
        Material.SPAWNER,
        Material.BEACON,
        Material.END_PORTAL_FRAME,
        Material.COMMAND_BLOCK
    );

    public BlockDamageTask(StormcraftPlugin plugin, ConfigManager config,
                          ZoneManager zoneManager, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
        this.worldGuardIntegration = worldGuardIntegration;
    }

    public void setActiveStorm(TravelingStorm storm) {
        this.activeStorm = storm;
    }

    public void setActiveStorms(List<TravelingStorm> storms) {
        this.activeStorms = storms;
    }

    @Override
    public void run() {
        // Multi-storm system
        if (!activeStorms.isEmpty()) {
            runMultiStormBlockDamage();
            return;
        }

        // Single storm system (legacy)
        if (activeStorm == null || !config.isBlockDamageEnabled()) {
            return;
        }

        World world = activeStorm.getCurrentLocation().getWorld();
        if (world == null) {
            return;
        }

        Location stormCenter = activeStorm.getCurrentLocation();
        double damageRadius = config.getStormDamageRadius();

        // Check if storm is in Stormlands (only damage blocks there)
        if (!zoneManager.isInStormlands(stormCenter)) {
            return;
        }

        // Get chunks within storm radius
        Set<Chunk> affectedChunks = getChunksInRadius(stormCenter, damageRadius);

        int blocksChecked = 0;
        int blocksDamaged = 0;
        int maxChecksPerTick = config.getBlockDamageMaxChecksPerTick();

        // Randomly sample blocks from affected chunks
        for (Chunk chunk : affectedChunks) {
            if (blocksChecked >= maxChecksPerTick) {
                break;
            }

            // Sample a few random blocks from this chunk
            for (int i = 0; i < 5; i++) {
                if (blocksChecked >= maxChecksPerTick) {
                    break;
                }

                Block block = getRandomBlockInChunk(chunk);
                blocksChecked++;

                if (canDamageBlock(block, stormCenter, damageRadius)) {
                    if (attemptBlockDamage(block)) {
                        blocksDamaged++;
                    }
                }
            }
        }

        if (config.isLogScheduling() && blocksDamaged > 0) {
            plugin.getLogger().info("Storm damaged " + blocksDamaged + " blocks (checked " + blocksChecked + ")");
        }
    }

    /**
     * Handles block damage for multiple simultaneous storms.
     */
    private void runMultiStormBlockDamage() {
        if (!config.isBlockDamageEnabled()) {
            return;
        }

        int blocksChecked = 0;
        int blocksDamaged = 0;
        int maxChecksPerTick = config.getBlockDamageMaxChecksPerTick();
        double damageRadius = config.getStormDamageRadius();

        // Collect all affected chunks from all storms
        Set<Chunk> affectedChunks = new HashSet<>();
        for (TravelingStorm storm : activeStorms) {
            Location stormCenter = storm.getCurrentLocation();

            // Only damage blocks in Stormlands
            if (zoneManager.isInStormlands(stormCenter)) {
                affectedChunks.addAll(getChunksInRadius(stormCenter, damageRadius));
            }
        }

        // Randomly sample blocks from affected chunks
        for (Chunk chunk : affectedChunks) {
            if (blocksChecked >= maxChecksPerTick) {
                break;
            }

            // Sample a few random blocks from this chunk
            for (int i = 0; i < 5; i++) {
                if (blocksChecked >= maxChecksPerTick) {
                    break;
                }

                Block block = getRandomBlockInChunk(chunk);
                blocksChecked++;

                // Check if block is in any storm's radius
                TravelingStorm closestStorm = findClosestStorm(block.getLocation());
                if (closestStorm != null && canDamageBlock(block, closestStorm.getCurrentLocation(), damageRadius)) {
                    if (attemptBlockDamage(block)) {
                        blocksDamaged++;
                    }
                }
            }
        }

        if (config.isLogScheduling() && blocksDamaged > 0) {
            plugin.getLogger().info("Storms damaged " + blocksDamaged + " blocks (checked " + blocksChecked + ")");
        }
    }

    /**
     * Finds the closest storm to a location that is within damage radius.
     */
    private TravelingStorm findClosestStorm(Location location) {
        TravelingStorm closest = null;
        double closestDistance = Double.MAX_VALUE;
        double damageRadius = config.getStormDamageRadius();

        for (TravelingStorm storm : activeStorms) {
            Location stormLoc = storm.getCurrentLocation();
            if (stormLoc.getWorld().equals(location.getWorld())) {
                double distance = location.distance(stormLoc);
                if (distance <= damageRadius && distance < closestDistance) {
                    closestDistance = distance;
                    closest = storm;
                }
            }
        }

        return closest;
    }

    /**
     * Gets all chunks within a radius of a location.
     */
    private Set<Chunk> getChunksInRadius(Location center, double radius) {
        Set<Chunk> chunks = new HashSet<>();
        World world = center.getWorld();

        int chunkRadius = (int) Math.ceil(radius / 16.0);
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                chunks.add(world.getChunkAt(centerChunkX + x, centerChunkZ + z));
            }
        }

        return chunks;
    }

    /**
     * Gets a random block from a chunk at a random Y level.
     */
    private Block getRandomBlockInChunk(Chunk chunk) {
        int x = (chunk.getX() << 4) + random.nextInt(16);
        int z = (chunk.getZ() << 4) + random.nextInt(16);
        int y = random.nextInt(chunk.getWorld().getMaxHeight() - chunk.getWorld().getMinHeight())
              + chunk.getWorld().getMinHeight();

        return chunk.getWorld().getBlockAt(x, y, z);
    }

    /**
     * Checks if a block can be damaged.
     */
    private boolean canDamageBlock(Block block, Location stormCenter, double damageRadius) {
        // Check if block is in storm radius
        if (block.getLocation().distance(stormCenter) > damageRadius) {
            return false;
        }

        Material type = block.getType();

        // Skip air
        if (type.isAir()) {
            return false;
        }

        // Skip protected blocks
        if (PROTECTED_BLOCKS.contains(type)) {
            return false;
        }

        // Skip blocks that aren't in degradation map
        if (!DEGRADATION_MAP.containsKey(type)) {
            return false;
        }

        // Check WorldGuard protection
        if (worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
            if (worldGuardIntegration.isInProtectedRegion(block.getLocation())) {
                return false;
            }
        }

        // Check if block has sky access (only damage exposed blocks)
        return block.getLightFromSky() > 0;
    }

    /**
     * Attempts to damage a block based on configured chance.
     */
    private boolean attemptBlockDamage(Block block) {
        Material type = block.getType();

        // Get base damage chance and multiplier
        double baseChance = config.getBlockDamageChance();
        double multiplier = DAMAGE_CHANCE_MULTIPLIERS.getOrDefault(type, 1.0);
        double finalChance = baseChance * multiplier;

        // Roll for damage
        if (random.nextDouble() < finalChance) {
            Material degradedMaterial = DEGRADATION_MAP.get(type);
            if (degradedMaterial != null) {
                block.setType(degradedMaterial);
                return true;
            }
        }

        return false;
    }
}