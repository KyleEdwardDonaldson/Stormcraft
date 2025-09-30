package dev.ked.stormcraft.ui;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks and displays traveling storm information to players.
 * Uses ActionBar for nearby storms and BossBar for active storm proximity.
 */
public class StormTracker extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final ZoneManager zoneManager;

    private TravelingStorm activeStorm;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    // Cardinal directions
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public StormTracker(StormcraftPlugin plugin, ConfigManager config, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
    }

    public void setActiveStorm(TravelingStorm storm) {
        this.activeStorm = storm;

        if (storm == null) {
            // Clear all boss bars when storm ends
            clearAllBossBars();
        }
    }

    @Override
    public void run() {
        if (activeStorm == null) {
            return;
        }

        Location stormLocation = activeStorm.getCurrentLocation();
        double damageRadius = config.getStormDamageRadius();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip if player not in enabled world
            if (!config.getEnabledWorlds().contains(player.getWorld().getName())) {
                continue;
            }

            Location playerLoc = player.getLocation();

            // Calculate distance to storm
            double distance = playerLoc.distance(stormLocation);

            // Determine if player should see storm info
            boolean showTracker = shouldShowTracker(player, distance);

            if (showTracker) {
                updatePlayerStormDisplay(player, stormLocation, distance, damageRadius);
            } else {
                removePlayerDisplay(player);
            }
        }
    }

    /**
     * Determines if a player should see the storm tracker.
     */
    private boolean shouldShowTracker(Player player, double distance) {
        // Always show if using ActionBar mode
        if (config.getStormTrackerMode().equals("actionbar")) {
            return distance <= config.getStormTrackerRange();
        }

        // BossBar mode: show if within range or in storm
        return distance <= config.getStormTrackerRange() ||
               distance <= config.getStormDamageRadius();
    }

    /**
     * Updates storm display for a player.
     */
    private void updatePlayerStormDisplay(Player player, Location stormLoc, double distance, double damageRadius) {
        String direction = getDirection(player.getLocation(), stormLoc);

        if (config.getStormTrackerMode().equals("actionbar")) {
            showActionBar(player, direction, distance, damageRadius);
        } else {
            showBossBar(player, direction, distance, damageRadius);
        }
    }

    /**
     * Shows storm info on player's ActionBar.
     */
    private void showActionBar(Player player, String direction, double distance, double damageRadius) {
        Component message;

        if (distance <= damageRadius) {
            // Player is IN the storm - danger!
            message = Component.text("⚡ ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("IN STORM", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" ⚡", NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            // Show distance and direction
            int distanceBlocks = (int) distance;
            NamedTextColor color = getDistanceColor(distance);

            message = Component.text("⛈ ", color)
                    .append(Component.text("STORM ", color, TextDecoration.BOLD))
                    .append(Component.text(direction, NamedTextColor.WHITE))
                    .append(Component.text(" " + distanceBlocks + "m", color));
        }

        player.sendActionBar(message);
    }

    /**
     * Shows storm info on player's BossBar.
     */
    private void showBossBar(Player player, String direction, double distance, double damageRadius) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = playerBossBars.get(playerId);

        // Create boss bar if doesn't exist
        if (bossBar == null) {
            bossBar = BossBar.bossBar(
                Component.empty(),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
            );
            playerBossBars.put(playerId, bossBar);
            player.showBossBar(bossBar);
        }

        // Update boss bar content
        Component title;
        BossBar.Color color;
        float progress;

        if (distance <= damageRadius) {
            // IN STORM
            title = Component.text("⚡ ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("ACTIVE STORM", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" - SEEK SHELTER", NamedTextColor.YELLOW));
            color = BossBar.Color.RED;
            progress = 1.0f;
        } else {
            // Show approaching storm
            int distanceBlocks = (int) distance;
            title = Component.text("⛈ ", NamedTextColor.YELLOW)
                    .append(Component.text("Storm ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(direction, NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(distanceBlocks + " blocks", NamedTextColor.YELLOW));

            // Progress bar based on distance (closer = fuller)
            double maxRange = config.getStormTrackerRange();
            progress = Math.max(0.1f, (float) (1.0 - (distance / maxRange)));

            // Color based on distance
            if (distance < 100) {
                color = BossBar.Color.RED;
            } else if (distance < 300) {
                color = BossBar.Color.YELLOW;
            } else {
                color = BossBar.Color.BLUE;
            }
        }

        bossBar.name(title);
        bossBar.color(color);
        bossBar.progress(progress);
    }

    /**
     * Removes storm display from a player.
     */
    private void removePlayerDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = playerBossBars.remove(playerId);

        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    /**
     * Clears all boss bars from all players.
     */
    private void clearAllBossBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removePlayerDisplay(player);
        }
        playerBossBars.clear();
    }

    /**
     * Gets cardinal direction from player to target.
     */
    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        // Calculate angle in degrees (0 = North, 90 = East, etc.)
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (angle + 90) % 360; // Adjust so North = 0
        if (angle < 0) angle += 360;

        // Convert to 8-direction cardinal
        int index = (int) Math.round(angle / 45.0) % 8;
        return DIRECTIONS[index];
    }

    /**
     * Gets color based on distance (red = close, yellow = medium, blue = far).
     */
    private NamedTextColor getDistanceColor(double distance) {
        if (distance < 100) {
            return NamedTextColor.RED;
        } else if (distance < 300) {
            return NamedTextColor.YELLOW;
        } else {
            return NamedTextColor.AQUA;
        }
    }

    /**
     * Cleanup when tracker is stopped.
     */
    public void shutdown() {
        clearAllBossBars();
        cancel();
    }
}