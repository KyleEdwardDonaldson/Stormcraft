package dev.ked.stormcraft.api.events;

import dev.ked.stormcraft.model.StormType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player should be awarded essence for storm exposure.
 * Fired BEFORE essence is deposited - handlers (like Stormcraft-Essence) are responsible
 * for actually awarding the essence via Vault.
 *
 * This event can be cancelled to prevent essence awards.
 * Stormcraft no longer directly deposits essence - it only fires this event.
 */
public class StormcraftEssenceAwardEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private double essenceAmount;
    private final StormType stormType;
    private final long exposureTicks;
    private final Location location;
    private boolean cancelled = false;

    public StormcraftEssenceAwardEvent(Player player, double essenceAmount, StormType stormType, long exposureTicks, Location location) {
        this.player = player;
        this.essenceAmount = essenceAmount;
        this.stormType = stormType;
        this.exposureTicks = exposureTicks;
        this.location = location;
    }

    // Backward compatibility constructor
    public StormcraftEssenceAwardEvent(Player player, double essenceAmount, StormType stormType, long exposureTicks) {
        this(player, essenceAmount, stormType, exposureTicks, player.getLocation());
    }

    /**
     * @return The player who earned essence
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The base amount of essence to award (before any handler modifications)
     */
    public double getEssenceAmount() {
        return essenceAmount;
    }

    /**
     * Sets the amount of essence to award.
     * Handlers can modify this value.
     */
    public void setEssenceAmount(double amount) {
        this.essenceAmount = amount;
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

    /**
     * @return The location where the player was exposed
     */
    public Location getLocation() {
        return location;
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
