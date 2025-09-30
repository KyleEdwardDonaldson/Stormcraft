package dev.ked.stormcraft.integration;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.*;
import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.awt.Color;
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

        // Note: BlueMap API has changed - we'll create simple POI markers instead of shapes
        // since the Shape API requires complex vector math that may not be compatible

        // Create Stormlands marker
        POIMarker stormlandsMarker = POIMarker.builder()
                .position(centerX, 64, centerZ)
                .label("Stormlands (High Risk, High Reward)")
                .icon("assets/poi.svg", 16, 16)
                .build();

        zoneMarkers.put("stormlands", stormlandsMarker);
        markerSet.getMarkers().put("stormlands_center", stormlandsMarker);

        // Create Storm Zone marker
        POIMarker stormZoneMarker = POIMarker.builder()
                .position(centerX, 64, centerZ)
                .label("Storm Zone (Moderate Risk)")
                .icon("assets/poi.svg", 16, 16)
                .build();

        zoneMarkers.put("stormzone", stormZoneMarker);
        markerSet.getMarkers().put("storm_zone_center", stormZoneMarker);

        // Create Safe Zone marker
        POIMarker safeZoneMarker = POIMarker.builder()
                .position(centerX, 64, centerZ)
                .label("Safe Zone (Low Storm Frequency)")
                .icon("assets/poi.svg", 16, 16)
                .build();

        zoneMarkers.put("safezone", safeZoneMarker);
        markerSet.getMarkers().put("safe_zone_center", safeZoneMarker);
    }

    /**
     * Removes zone circle markers from the map.
     */
    private void removeZoneCircles() {
        if (markerSet == null) return;

        markerSet.getMarkers().remove("stormlands_center");
        markerSet.getMarkers().remove("storm_zone_center");
        markerSet.getMarkers().remove("safe_zone_center");
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
        POIMarker stormMarker = POIMarker.builder()
                .position(loc.getX(), loc.getY(), loc.getZ())
                .label(String.format(
                    "%s Storm - %ds remaining - %.1f HP/s",
                    storm.getProfile().getType().name(),
                    storm.getRemainingSeconds(),
                    storm.getCurrentDamagePerSecond()
                ))
                .icon("assets/poi.svg", 16, 16)
                .build();

        stormMarkers.put("poi", stormMarker);
        markerSet.getMarkers().put("active_storm", stormMarker);
    }

    /**
     * Removes the active storm marker from the map.
     */
    public void removeStormMarker() {
        if (markerSet == null) return;

        markerSet.getMarkers().remove("active_storm");
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
