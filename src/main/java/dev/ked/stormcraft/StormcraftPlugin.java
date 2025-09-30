package dev.ked.stormcraft;

import dev.ked.stormcraft.command.StormcraftCommand;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.config.PersistenceManager;
import dev.ked.stormcraft.exposure.PlayerExposureUtil;
import dev.ked.stormcraft.integration.DynmapIntegration;
import dev.ked.stormcraft.integration.PlaceholderAPIIntegration;
import dev.ked.stormcraft.integration.VaultIntegration;
import dev.ked.stormcraft.integration.WorldGuardIntegration;
import dev.ked.stormcraft.listener.WeatherControlListener;
import dev.ked.stormcraft.schedule.StormManager;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main plugin class for Stormcraft.
 * Manages plugin lifecycle, initialization, and shutdown.
 */
public class StormcraftPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private PersistenceManager persistenceManager;
    private WorldGuardIntegration worldGuardIntegration;
    private VaultIntegration vaultIntegration;
    private ZoneManager zoneManager;
    private DynmapIntegration dynmapIntegration;
    private PlayerExposureUtil exposureUtil;
    private StormManager stormManager;
    private PlaceholderAPIIntegration placeholderAPIIntegration;

    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        getLogger().info("Starting Stormcraft...");

        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // Initialize persistence
        persistenceManager = new PersistenceManager(this, configManager);

        // Initialize integrations
        worldGuardIntegration = new WorldGuardIntegration(this);
        vaultIntegration = new VaultIntegration(this);
        if (vaultIntegration.initialize() && configManager.isEconomyEnabled()) {
            getLogger().info("Storm essence economy enabled.");
        }

        // Initialize zone system
        zoneManager = new ZoneManager(this, configManager);
        if (zoneManager.isEnabled()) {
            getLogger().info("Zone system enabled: Stormlands, Storm Zone, Safe Zone");
        }

        // Initialize Dynmap integration
        dynmapIntegration = new DynmapIntegration(this, zoneManager);
        if (dynmapIntegration.initialize()) {
            getLogger().info("Dynmap visualization enabled.");
        }

        // Initialize core systems
        exposureUtil = new PlayerExposureUtil(this, configManager);
        stormManager = new StormManager(this, configManager, exposureUtil, worldGuardIntegration,
                                       vaultIntegration, zoneManager, dynmapIntegration);

        // Load saved state
        persistenceManager.loadState(stormManager);

        // Clear any existing weather in enabled worlds
        clearVanillaWeather();

        // Start storm manager
        stormManager.start();

        // Register listeners
        registerListeners();

        // Start periodic weather enforcement task (every 5 seconds = 100 ticks)
        Bukkit.getScheduler().runTaskTimer(this, this::enforceWeatherControl, 100L, 100L);

        // Register commands
        registerCommands();

        // Initialize PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIIntegration = new PlaceholderAPIIntegration(this, stormManager);
            placeholderAPIIntegration.register();
            getLogger().info("PlaceholderAPI integration enabled.");
        }

        // Start autosave task (every 10 seconds = 200 ticks)
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, this::autosave, 200L, 200L);

        getLogger().info("Stormcraft enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down Stormcraft...");

        // Stop storm manager
        if (stormManager != null) {
            stormManager.stop();
        }

        // Save state
        if (persistenceManager != null && stormManager != null) {
            persistenceManager.saveState(stormManager);
        }

        // Cancel autosave task
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }

        // Unregister PlaceholderAPI
        if (placeholderAPIIntegration != null) {
            placeholderAPIIntegration.unregister();
        }

        // Shutdown Dynmap integration
        if (dynmapIntegration != null) {
            dynmapIntegration.shutdown();
        }

        getLogger().info("Stormcraft disabled.");
    }

    private void registerListeners() {
        // Register weather control listener to prevent vanilla rain/thunder
        WeatherControlListener weatherListener = new WeatherControlListener(this, configManager, stormManager);
        Bukkit.getPluginManager().registerEvents(weatherListener, this);
    }

    private void registerCommands() {
        StormcraftCommand commandHandler = new StormcraftCommand(this, configManager, stormManager);

        PluginCommand stormcraftCmd = getCommand("stormcraft");
        if (stormcraftCmd != null) {
            stormcraftCmd.setExecutor(commandHandler);
            stormcraftCmd.setTabCompleter(commandHandler);
        } else {
            getLogger().warning("Failed to register /stormcraft command!");
        }

        PluginCommand stormCmd = getCommand("storm");
        if (stormCmd != null) {
            stormCmd.setExecutor(commandHandler);
            stormCmd.setTabCompleter(commandHandler);
        } else {
            getLogger().warning("Failed to register /storm command!");
        }
    }

    private void autosave() {
        if (persistenceManager != null && stormManager != null) {
            persistenceManager.saveState(stormManager);
        }
    }

    /**
     * Clears vanilla weather from all enabled worlds.
     */
    private void clearVanillaWeather() {
        for (String worldName : configManager.getEnabledWorlds()) {
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world != null && (world.hasStorm() || world.isThundering())) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
            }
        }
    }

    /**
     * Periodically enforces weather control - clears vanilla weather if no active storm.
     */
    private void enforceWeatherControl() {
        if (stormManager == null || stormManager.hasActiveStorm()) {
            return; // Let storms control their own weather
        }

        // Clear any vanilla weather that snuck through
        for (String worldName : configManager.getEnabledWorlds()) {
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world != null && (world.hasStorm() || world.isThundering())) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
            }
        }
    }

    // Public getters for external API access
    public StormManager getStormManager() {
        return stormManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerExposureUtil getExposureUtil() {
        return exposureUtil;
    }

    public WorldGuardIntegration getWorldGuardIntegration() {
        return worldGuardIntegration;
    }
}