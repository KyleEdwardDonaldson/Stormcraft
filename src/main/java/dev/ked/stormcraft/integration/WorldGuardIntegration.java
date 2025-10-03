package dev.ked.stormcraft.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.ked.stormcraft.StormcraftPlugin;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Optional WorldGuard integration for region-based storm protection.
 */
public class WorldGuardIntegration {
    private final StormcraftPlugin plugin;
    private boolean enabled = false;
    private StateFlag stormcraftProtectFlag;

    public WorldGuardIntegration(StormcraftPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        Plugin worldGuardPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");

        if (worldGuardPlugin == null || !worldGuardPlugin.isEnabled()) {
            plugin.getLogger().info("WorldGuard not found, region protection disabled.");
            return;
        }

        // Enable WorldGuard integration even if flag registration fails
        enabled = true;

        try {
            // Try to register custom flag (optional - only needed for stormcraft-protect feature)
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            StateFlag flag = new StateFlag("stormcraft-protect", false);
            registry.register(flag);
            stormcraftProtectFlag = flag;

            plugin.getLogger().info("WorldGuard integration enabled. Use flag 'stormcraft-protect' to protect regions.");
        } catch (FlagConflictException e) {
            // Flag already registered (likely from previous plugin load)
            stormcraftProtectFlag = (StateFlag) WorldGuard.getInstance().getFlagRegistry().get("stormcraft-protect");
            plugin.getLogger().info("WorldGuard integration enabled (flag already registered).");
        } catch (Exception e) {
            // Flag registration failed, but we can still use WorldGuard for zone detection
            plugin.getLogger().info("WorldGuard integration enabled (zone detection only - flag registration failed: " + e.getMessage() + ")");
            stormcraftProtectFlag = null;
        }
    }

    /**
     * Checks if a location is within a protected region.
     * @param location The location to check
     * @return true if the location is protected from storms
     */
    public boolean isInProtectedRegion(org.bukkit.Location location) {
        if (!enabled || stormcraftProtectFlag == null) {
            return false;
        }

        try {
            Location wgLocation = BukkitAdapter.adapt(location);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            ApplicableRegionSet regions = query.getApplicableRegions(wgLocation);

            // Check if the flag is set to ALLOW (which means protect from storms)
            StateFlag.State state = regions.queryState(null, stormcraftProtectFlag);
            return state == StateFlag.State.ALLOW;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard region: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets a WorldGuard region by name.
     * @param world The world to search in
     * @param regionName The region name (case-insensitive)
     * @return The protected region, or null if not found
     */
    public ProtectedRegion getRegion(World world, String regionName) {
        if (!enabled) {
            return null;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return null;
            }

            return regionManager.getRegion(regionName.toLowerCase());
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting WorldGuard region '" + regionName + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a region exists in the world.
     * @param world The world to check
     * @param regionName The region name
     * @return true if the region exists
     */
    public boolean hasRegion(World world, String regionName) {
        return getRegion(world, regionName) != null;
    }

    /**
     * Checks if a location is within a specific named region.
     * @param location The location to check (Bukkit Location)
     * @param regionName The region name to check for
     * @return true if the location is in the specified region
     */
    public boolean isInRegion(org.bukkit.Location location, String regionName) {
        if (!enabled || location == null || regionName == null) {
            return false;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regionManager == null) {
                return false;
            }

            ProtectedRegion region = regionManager.getRegion(regionName.toLowerCase());
            if (region == null) {
                return false;
            }

            BlockVector3 point = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            return region.contains(point);
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking if location is in region '" + regionName + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets a random location within a WorldGuard region.
     * @param world The world
     * @param regionName The region name
     * @param random Random instance for randomization
     * @return A random location within the region, or null if region not found
     */
    public org.bukkit.Location getRandomLocationInRegion(World world, String regionName, Random random) {
        ProtectedRegion region = getRegion(world, regionName);
        if (region == null) {
            return null;
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // Try up to 20 times to get a random point within the region
        for (int i = 0; i < 20; i++) {
            int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
            int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
            int y = 64; // Use a standard Y level

            BlockVector3 point = BlockVector3.at(x, y, z);

            // Check if point is actually in the region (handles complex shapes)
            if (region.contains(point)) {
                return new org.bukkit.Location(world, x, y, z);
            }
        }

        // Fallback: use region center
        BlockVector3 center = region.getMinimumPoint().add(region.getMaximumPoint()).divide(2);
        return new org.bukkit.Location(world, center.getX(), 64, center.getZ());
    }

}