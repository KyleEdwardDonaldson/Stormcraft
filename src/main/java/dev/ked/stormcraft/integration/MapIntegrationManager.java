package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.model.TravelingStorm;

/**
 * Manages all map integration plugins (Dynmap, squaremap, BlueMap).
 * Delegates storm marker updates to all enabled map integrations.
 */
public class MapIntegrationManager {
    private final DynmapIntegration dynmapIntegration;
    private final SquaremapIntegration squaremapIntegration;
    private final BluemapIntegration bluemapIntegration;

    public MapIntegrationManager(DynmapIntegration dynmap, SquaremapIntegration squaremap, BluemapIntegration bluemap) {
        this.dynmapIntegration = dynmap;
        this.squaremapIntegration = squaremap;
        this.bluemapIntegration = bluemap;
    }

    /**
     * Updates storm marker on all enabled map integrations.
     */
    public void updateStormMarker(TravelingStorm storm) {
        if (dynmapIntegration != null && dynmapIntegration.isEnabled()) {
            dynmapIntegration.updateStormMarker(storm);
        }
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.updateStormMarker(storm);
        }
        if (bluemapIntegration != null && bluemapIntegration.isEnabled()) {
            bluemapIntegration.updateStormMarker(storm);
        }
    }

    /**
     * Removes storm marker from all enabled map integrations.
     */
    public void removeStormMarker() {
        if (dynmapIntegration != null && dynmapIntegration.isEnabled()) {
            dynmapIntegration.removeStormMarker();
        }
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.removeStormMarker();
        }
        if (bluemapIntegration != null && bluemapIntegration.isEnabled()) {
            bluemapIntegration.removeStormMarker();
        }
    }

    /**
     * Reloads zones on all enabled map integrations.
     */
    public void reloadZones() {
        if (dynmapIntegration != null && dynmapIntegration.isEnabled()) {
            dynmapIntegration.reloadZones();
        }
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.reloadZones();
        }
        if (bluemapIntegration != null && bluemapIntegration.isEnabled()) {
            bluemapIntegration.reloadZones();
        }
    }

    /**
     * Checks if any map integration is enabled.
     */
    public boolean isAnyEnabled() {
        return (dynmapIntegration != null && dynmapIntegration.isEnabled()) ||
               (squaremapIntegration != null && squaremapIntegration.isEnabled()) ||
               (bluemapIntegration != null && bluemapIntegration.isEnabled());
    }
}
