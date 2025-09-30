package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.*;

/**
 * Integration with Dynmap to show zones and active storms on the map.
 */
public class DynmapIntegration {
    private final StormcraftPlugin plugin;
    private final ZoneManager zoneManager;

    private DynmapAPI dynmapAPI;
    private MarkerAPI markerAPI;
    private MarkerSet markerSet;

    private CircleMarker stormlandsCircle;
    private CircleMarker stormZoneCircle;
    private CircleMarker safeZoneCircle;

    private Marker activeStormMarker;
    private CircleMarker activeStormArea;

    private boolean enabled = false;

    public DynmapIntegration(StormcraftPlugin plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    /**
     * Initializes Dynmap integration if Dynmap is available.
     */
    public boolean initialize() {
        if (Bukkit.getPluginManager().getPlugin("Dynmap") == null) {
            return false;
        }

        try {
            dynmapAPI = (DynmapAPI) Bukkit.getPluginManager().getPlugin("Dynmap");
            if (dynmapAPI == null) {
                return false;
            }

            markerAPI = dynmapAPI.getMarkerAPI();
            if (markerAPI == null) {
                return false;
            }

            // Create marker set for Stormcraft
            markerSet = markerAPI.getMarkerSet("stormcraft");
            if (markerSet == null) {
                markerSet = markerAPI.createMarkerSet("stormcraft", "Stormcraft", null, false);
            }

            markerSet.setLabelShow(true);
            markerSet.setMinZoom(0);
            markerSet.setLayerPriority(10);

            enabled = true;

            // Initialize zone circles if zones are enabled
            if (zoneManager.isEnabled()) {
                createZoneCircles();
            }

            plugin.getLogger().info("Dynmap integration enabled.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize Dynmap integration: " + e.getMessage());
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

        String worldName = plugin.getConfigManager().getEnabledWorlds().get(0);
        double centerX = zoneManager.getCenterX();
        double centerZ = zoneManager.getCenterZ();

        // Remove existing circles if they exist
        removeZoneCircles();

        // Create Stormlands circle (innermost, red)
        stormlandsCircle = markerSet.createCircleMarker(
            "stormlands_zone",
            "Stormlands",
            false,
            worldName,
            centerX,
            64.0,
            centerZ,
            zoneManager.getStormlandsRadius(),
            zoneManager.getStormlandsRadius(),
            true
        );
        stormlandsCircle.setFillStyle(0.3, 0xFF4444); // Red with transparency
        stormlandsCircle.setLineStyle(2, 1.0, 0xFF0000); // Solid red border
        stormlandsCircle.setLabel("Stormlands (High Risk, High Reward)");

        // Create Storm Zone circle (middle ring, orange)
        stormZoneCircle = markerSet.createCircleMarker(
            "storm_zone",
            "Storm Zone",
            false,
            worldName,
            centerX,
            64.0,
            centerZ,
            zoneManager.getStormZoneRadius(),
            zoneManager.getStormZoneRadius(),
            true
        );
        stormZoneCircle.setFillStyle(0.2, 0xFFAA00); // Orange with transparency
        stormZoneCircle.setLineStyle(2, 1.0, 0xFF8800); // Solid orange border
        stormZoneCircle.setLabel("Storm Zone (Moderate Risk)");

        // Create Safe Zone circle (outermost, green)
        safeZoneCircle = markerSet.createCircleMarker(
            "safe_zone",
            "Safe Zone",
            false,
            worldName,
            centerX,
            64.0,
            centerZ,
            zoneManager.getSafeZoneRadius(),
            zoneManager.getSafeZoneRadius(),
            true
        );
        safeZoneCircle.setFillStyle(0.1, 0x44FF44); // Green with transparency
        safeZoneCircle.setLineStyle(2, 1.0, 0x00FF00); // Solid green border
        safeZoneCircle.setLabel("Safe Zone (Low Storm Frequency)");
    }

    /**
     * Removes zone circle markers from the map.
     */
    private void removeZoneCircles() {
        if (stormlandsCircle != null) {
            stormlandsCircle.deleteMarker();
            stormlandsCircle = null;
        }
        if (stormZoneCircle != null) {
            stormZoneCircle.deleteMarker();
            stormZoneCircle = null;
        }
        if (safeZoneCircle != null) {
            safeZoneCircle.deleteMarker();
            safeZoneCircle = null;
        }
    }

    /**
     * Updates or creates a marker for an active traveling storm.
     */
    public void updateStormMarker(TravelingStorm storm) {
        if (!enabled || storm == null) {
            return;
        }

        Location loc = storm.getCurrentLocation();
        String worldName = loc.getWorld().getName();

        // Remove old markers
        removeStormMarker();

        // Create storm center marker (icon)
        MarkerIcon icon = markerAPI.getMarkerIcon("warning"); // Use warning icon
        if (icon == null) {
            icon = markerAPI.getMarkerIcon("default");
        }

        activeStormMarker = markerSet.createMarker(
            "active_storm",
            "Active Storm",
            worldName,
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            icon,
            false
        );

        String stormInfo = String.format(
            "<b>%s Storm</b><br>" +
            "Remaining: %d seconds<br>" +
            "Moving toward Stormlands<br>" +
            "Damage: %.1f HP/s",
            storm.getProfile().getType().name(),
            storm.getRemainingSeconds(),
            storm.getActualDamagePerSecond()
        );
        activeStormMarker.setLabel(stormInfo);
        activeStormMarker.setDescription(stormInfo);

        // Create storm damage radius circle
        double damageRadius = plugin.getConfigManager().getStormDamageRadius();
        activeStormArea = markerSet.createCircleMarker(
            "active_storm_area",
            "Storm Damage Radius",
            false,
            worldName,
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            damageRadius,
            damageRadius,
            true
        );
        activeStormArea.setFillStyle(0.4, 0x8800FF); // Purple with transparency
        activeStormArea.setLineStyle(3, 1.0, 0xFF00FF); // Bright purple border
    }

    /**
     * Removes the active storm marker from the map.
     */
    public void removeStormMarker() {
        if (activeStormMarker != null) {
            activeStormMarker.deleteMarker();
            activeStormMarker = null;
        }
        if (activeStormArea != null) {
            activeStormArea.deleteMarker();
            activeStormArea = null;
        }
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
     * Cleans up all Dynmap markers.
     */
    public void shutdown() {
        if (!enabled) {
            return;
        }

        removeZoneCircles();
        removeStormMarker();

        if (markerSet != null) {
            markerSet.deleteMarkerSet();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}