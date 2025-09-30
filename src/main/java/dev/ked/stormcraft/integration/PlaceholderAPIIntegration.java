package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.schedule.StormManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI integration for Stormcraft.
 * Provides placeholders like %stormcraft_status%, %stormcraft_time_left%, etc.
 */
public class PlaceholderAPIIntegration extends PlaceholderExpansion {
    private final StormcraftPlugin plugin;
    private final StormManager stormManager;

    public PlaceholderAPIIntegration(StormcraftPlugin plugin, StormManager stormManager) {
        this.plugin = plugin;
        this.stormManager = stormManager;
    }

    @Override
    public String getIdentifier() {
        return "stormcraft";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep expansion loaded across reloads
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return switch (params.toLowerCase()) {
            case "status" -> getStatusString();
            case "time_left" -> getTimeLeftString();
            case "type" -> getStormTypeString();
            case "dps" -> getDpsString();
            case "next_storm" -> getNextStormString();
            default -> null;
        };
    }

    private String getStatusString() {
        if (stormManager.hasActiveStorm()) {
            return "Active";
        } else if (stormManager.isCountdownActive()) {
            return "Countdown";
        } else {
            return "Idle";
        }
    }

    private String getTimeLeftString() {
        if (stormManager.hasActiveStorm()) {
            int seconds = stormManager.getActiveStorm().getRemainingSeconds();
            return formatTime(seconds);
        } else if (stormManager.isCountdownActive()) {
            int seconds = stormManager.getCountdownRemainingSeconds();
            return formatTime(seconds);
        } else {
            long nextStormSeconds = stormManager.getSecondsUntilNextStorm();
            return formatTime((int) nextStormSeconds);
        }
    }

    private String getStormTypeString() {
        if (stormManager.hasActiveStorm()) {
            return formatStormType(stormManager.getActiveStorm().getProfile().getType().name());
        } else if (stormManager.isCountdownActive()) {
            return formatStormType(stormManager.getUpcomingStormType().name());
        } else {
            return "None";
        }
    }

    private String getDpsString() {
        if (stormManager.hasActiveStorm()) {
            return String.format("%.1f", stormManager.getActiveStorm().getProfile().getDamagePerSecond());
        } else if (stormManager.isCountdownActive() && stormManager.getUpcomingProfile() != null) {
            return String.format("%.1f", stormManager.getUpcomingProfile().getDamagePerSecond());
        } else {
            return "0.0";
        }
    }

    private String getNextStormString() {
        long seconds = stormManager.getSecondsUntilNextStorm();
        return formatTime((int) seconds);
    }

    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%dm %ds", minutes, secs);
        } else {
            return seconds + "s";
        }
    }

    private String formatStormType(String enumName) {
        return switch (enumName) {
            case "SHORT_WEAK" -> "Short Weak";
            case "MEDIUM" -> "Medium";
            case "LONG_DANGEROUS" -> "Long Dangerous";
            default -> enumName;
        };
    }
}