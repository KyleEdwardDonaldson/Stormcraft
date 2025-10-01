package dev.ked.stormcraft.command;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
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

import java.util.List;
import java.util.UUID;

/**
 * Command handler for /storm - shows closest storm or delegates to admin commands.
 */
public class StormCommand implements CommandExecutor {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormManager stormManager;
    private final StormcraftCommand adminHandler;

    public StormCommand(StormcraftPlugin plugin, ConfigManager config, StormManager stormManager, StormcraftCommand adminHandler) {
        this.plugin = plugin;
        this.config = config;
        this.stormManager = stormManager;
        this.adminHandler = adminHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle /storm ui subcommand
        if (args.length >= 1 && args[0].equalsIgnoreCase("ui")) {
            return handleUIToggle(sender);
        }

        // If args provided, delegate to admin command handler
        if (args.length > 0) {
            return adminHandler.onCommand(sender, command, label, args);
        }

        // Check if using traveling storms + erratic spawning
        boolean usingErraticSpawning = config.isErraticSpawningEnabled() &&
                                       config.isTravelingStormsEnabled();

        // Check for active storms
        List<TravelingStorm> activeStorms = stormManager.getActiveStorms();

        if (!activeStorms.isEmpty()) {
            // Show closest storm
            if (sender instanceof Player player) {
                TravelingStorm closestStorm = findClosestStorm(player, activeStorms);
                if (closestStorm != null) {
                    showTravelingStormInfo(sender, closestStorm, activeStorms.size());
                    return true;
                }
            } else {
                // Console: show first storm
                showTravelingStormInfo(sender, activeStorms.get(0), activeStorms.size());
                return true;
            }
        }

        // No active storms
        if (usingErraticSpawning) {
            // Show erratic spawning status
            sender.sendMessage(Component.text("No active storms.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Storms spawn in random bursts every ", NamedTextColor.GRAY)
                    .append(Component.text(config.getMinBurstDelaySeconds() + "-" + config.getMaxBurstDelaySeconds() + "s", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY)
                    .append(Component.text("/storms", NamedTextColor.AQUA))
                    .append(Component.text(" to see all active storms", NamedTextColor.GRAY)));
        } else if (stormManager.isCountdownActive()) {
            // Show countdown
            int countdown = stormManager.getCountdownRemainingSeconds();
            sender.sendMessage(Component.text("Storm arriving in ", NamedTextColor.GRAY)
                    .append(Component.text(formatTime(countdown), NamedTextColor.YELLOW)));
        } else {
            // Show next storm time
            long nextStormSeconds = stormManager.getSecondsUntilNextStorm();
            sender.sendMessage(Component.text("Next storm in ", NamedTextColor.GRAY)
                    .append(Component.text(formatTime((int) nextStormSeconds), NamedTextColor.YELLOW)));
        }

        return true;
    }

    private TravelingStorm findClosestStorm(Player player, List<TravelingStorm> storms) {
        TravelingStorm closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (TravelingStorm storm : storms) {
            Location stormLoc = storm.getCurrentLocation();
            if (stormLoc.getWorld().equals(player.getWorld())) {
                double distance = player.getLocation().distance(stormLoc);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = storm;
                }
            }
        }

        return closest;
    }

    private void showTravelingStormInfo(CommandSender sender, TravelingStorm storm, int totalStorms) {
        // Top separator
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));

        Component message = Component.text("⛈ ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Traveling Storm", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Type: ", NamedTextColor.GRAY))
                .append(Component.text(formatStormType(storm.getProfile().getType().name()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Remaining: ", NamedTextColor.GRAY))
                .append(Component.text(formatTime(storm.getRemainingSeconds()), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Damage: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f HP/s", storm.getCurrentDamagePerSecond()), NamedTextColor.RED));

        // Add location info
        Location loc = storm.getCurrentLocation();
        message = message.append(Component.newline())
                .append(Component.text("Location: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("(%d, %d)", (int)loc.getX(), (int)loc.getZ()), NamedTextColor.AQUA));

        // Add distance if sender is a player
        if (sender instanceof Player player) {
            if (loc.getWorld().equals(player.getWorld())) {
                double distance = player.getLocation().distance(loc);
                String direction = getDirection(player.getLocation(), loc);
                NamedTextColor distColor = getDistanceColor(distance);

                message = message.append(Component.newline())
                        .append(Component.text("Distance: ", NamedTextColor.GRAY))
                        .append(Component.text(direction + " ", NamedTextColor.WHITE))
                        .append(Component.text((int)distance + " blocks", distColor));

                if (distance <= config.getStormDamageRadius()) {
                    message = message.append(Component.newline())
                            .append(Component.text("⚡ YOU ARE IN THE STORM! ⚡", NamedTextColor.RED, TextDecoration.BOLD));
                }
            }
        }

        // Add total storms note if multiple
        if (totalStorms > 1) {
            message = message.append(Component.newline())
                    .append(Component.text("(" + totalStorms + " total active storms - use ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("/storms", NamedTextColor.AQUA))
                    .append(Component.text(" to see all)", NamedTextColor.DARK_GRAY));
        }

        sender.sendMessage(message);

        // Bottom separator
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
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

    private boolean handleUIToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        dev.ked.stormcraft.ui.StormUIPreferences uiPrefs = plugin.getUIPreferences();
        UUID playerId = player.getUniqueId();

        // Cycle through modes: Both → Actionbar Only → Bossbar Only → Both Disabled → (back to Both)
        boolean actionbarEnabled = uiPrefs.isActionbarEnabled(playerId);
        boolean bossbarEnabled = uiPrefs.isBossbarEnabled(playerId);

        if (actionbarEnabled && bossbarEnabled) {
            // Both enabled → Actionbar only
            uiPrefs.setActionbarEnabled(playerId, true);
            uiPrefs.setBossbarEnabled(playerId, false);
            player.sendMessage(Component.text("Storm UI: ", NamedTextColor.GRAY)
                    .append(Component.text("Action Bar Only", NamedTextColor.YELLOW)));
        } else if (actionbarEnabled && !bossbarEnabled) {
            // Actionbar only → Bossbar only
            uiPrefs.setActionbarEnabled(playerId, false);
            uiPrefs.setBossbarEnabled(playerId, true);
            player.sendMessage(Component.text("Storm UI: ", NamedTextColor.GRAY)
                    .append(Component.text("Boss Bar Only", NamedTextColor.AQUA)));
        } else if (!actionbarEnabled && bossbarEnabled) {
            // Bossbar only → Both disabled
            uiPrefs.setActionbarEnabled(playerId, false);
            uiPrefs.setBossbarEnabled(playerId, false);
            player.sendMessage(Component.text("Storm UI: ", NamedTextColor.GRAY)
                    .append(Component.text("DISABLED", NamedTextColor.RED)));
        } else {
            // Both disabled → Both enabled
            uiPrefs.setActionbarEnabled(playerId, true);
            uiPrefs.setBossbarEnabled(playerId, true);
            player.sendMessage(Component.text("Storm UI: ", NamedTextColor.GRAY)
                    .append(Component.text("Action Bar + Boss Bar", NamedTextColor.GREEN)));
        }

        return true;
    }
}
