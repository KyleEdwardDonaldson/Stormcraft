package dev.ked.stormcraft.api.events;

import dev.ked.stormcraft.model.ActiveStorm;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * Called each exposure check interval during an active storm.
 * Contains the list of exposed players who will receive damage.
 */
public class StormcraftStormTickEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final ActiveStorm activeStorm;
    private final List<Player> exposedPlayers;

    public StormcraftStormTickEvent(ActiveStorm activeStorm, List<Player> exposedPlayers) {
        this.activeStorm = activeStorm;
        this.exposedPlayers = exposedPlayers;
    }

    public ActiveStorm getActiveStorm() {
        return activeStorm;
    }

    public List<Player> getExposedPlayers() {
        return exposedPlayers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}