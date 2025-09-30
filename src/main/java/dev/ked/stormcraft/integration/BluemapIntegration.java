package dev.ked.stormcraft.integration;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import de.bluecolored.bluemap.api.markers.*;
import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration with BlueMap to show zones and active storms on the map.
 */
public class BluemapIntegration {
    private final StormcraftPlugin plugin;
    private final ZoneManager zoneManager;

    private BlueMapAPI blueMapAPI;
    private MarkerSet markerSet;

    private final Map<String, Marker> zoneMarkers = new HashMap<>();
    private final Map<String, Marker> stormMarkers = new HashMap<>();

    private boolean enabled = false;

    public BluemapIntegration(StormcraftPlugin plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    /**
     * Initializes BlueMap integration if BlueMap is available.
     */
    public boolean initialize() {
        if (Bukkit.getPluginManager().getPlugin("BlueMap") == null) {
            return false;
        }

        try {
            BlueMapAPI.onEnable(api -> {
                this.blueMapAPI = api;

                // Create marker set for Stormcraft
                for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
                    api.getWorld(Bukkit.getWorld(worldName)).ifPresent(world -> {
                        for (BlueMapMap map : world.getMaps()) {
                            markerSet = map.getMarkerSets().computeIfAbsent("stormcraft",
                                id -> MarkerSet.builder()
                                    .label("Stormcraft")
                                    .toggleable(true)
                                    .defaultHidden(false)
                                    .build()
                            );
                        }
                    });
                }

                enabled = true;

                // Initialize zone circles if zones are enabled
                if (zoneManager.isEnabled()) {
                    createZoneCircles();
                }

                plugin.getLogger().info("BlueMap integration enabled.");
            });

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize BlueMap integration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates circular markers for each zone on the map.
     */
    private void createZoneCircles() {
        if (!enabled || !zoneManager.isEnabled() || markerSet == null) {
            return;
        }

        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();

        // Remove existing circles if they exist
        removeZoneCircles();

        // Create Stormlands circle (red)
        Marker stormlandsCircle = ExtrudeMarker.builder()
                .label("Stormlands (High Risk, High Reward)")
                .shape(Shape.createCircle(centerX, centerZ, zoneManager.getStormlandsRadius(), 64), 64, 80)
                .centerPosition()
                .lineColor(new Color(255, 0, 0, 0.8f))
                .fillColor(new Color(255, 0, 0, 0.15f))
                .lineWidth(3)
                .build();

        zoneMarkers.put("stormlands", stormlandsCircle);
        markerSet.put("stormlands_circle", stormlandsCircle);

        // Create Storm Zone circle (orange)
        Marker stormZoneCircle = ExtrudeMarker.builder()
                .label("Storm Zone (Moderate Risk)")
                .shape(Shape.createCircle(centerX, centerZ, zoneManager.getStormZoneRadius(), 64), 64, 80)
                .centerPosition()
                .lineColor(new Color(255, 165, 0, 0.8f))
                .fillColor(new Color(255, 165, 0, 0.1f))
                .lineWidth(2)
                .build();

        zoneMarkers.put("stormzone", stormZoneCircle);
        markerSet.put("storm_zone_circle", stormZoneCircle);

        // Create Safe Zone circle (green)
        Marker safeZoneCircle = ExtrudeMarker.builder()
                .label("Safe Zone (Low Storm Frequency)")
                .shape(Shape.createCircle(centerX, centerZ, zoneManager.getSafeZoneRadius(), 64), 64, 80)
                .centerPosition()
                .lineColor(new Color(0, 255, 0, 0.8f))
                .fillColor(new Color(0, 255, 0, 0.05f))
                .lineWidth(2)
                .build();

        zoneMarkers.put("safezone", safeZoneCircle);
        markerSet.put("safe_zone_circle", safeZoneCircle);

        plugin.getLogger().info("Created BlueMap zone circles");
    }

    /**
     * Removes zone circle markers from the map.
     */
    private void removeZoneCircles() {
        if (markerSet == null) return;

        markerSet.remove("stormlands_circle");
        markerSet.remove("storm_zone_circle");
        markerSet.remove("safe_zone_circle");
        zoneMarkers.clear();
    }

    /**
     * Updates or creates a marker for an active traveling storm.
     */
    public void updateStormMarker(TravelingStorm storm) {
        if (!enabled || storm == null || markerSet == null) {
            return;
        }

        Location loc = storm.getCurrentLocation();

        // Remove old markers
        removeStormMarker();

        // Create storm center marker (POI)
        POIMarker stormCenterMarker = POIMarker.builder()
                .position(loc.getX(), loc.getY(), loc.getZ())
                .label(String.format(
                    "%s Storm - %ds remaining - %.1f HP/s",
                    storm.getProfile().getType().name(),
                    storm.getRemainingSeconds(),
                    storm.getCurrentDamagePerSecond()
                ))
                .icon("assets/poi.svg", 16, 16)
                .build();

        stormMarkers.put("center", stormCenterMarker);
        markerSet.put("active_storm_center", stormCenterMarker);

        // Create storm damage radius circle (purple)
        // Get damage radius from config (default 50.0)
        double damageRadius = plugin.getConfigManager() != null
            ? plugin.getConfigManager().getStormDamageRadius()
            : 50.0;

        Marker stormCircle = ExtrudeMarker.builder()
                .label(String.format("%s Storm Radius", storm.getProfile().getType().name()))
                .shape(Shape.createCircle(loc.getX(), loc.getZ(), damageRadius, 32), (float) loc.getY(), (float) loc.getY() + 10)
                .centerPosition()
                .lineColor(new Color(128, 0, 128, 1.0f))
                .fillColor(new Color(128, 0, 128, 0.2f))
                .lineWidth(3)
                .build();

        stormMarkers.put("radius", stormCircle);
        markerSet.put("active_storm_radius", stormCircle);
    }

    /**
     * Removes the active storm marker from the map.
     */
    public void removeStormMarker() {
        if (markerSet == null) return;

        markerSet.remove("active_storm_center");
        markerSet.remove("active_storm_radius");
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
     * Cleans up all BlueMap markers.
     */
    public void shutdown() {
        if (!enabled) {
            return;
        }

        removeZoneCircles();
        removeStormMarker();

        // Remove marker set
        if (blueMapAPI != null && markerSet != null) {
            for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
                blueMapAPI.getWorld(Bukkit.getWorld(worldName)).ifPresent(world -> {
                    for (BlueMapMap map : world.getMaps()) {
                        map.getMarkerSets().remove("stormcraft");
                    }
                });
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
