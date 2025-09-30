package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.model.TravelingStorm;

/**
 * Manages all map integration plugins (squaremap only).
 * Delegates storm marker updates to enabled map integration.
 */
public class MapIntegrationManager {
    private final SquaremapIntegration squaremapIntegration;

    public MapIntegrationManager(SquaremapIntegration squaremap) {
        this.squaremapIntegration = squaremap;
    }

    /**
     * Updates storm marker on squaremap.
     */
    public void updateStormMarker(TravelingStorm storm) {
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.updateStormMarker(storm);
        }
    }

    /**
     * Removes storm marker from squaremap.
     */
    public void removeStormMarker() {
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.removeStormMarker();
        }
    }

    /**
     * Reloads zones on squaremap.
     */
    public void reloadZones() {
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.reloadZones();
        }
    }

    /**
     * Checks if squaremap integration is enabled.
     */
    public boolean isAnyEnabled() {
        return (squaremapIntegration != null && squaremapIntegration.isEnabled());
    }
}
