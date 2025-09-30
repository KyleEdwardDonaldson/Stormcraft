package dev.ked.stormcraft.listener;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.schedule.StormManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Prevents vanilla Minecraft weather changes.
 * Only allows weather during Stormcraft storms.
 */
public class WeatherControlListener implements Listener {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormManager stormManager;

    public WeatherControlListener(StormcraftPlugin plugin, ConfigManager config, StormManager stormManager) {
        this.plugin = plugin;
        this.config = config;
        this.stormManager = stormManager;
    }

    /**
     * Prevents vanilla weather changes in enabled worlds.
     * Only allows weather when Stormcraft has an active storm.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        World world = event.getWorld();

        // Only control weather in enabled worlds
        if (!config.getEnabledWorlds().contains(world.getName())) {
            return;
        }

        // If weather is starting (toWeatherState = true = rain starting)
        if (event.toWeatherState()) {
            // Cancel vanilla rain unless we have an active storm
            if (!stormManager.hasActiveStorm()) {
                event.setCancelled(true);
            }
        }
        // If weather is stopping (toWeatherState = false = clearing)
        else {
            // Cancel clearing if we still have an active storm
            if (stormManager.hasActiveStorm()) {
                event.setCancelled(true);
            }
        }
    }
}