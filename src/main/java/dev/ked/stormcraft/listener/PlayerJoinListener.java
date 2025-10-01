package dev.ked.stormcraft.listener;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.schedule.StormManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles custom join messages with storm information.
 */
public class PlayerJoinListener implements Listener {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormManager stormManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerJoinListener(StormcraftPlugin plugin, ConfigManager config, StormManager stormManager) {
        this.plugin = plugin;
        this.config = config;
        this.stormManager = stormManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Only send custom welcome if enabled in config
        if (!config.isCustomWelcomeEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        // Delay by 1 tick to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendWelcomeMessage(player);
        }, 1L);
    }

    private void sendWelcomeMessage(Player player) {
        // Only show storm countdown for global (non-traveling) storms
        if (!config.isTravelingStormsEnabled()) {
            String timeLeft = getFormattedTimeLeft();
            player.sendMessage(miniMessage.deserialize("<yellow>⚡</yellow> <gray>The next storm is in:</gray> <white>" + timeLeft + "</white>"));
        }
    }

    private String getFormattedTimeLeft() {
        long secondsLeft = stormManager.getSecondsUntilNextStorm();

        if (secondsLeft <= 0) {
            return "NOW!";
        }

        if (secondsLeft < 60) {
            return secondsLeft + "s";
        } else if (secondsLeft < 3600) {
            long minutes = secondsLeft / 60;
            long seconds = secondsLeft % 60;
            return String.format("%dm %ds", minutes, seconds);
        } else {
            long hours = secondsLeft / 3600;
            long minutes = (secondsLeft % 3600) / 60;
            return String.format("%dh %dm", hours, minutes);
        }
    }
}
