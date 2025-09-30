package dev.ked.stormcraft.api.events;

import dev.ked.stormcraft.model.ActiveStorm;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a storm reaches landfall and becomes active.
 */
public class StormcraftStormStartEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final ActiveStorm activeStorm;

    public StormcraftStormStartEvent(ActiveStorm activeStorm) {
        this.activeStorm = activeStorm;
    }

    public ActiveStorm getActiveStorm() {
        return activeStorm;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}