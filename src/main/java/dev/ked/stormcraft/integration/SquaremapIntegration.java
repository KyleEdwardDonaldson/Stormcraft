package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.marker.Circle;
import xyz.jpenilla.squaremap.api.marker.Icon;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration with squaremap to show zones and active storms on the map.
 */
public class SquaremapIntegration {
    private final StormcraftPlugin plugin;
    private final ZoneManager zoneManager;

    private Squaremap squaremapApi;
    private SimpleLayerProvider layerProvider;

    private final Map<String, Marker> zoneMarkers = new HashMap<>();
    private final Map<String, Marker> stormMarkers = new HashMap<>();

    private boolean enabled = false;

    public SquaremapIntegration(StormcraftPlugin plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    /**
     * Initializes squaremap integration if squaremap is available.
     */
    public boolean initialize() {
        if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
            return false;
        }

        try {
            // Get squaremap API
            squaremapApi = SquaremapProvider.get();

            // Create layer for Stormcraft markers
            layerProvider = SimpleLayerProvider.builder("Stormcraft")
                    .showControls(true)
                    .defaultHidden(false)
                    .build();

            // Register layer with all enabled worlds
            for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
                org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld != null) {
                    WorldIdentifier worldId = BukkitAdapter.worldIdentifier(bukkitWorld);
                    squaremapApi.getWorldIfEnabled(worldId).ifPresent(mapWorld -> {
                        mapWorld.layerRegistry().register(Key.of("stormcraft"), layerProvider);
                    });
                }
            }

            enabled = true;

            // Initialize zone circles if zones are enabled
            if (zoneManager.isEnabled()) {
                createZoneCircles();
            }

            plugin.getLogger().info("squaremap integration enabled.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize squaremap integration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates circular markers for each zone on the map.
     */
    private void createZoneCircles() {
        if (!enabled || !zoneManager.isEnabled()) {
            return;
        }

        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();

        // Remove existing circles if they exist
        removeZoneCircles();

        Point center = Point.of(centerX, centerZ);

        // Create Stormlands circle (innermost, red)
        Circle stormlandsCircle = Marker.circle(center, zoneManager.getStormlandsRadius());
        stormlandsCircle.markerOptions(
            MarkerOptions.builder()
                .strokeColor(Color.RED)
                .strokeWeight(2)
                .fillColor(new Color(255, 68, 68, 77)) // Red with alpha
                .clickTooltip("Stormlands (High Risk, High Reward)")
                .build()
        );
        zoneMarkers.put("stormlands", stormlandsCircle);
        layerProvider.addMarker(Key.of("stormlands_zone"), stormlandsCircle);

        // Create Storm Zone circle (middle ring, orange)
        Circle stormZoneCircle = Marker.circle(center, zoneManager.getStormZoneRadius());
        stormZoneCircle.markerOptions(
            MarkerOptions.builder()
                .strokeColor(new Color(255, 136, 0))
                .strokeWeight(2)
                .fillColor(new Color(255, 170, 0, 51)) // Orange with alpha
                .clickTooltip("Storm Zone (Moderate Risk)")
                .build()
        );
        zoneMarkers.put("stormzone", stormZoneCircle);
        layerProvider.addMarker(Key.of("storm_zone"), stormZoneCircle);

        // Create Safe Zone circle (outermost, green)
        Circle safeZoneCircle = Marker.circle(center, zoneManager.getSafeZoneRadius());
        safeZoneCircle.markerOptions(
            MarkerOptions.builder()
                .strokeColor(Color.GREEN)
                .strokeWeight(2)
                .fillColor(new Color(68, 255, 68, 26)) // Green with alpha
                .clickTooltip("Safe Zone (Low Storm Frequency)")
                .build()
        );
        zoneMarkers.put("safezone", safeZoneCircle);
        layerProvider.addMarker(Key.of("safe_zone"), safeZoneCircle);
    }

    /**
     * Removes zone circle markers from the map.
     */
    private void removeZoneCircles() {
        if (layerProvider == null) return;

        layerProvider.removeMarker(Key.of("stormlands_zone"));
        layerProvider.removeMarker(Key.of("storm_zone"));
        layerProvider.removeMarker(Key.of("safe_zone"));
        zoneMarkers.clear();
    }

    /**
     * Updates or creates a marker for an active traveling storm.
     */
    public void updateStormMarker(TravelingStorm storm) {
        if (!enabled || storm == null || layerProvider == null) {
            return;
        }

        Location loc = storm.getCurrentLocation();

        // Remove old markers
        removeStormMarker();

        Point stormPoint = Point.of(loc.getX(), loc.getZ());

        // Create storm center icon marker
        Icon stormIcon = Icon.icon(
            stormPoint,
            Key.of("storm_icon"),
            16, 16
        );
        stormIcon.markerOptions(
            MarkerOptions.builder()
                .clickTooltip(String.format(
                    "%s Storm - %ds remaining - %.1f HP/s",
                    storm.getProfile().getType().name(),
                    storm.getRemainingSeconds(),
                    storm.getCurrentDamagePerSecond()
                ))
                .build()
        );
        stormMarkers.put("icon", stormIcon);
        layerProvider.addMarker(Key.of("active_storm_icon"), stormIcon);

        // Create damage radius circle
        Circle damageCircle = Marker.circle(stormPoint, plugin.getConfigManager().getStormDamageRadius());
        damageCircle.markerOptions(
            MarkerOptions.builder()
                .strokeColor(Color.RED)
                .strokeWeight(3)
                .fillColor(new Color(255, 0, 0, 51)) // Red with transparency
                .clickTooltip("Storm Damage Area")
                .build()
        );
        stormMarkers.put("circle", damageCircle);
        layerProvider.addMarker(Key.of("active_storm_circle"), damageCircle);
    }

    /**
     * Removes the active storm marker from the map.
     */
    public void removeStormMarker() {
        if (layerProvider == null) return;

        layerProvider.removeMarker(Key.of("active_storm_icon"));
        layerProvider.removeMarker(Key.of("active_storm_circle"));
        stormMarkers.clear();
    }

    /**
     * Reloads zone circles (useful after config reload).
     */
    public void reloadZones() {
        if (enabled && zoneManager.isEnabled()) {
            createZoneCircles();
        } else {
            removeZoneCircles();
        }
    }

    /**
     * Cleans up all squaremap markers and layers.
     */
    public void shutdown() {
        if (!enabled) {
            return;
        }

        removeZoneCircles();
        removeStormMarker();

        // Unregister layer from all worlds
        if (squaremapApi != null) {
            for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
                org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld != null) {
                    WorldIdentifier worldId = BukkitAdapter.worldIdentifier(bukkitWorld);
                    squaremapApi.getWorldIfEnabled(worldId).ifPresent(mapWorld -> {
                        mapWorld.layerRegistry().unregister(Key.of("stormcraft"));
                    });
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
