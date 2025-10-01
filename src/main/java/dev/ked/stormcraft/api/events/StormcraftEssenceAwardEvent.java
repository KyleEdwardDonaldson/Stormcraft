package dev.ked.stormcraft.api.events;

import dev.ked.stormcraft.model.StormType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player is awarded essence (via Vault economy) for storm exposure.
 * Fired after damage is dealt and essence is deposited.
 *
 * This event is informational for tracking storm-earned essence separately from
 * other economy sources (e.g., for progression systems or statistics).
 */
public class StormcraftEssenceAwardEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final double essenceAmount;
    private final StormType stormType;
    private final long exposureTicks;

    public StormcraftEssenceAwardEvent(Player player, double essenceAmount, StormType stormType, long exposureTicks) {
        this.player = player;
        this.essenceAmount = essenceAmount;
        this.stormType = stormType;
        this.exposureTicks = exposureTicks;
    }

    /**
     * @return The player who earned essence
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The amount of essence awarded (already deposited via Vault)
     */
    public double getEssenceAmount() {
        return essenceAmount;
    }

    /**
     * @return The type of storm the player was exposed to
     */
    public StormType getStormType() {
        return stormType;
    }

    /**
     * @return How many ticks the player was exposed during this award cycle
     */
    public long getExposureTicks() {
        return exposureTicks;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
