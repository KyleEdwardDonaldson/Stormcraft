package dev.ked.stormcraft.api.events;

import dev.ked.stormcraft.model.StormProfile;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a storm ends and clears.
 */
public class StormcraftStormEndEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final StormProfile profile;
    private final int durationSeconds;

    public StormcraftStormEndEvent(StormProfile profile, int durationSeconds) {
        this.profile = profile;
        this.durationSeconds = durationSeconds;
    }

    public StormProfile getProfile() {
        return profile;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}