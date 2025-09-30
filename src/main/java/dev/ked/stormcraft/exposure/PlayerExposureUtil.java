package dev.ked.stormcraft.exposure;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Utility class for determining if a player is exposed to a storm.
 */
public class PlayerExposureUtil {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;

    // Materials that count as transparent/cover depending on config
    private static final Set<Material> LEAF_MATERIALS = Set.of(
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
        Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES
    );

    private static final Set<Material> GLASS_MATERIALS = Set.of(
        Material.GLASS, Material.GLASS_PANE,
        Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
        Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
        Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.RED_STAINED_GLASS,
        Material.BLACK_STAINED_GLASS,
        Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
        Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
        Material.BLACK_STAINED_GLASS_PANE, Material.TINTED_GLASS
    );

    public PlayerExposureUtil(StormcraftPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Checks if a player is exposed to the storm.
     * @param player The player to check
     * @return true if the player is exposed, false if sheltered
     */
    public boolean isPlayerExposed(Player player) {
        // Check if world is enabled
        if (!config.getEnabledWorlds().contains(player.getWorld().getName())) {
            return false;
        }

        // Check game mode
        if (config.getIgnoreGameModes().contains(player.getGameMode())) {
            return false;
        }

        // Check sky access
        return hasSkyAccess(player);
    }

    /**
     * Determines if a player has direct access to the sky (is not sufficiently covered).
     * Only requires overhead block cover for protection.
     * @param player The player to check
     * @return true if player can see sky, false if covered
     */
    private boolean hasSkyAccess(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        if (world == null) {
            return false;
        }

        // Quick check: compare player Y to highest block Y
        int highestBlockY = world.getHighestBlockYAt(loc);
        int playerY = loc.getBlockY();

        // If player is below the highest block by at least minDepth, check if they have overhead protection
        int minDepth = config.getIgnoreIfUnderBlocksMinDepth();
        if (highestBlockY - playerY >= minDepth) {
            // Only need overhead blocks for protection
            return !hasBlocksOverhead(player, minDepth);
        }

        // Player is at or near surface level
        return true;
    }

    /**
     * Checks if there are solid blocks overhead that provide cover.
     * @param player The player to check
     * @param minDepth Minimum number of solid blocks required for cover
     * @return true if sufficiently covered, false if exposed
     */
    private boolean hasBlocksOverhead(Player player, int minDepth) {
        Location loc = player.getLocation(); // Use feet location, not eye
        World world = loc.getWorld();

        if (world == null) {
            return false;
        }

        int solidBlockCount = 0;
        int maxHeight = world.getMaxHeight();

        // Raycast upward from player's feet level
        int startY = loc.getBlockY() + 1;
        for (int y = startY; y < maxHeight; y++) {
            Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            Material material = block.getType();

            if (material.isAir()) {
                continue;
            }

            // Check if this material counts as cover
            if (isCoverMaterial(material)) {
                solidBlockCount++;
                if (config.isLogExposureSamples()) {
                    plugin.getLogger().info("Found cover block: " + material.name() + " at Y=" + y + ", count=" + solidBlockCount);
                }
                if (solidBlockCount >= minDepth) {
                    return true;
                }
            } else if (config.isLogExposureSamples()) {
                plugin.getLogger().info("Skipping non-cover block: " + material.name() + " at Y=" + y);
            }
        }

        return solidBlockCount >= minDepth;
    }

    /**
     * Checks if there is at least one solid block adjacent to the player (horizontal neighbors).
     * This provides "wind protection" in addition to overhead cover.
     * @param player The player to check
     * @return true if at least one adjacent wall exists, false otherwise
     */
    private boolean hasAdjacentWall(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        if (world == null) {
            return false;
        }

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Check all 8 horizontal neighbors at player's feet level
        int[][] neighbors = {
            {-1, 0}, {1, 0},   // West, East
            {0, -1}, {0, 1},   // North, South
            {-1, -1}, {-1, 1}, // NW, SW
            {1, -1}, {1, 1}    // NE, SE
        };

        for (int[] offset : neighbors) {
            Block block = world.getBlockAt(x + offset[0], y, z + offset[1]);
            Material material = block.getType();

            // Check if this is a solid wall block
            if (isCoverMaterial(material)) {
                return true; // Found at least one wall
            }
        }

        return false; // No adjacent walls found
    }

    /**
     * Determines if a material counts as cover based on config settings.
     * @param material The material to check
     * @return true if this material provides cover
     */
    private boolean isCoverMaterial(Material material) {
        // Air never counts as cover
        if (material.isAir()) {
            return false;
        }

        // Check leaves
        if (LEAF_MATERIALS.contains(material)) {
            return config.isTreatLeavesAsCover();
        }

        // Check glass
        if (GLASS_MATERIALS.contains(material)) {
            return config.isTreatGlassAsCover();
        }

        // All other solid blocks count as cover
        return material.isSolid();
    }

    /**
     * Gets a display name for logging/debugging.
     * @param player The player
     * @return A display string for the player
     */
    public String getPlayerDebugString(Player player) {
        Location loc = player.getLocation();
        return player.getName() + " @ " +
               String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }
}