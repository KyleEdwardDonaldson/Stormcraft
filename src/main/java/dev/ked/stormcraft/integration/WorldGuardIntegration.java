package dev.ked.stormcraft.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.ked.stormcraft.StormcraftPlugin;
import org.bukkit.plugin.Plugin;

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

        try {
            // Register custom flag
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            StateFlag flag = new StateFlag("stormcraft-protect", false);
            registry.register(flag);
            stormcraftProtectFlag = flag;

            enabled = true;
            plugin.getLogger().info("WorldGuard integration enabled. Use flag 'stormcraft-protect' to protect regions.");
        } catch (FlagConflictException e) {
            // Flag already registered (likely from previous plugin load)
            stormcraftProtectFlag = (StateFlag) WorldGuard.getInstance().getFlagRegistry().get("stormcraft-protect");
            enabled = true;
            plugin.getLogger().info("WorldGuard integration enabled (flag already registered).");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize WorldGuard integration: " + e.getMessage());
            enabled = false;
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
}