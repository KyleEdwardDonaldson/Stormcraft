package dev.ked.stormcraft.api.events;

import dev.ked.stormcraft.model.StormProfile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a storm countdown is about to begin.
 * This event is cancellable.
 */
public class StormcraftPreStormEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;

    private final StormProfile profile;
    private final int countdownSeconds;

    public StormcraftPreStormEvent(StormProfile profile, int countdownSeconds) {
        this.profile = profile;
        this.countdownSeconds = countdownSeconds;
    }

    public StormProfile getProfile() {
        return profile;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}