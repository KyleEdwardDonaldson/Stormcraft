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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final StormUIPreferences uiPreferences;

    private TravelingStorm activeStorm;
    private List<TravelingStorm> activeStorms = new ArrayList<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    // Cardinal directions
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public StormTracker(StormcraftPlugin plugin, ConfigManager config, ZoneManager zoneManager, StormUIPreferences uiPreferences) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
        this.uiPreferences = uiPreferences;
    }

    public void setActiveStorm(TravelingStorm storm) {
        this.activeStorm = storm;

        if (storm == null) {
            // Clear all boss bars when storm ends
            clearAllBossBars();
        }
    }

    public void setActiveStorms(List<TravelingStorm> storms) {
        this.activeStorms = storms;

        if (storms.isEmpty()) {
            clearAllBossBars();
        }
    }

    @Override
    public void run() {
        // Multi-storm system - show closest storm
        if (!activeStorms.isEmpty()) {
            runMultiStormTracking();
            return;
        }

        // Single storm system (legacy)
        if (activeStorm != null) {
            runSingleStormTracking();
            return;
        }

        // No active storms - just show zone info on actionbar
        showZoneActionbarForAllPlayers();
    }

    private void runSingleStormTracking() {
        Location stormLocation = activeStorm.getCurrentLocation();
        double damageRadius = activeStorm.getDamageRadius();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip if player not in enabled world
            if (!config.getEnabledWorlds().contains(player.getWorld().getName())) {
                continue;
            }

            Location playerLoc = player.getLocation();

            // Calculate distance to storm edge
            double distanceToCenter = playerLoc.distance(stormLocation);
            double distanceToEdge = Math.max(0, distanceToCenter - damageRadius);

            // Determine if player should see storm info
            boolean showTracker = shouldShowTracker(player, distanceToEdge);

            if (showTracker) {
                // Single storm
                updatePlayerStormDisplay(player, activeStorm, distanceToEdge);
            } else {
                removePlayerDisplay(player);
            }
        }
    }

    private void showZoneActionbarForAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!config.getEnabledWorlds().contains(player.getWorld().getName())) {
                continue;
            }

            UUID playerId = player.getUniqueId();
            if (uiPreferences.isActionbarEnabled(playerId)) {
                showActionBar(player, null, 0, 0); // Zone display doesn't need storm params
            }
        }
    }

    /**
     * Handles storm tracking for multiple simultaneous storms.
     * Shows the closest storm to each player.
     */
    private void runMultiStormTracking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip if player not in enabled world
            if (!config.getEnabledWorlds().contains(player.getWorld().getName())) {
                removePlayerDisplay(player);
                continue;
            }

            Location playerLoc = player.getLocation();

            // Sort storms by distance to edge (same as /storms command)
            java.util.List<TravelingStorm> sortedStorms = getSortedStormsByDistance(player);

            if (sortedStorms.isEmpty()) {
                removePlayerDisplay(player);
                continue;
            }

            // Show closest storm (storm #1)
            TravelingStorm closestStorm = sortedStorms.get(0);
            Location stormLocation = closestStorm.getCurrentLocation();
            double distanceToCenter = playerLoc.distance(stormLocation);
            double damageRadius = closestStorm.getDamageRadius();

            // Calculate distance to edge (0 if inside storm)
            double distanceToEdge = Math.max(0, distanceToCenter - damageRadius);

            // Determine if player should see storm info
            boolean showTracker = shouldShowTracker(player, distanceToEdge);

            if (showTracker) {
                // Storm number is 1 (closest storm)
                updatePlayerStormDisplay(player, closestStorm, distanceToEdge);
            } else {
                removePlayerDisplay(player);
            }
        }
    }

    /**
     * Gets storms sorted by distance to edge (same sorting as /storms command).
     */
    private java.util.List<TravelingStorm> getSortedStormsByDistance(Player player) {
        java.util.List<TravelingStorm> sorted = new java.util.ArrayList<>(activeStorms);
        sorted.sort((s1, s2) -> {
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
        return sorted;
    }

    /**
     * Determines if a player should see the storm tracker.
     * Always shows the closest storm regardless of distance.
     */
    private boolean shouldShowTracker(Player player, double distance) {
        // Always show the closest storm to help players track them
        return true;
    }

    /**
     * Updates storm display for a player.
     * Shows both actionbar and bossbar based on user preferences.
     */
    private void updatePlayerStormDisplay(Player player, TravelingStorm storm, double distanceToEdge) {
        Location stormLoc = storm.getCurrentLocation();
        double damageRadius = storm.getDamageRadius();

        // Set player weather to rain if inside storm
        boolean isInStorm = distanceToEdge == 0;
        if (isInStorm) {
            player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
        } else {
            player.resetPlayerWeather();
        }
        UUID playerId = player.getUniqueId();

        // Show actionbar if enabled for this player
        if (uiPreferences.isActionbarEnabled(playerId)) {
            showActionBar(player, null, distanceToEdge, damageRadius);
        }

        // Show bossbar if enabled for this player
        if (uiPreferences.isBossbarEnabled(playerId)) {
            showBossBar(player, storm, distanceToEdge);
        } else {
            // If bossbar disabled, remove it if it exists
            removePlayerBossBar(player);
        }
    }

    /**
     * Shows storm info on player's ActionBar.
     */
    private void showActionBar(Player player, String direction, double distance, double damageRadius) {
        // Show current zone instead of storm info
        if (!zoneManager.isEnabled()) {
            return; // Don't show anything if zones disabled
        }

        ZoneManager.ZoneType zone = zoneManager.getZoneAt(player.getLocation());
        Component message;

        switch (zone) {
            case STORMLANDS:
                message = Component.text("⚡ ", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text("STORMLANDS", NamedTextColor.RED, TextDecoration.BOLD));
                break;
            case STORM_ZONE:
                message = Component.text("⛈ ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .append(Component.text("STORM ZONE", NamedTextColor.YELLOW, TextDecoration.BOLD));
                break;
            case SAFE_ZONE:
                message = Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(Component.text("SAFE ZONE", NamedTextColor.GREEN, TextDecoration.BOLD));
                break;
            default:
                return;
        }

        player.sendActionBar(message);
    }

    /**
     * Shows storm info on player's BossBar.
     */
    private void showBossBar(Player player, TravelingStorm storm, double distanceToEdge) {
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

        Location playerLoc = player.getLocation();
        Location stormLoc = storm.getCurrentLocation();
        double damageRadius = storm.getDamageRadius();

        // Update boss bar content
        Component title;
        BossBar.Color color;
        float progress;

        if (distanceToEdge == 0) {
            // IN STORM - show escape direction
            String escapeDirection = getEscapeDirection(player, storm);

            title = Component.text("⚡ ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("ACTIVE STORM", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" - ESCAPE ", NamedTextColor.YELLOW))
                    .append(Component.text(escapeDirection, NamedTextColor.WHITE, TextDecoration.BOLD));
            color = BossBar.Color.RED;
            progress = 1.0f;
        } else {
            // Show approaching storm (distance to edge)
            String direction = getDirection(playerLoc, stormLoc);
            int distanceBlocks = (int) distanceToEdge;

            title = Component.text("⛈ ", NamedTextColor.YELLOW)
                    .append(Component.text("Storm ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(direction, NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(distanceBlocks + " blocks", NamedTextColor.YELLOW));

            // Progress bar based on distance (closer = fuller)
            double maxRange = config.getStormTrackerRange();
            progress = Math.max(0.1f, (float) (1.0 - (distanceToEdge / maxRange)));

            // Color based on distance
            if (distanceToEdge < 100) {
                color = BossBar.Color.RED;
            } else if (distanceToEdge < 300) {
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
        removePlayerBossBar(player);
    }

    /**
     * Removes only the bossbar from a player.
     */
    private void removePlayerBossBar(Player player) {
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
     * Gets the best escape direction for a player inside a storm.
     * Points away from storm center, unless storm is chasing faster than player can run.
     */
    private String getEscapeDirection(Player player, TravelingStorm storm) {
        Location playerLoc = player.getLocation();
        Location stormLoc = storm.getCurrentLocation();

        // Calculate direction from storm center to player (away from storm)
        double dx = playerLoc.getX() - stormLoc.getX();
        double dz = playerLoc.getZ() - stormLoc.getZ();

        // Get storm's current target waypoint to determine movement direction
        Location targetLoc = storm.getTargetLocation();
        double stormDx = targetLoc.getX() - stormLoc.getX();
        double stormDz = targetLoc.getZ() - stormLoc.getZ();

        // Normalize storm movement vector
        double stormDistance = Math.sqrt(stormDx * stormDx + stormDz * stormDz);
        if (stormDistance > 0) {
            stormDx /= stormDistance;
            stormDz /= stormDistance;
        }

        // Calculate dot product to see if storm is moving toward player
        double dotProduct = (stormDx * dx) + (stormDz * dz);

        // Player run speed ~5.6 blocks/s (walking), sprint ~7.0 blocks/s
        double playerSpeed = 7.0;
        double stormSpeed = storm.getMovementSpeed();

        // If storm is chasing faster than player can run, flip the direction
        if (dotProduct > 0 && stormSpeed > playerSpeed) {
            // Storm catching up - run opposite direction (toward storm center, then through it)
            dx = -dx;
            dz = -dz;
        }

        // Convert to cardinal direction with arrow
        return getDirectionWithArrow(dx, dz);
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
     * Converts dx, dz to cardinal direction with Unicode arrow.
     */
    private String getDirectionWithArrow(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (angle + 90) % 360;
        if (angle < 0) angle += 360;

        int index = (int) Math.round(angle / 45.0) % 8;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[index] + " " + DIRECTIONS[index];
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