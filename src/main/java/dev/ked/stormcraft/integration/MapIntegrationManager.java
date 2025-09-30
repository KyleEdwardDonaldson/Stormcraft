package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.model.TravelingStorm;

/**
 * Manages all map integration plugins (squaremap).
 * Delegates storm marker updates to all enabled map integrations.
 */
public class MapIntegrationManager {
    private final SquaremapIntegration squaremapIntegration;

    public MapIntegrationManager(SquaremapIntegration squaremap) {
        this.squaremapIntegration = squaremap;
    }

    /**
     * Updates storm marker on all enabled map integrations.
     */
    public void updateStormMarker(TravelingStorm storm) {
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.updateStormMarker(storm);
        }
    }

    /**
     * Removes storm marker from all enabled map integrations.
     */
    public void removeStormMarker() {
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.removeStormMarker();
        }
    }

    /**
     * Reloads zones on all enabled map integrations.
     */
    public void reloadZones() {
        if (squaremapIntegration != null && squaremapIntegration.isEnabled()) {
            squaremapIntegration.reloadZones();
        }
    }

    /**
     * Checks if any map integration is enabled.
     */
    public boolean isAnyEnabled() {
        return (squaremapIntegration != null && squaremapIntegration.isEnabled());
    }
}
