package dev.ked.stormcraft.command;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.model.ActiveStorm;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.schedule.StormManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command handler for /storms - lists all active storms.
 * Currently supports single storm, but designed for future multi-storm support.
 */
public class StormsCommand implements CommandExecutor {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormManager stormManager;

    public StormsCommand(StormcraftPlugin plugin, ConfigManager config, StormManager stormManager) {
        this.plugin = plugin;
        this.config = config;
        this.stormManager = stormManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for multiple active storms first
        java.util.List<TravelingStorm> activeStorms = stormManager.getActiveStorms();
        if (!activeStorms.isEmpty()) {
            showMultipleStorms(sender, activeStorms);
            return true;
        }

        // Check for traveling storm (legacy single storm)
        TravelingStorm travelingStorm = stormManager.getTravelingStorm();
        ActiveStorm activeStorm = stormManager.getActiveStorm();

        if (travelingStorm == null && activeStorm == null) {
            sender.sendMessage(Component.text("No active storms.", NamedTextColor.GRAY));

            if (stormManager.isCountdownActive()) {
                int countdown = stormManager.getCountdownRemainingSeconds();
                sender.sendMessage(Component.text("Next storm arriving in ", NamedTextColor.GRAY)
                        .append(Component.text(formatTime(countdown), NamedTextColor.YELLOW)));
            }
            return true;
        }

        // Header
        sender.sendMessage(Component.text("════ ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Active Storms", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ════", NamedTextColor.DARK_GRAY)));

        // Show traveling storm
        if (travelingStorm != null) {
            showTravelingStorm(sender, travelingStorm);
        }
        // Show stationary storm
        else if (activeStorm != null) {
            showStationaryStorm(sender, activeStorm);
        }

        return true;
    }

    /**
     * Shows information about multiple active storms.
     */
    private void showMultipleStorms(CommandSender sender, java.util.List<TravelingStorm> storms) {
        // Header
        sender.sendMessage(Component.text("════ ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Active Storms", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (" + storms.size() + " total)", NamedTextColor.GRAY))
                .append(Component.text(" ════", NamedTextColor.DARK_GRAY)));

        // Sort storms by distance to edge if sender is a player
        java.util.List<TravelingStorm> sortedStorms = new java.util.ArrayList<>(storms);
        if (sender instanceof Player player) {
            sortedStorms.sort((s1, s2) -> {
                Location loc1 = s1.getCurrentLocation();
                Location loc2 = s2.getCurrentLocation();
                if (!loc1.getWorld().equals(player.getWorld())) return 1;
                if (!loc2.getWorld().equals(player.getWorld())) return -1;

                // Calculate distance to edge (0 if inside storm)
                double distToCenter1 = player.getLocation().distance(loc1);
                double distToEdge1 = Math.max(0, distToCenter1 - s1.getDamageRadius());

                double distToCenter2 = player.getLocation().distance(loc2);
                double distToEdge2 = Math.max(0, distToCenter2 - s2.getDamageRadius());

                return Double.compare(distToEdge1, distToEdge2);
            });
        }

        // Show each storm
        for (int i = 0; i < sortedStorms.size(); i++) {
            TravelingStorm storm = sortedStorms.get(i);
            showTravelingStorm(sender, storm, i + 1);
            if (i < sortedStorms.size() - 1) {
                sender.sendMessage(Component.empty());
            }
        }
    }

    private void showTravelingStorm(CommandSender sender, TravelingStorm storm) {
        showTravelingStorm(sender, storm, 0);
    }

    private void showTravelingStorm(CommandSender sender, TravelingStorm storm, int number) {
        Component prefix = number > 0 ?
            Component.text("#" + number + " ", NamedTextColor.DARK_GRAY) :
            Component.empty();

        // Calculate storm size category based on radius
        String sizeCategory = getStormSizeCategory(storm.getDamageRadius());

        Component message = prefix
                .append(Component.text("⛈ ", NamedTextColor.YELLOW))
                .append(Component.text(sizeCategory, NamedTextColor.WHITE))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Time: ", NamedTextColor.GRAY))
                .append(Component.text(formatTime(storm.getRemainingSeconds()), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Damage: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f HP/s", storm.getCurrentDamagePerSecond()), NamedTextColor.RED));

        // Add location info
        Location loc = storm.getCurrentLocation();
        message = message.append(Component.newline())
                .append(Component.text("  Location: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("(%d, %d)", (int)loc.getX(), (int)loc.getZ()), NamedTextColor.AQUA));

        // Add distance if sender is a player
        if (sender instanceof Player player) {
            if (loc.getWorld().equals(player.getWorld())) {
                double distanceToCenter = player.getLocation().distance(loc);
                double damageRadius = storm.getDamageRadius();
                double distanceToEdge = Math.max(0, distanceToCenter - damageRadius);

                message = message.append(Component.text(" | ", NamedTextColor.DARK_GRAY));

                if (distanceToEdge == 0) {
                    // Player is inside the storm
                    message = message.append(Component.text("YOU ARE IN THIS STORM ", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text("⚡", NamedTextColor.YELLOW, TextDecoration.BOLD));
                } else {
                    // Player is outside, show distance to edge
                    String direction = getDirection(player.getLocation(), loc);
                    NamedTextColor distColor = getDistanceColor(distanceToEdge);

                    message = message.append(Component.text("Distance: ", NamedTextColor.GRAY))
                            .append(Component.text(direction + " ", NamedTextColor.WHITE))
                            .append(Component.text((int)distanceToEdge + "m", distColor));
                }
            }
        }

        sender.sendMessage(message);
    }

    private void showStationaryStorm(CommandSender sender, ActiveStorm storm) {
        Component message = Component.text("⛈ ", NamedTextColor.YELLOW)
                .append(Component.text("Global Storm", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("  Type: ", NamedTextColor.GRAY))
                .append(Component.text(formatStormType(storm.getProfile().getType().name()), NamedTextColor.WHITE))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Time: ", NamedTextColor.GRAY))
                .append(Component.text(formatTime(storm.getRemainingSeconds()), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Damage: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f HP/s", storm.getActualDamagePerSecond()), NamedTextColor.RED));

        sender.sendMessage(message);
    }

    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (angle + 90) % 360;
        if (angle < 0) angle += 360;
        int index = (int) Math.round(angle / 45.0) % 8;
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return directions[index];
    }

    private NamedTextColor getDistanceColor(double distance) {
        if (distance < 100) return NamedTextColor.RED;
        else if (distance < 300) return NamedTextColor.YELLOW;
        else return NamedTextColor.AQUA;
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

    private String formatStormType(String type) {
        return switch (type) {
            case "SHORT_WEAK" -> "Short Weak";
            case "MEDIUM" -> "Medium";
            case "LONG_DANGEROUS" -> "Long Dangerous";
            default -> type;
        };
    }

    private String getStormSizeCategory(double radius) {
        if (radius < 600) return "Tiny";
        else if (radius < 1000) return "Small";
        else if (radius < 1500) return "Medium";
        else if (radius < 2000) return "Large";
        else if (radius < 2500) return "Huge";
        else return "Massive";
    }
}
