package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.model.StormProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles countdown announcements before a storm reaches landfall.
 */
public class CountdownTask extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormProfile profile;
    private final Runnable onComplete;

    private int remainingSeconds;
    private final List<Integer> announceSchedule;

    public CountdownTask(StormcraftPlugin plugin, ConfigManager config,
                        StormProfile profile, int countdownSeconds, Runnable onComplete) {
        this.plugin = plugin;
        this.config = config;
        this.profile = profile;
        this.remainingSeconds = countdownSeconds;
        this.onComplete = onComplete;
        this.announceSchedule = config.getCountdownAnnounceScheduleSeconds();
    }

    @Override
    public void run() {
        // Check if we should announce at this second
        if (announceSchedule.contains(remainingSeconds)) {
            announceCountdown();
        }

        remainingSeconds--;

        // Check if countdown is complete
        if (remainingSeconds < 0) {
            this.cancel();
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    /**
     * Announces the countdown to all players.
     */
    private void announceCountdown() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("time", formatTime(remainingSeconds));
        placeholders.put("dps", String.format("%.1f", profile.getDamagePerSecond()));
        placeholders.put("type", formatStormType(profile.getType().name()));

        Component message = config.formatMessage("countdown", placeholders);
        Bukkit.broadcast(message);

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Countdown: " + remainingSeconds + "s remaining");
        }
    }

    /**
     * Formats seconds into a readable time string (e.g., "3:00", "45s").
     */
    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%d:%02d", minutes, secs);
        } else {
            return seconds + "s";
        }
    }

    /**
     * Formats storm type enum name into a readable string.
     */
    private String formatStormType(String enumName) {
        return switch (enumName) {
            case "SHORT_WEAK" -> "Short Weak";
            case "MEDIUM" -> "Medium";
            case "LONG_DANGEROUS" -> "Long Dangerous";
            default -> enumName;
        };
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }
}