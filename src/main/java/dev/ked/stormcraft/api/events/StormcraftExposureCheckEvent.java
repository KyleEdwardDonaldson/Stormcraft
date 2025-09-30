package dev.ked.stormcraft.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when checking if a player is exposed to a storm.
 * Other plugins can modify the exposure status or damage amount.
 */
public class StormcraftExposureCheckEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private boolean exposed;
    private double damageAmount;

    public StormcraftExposureCheckEvent(Player player, boolean exposed, double damageAmount) {
        this.player = player;
        this.exposed = exposed;
        this.damageAmount = damageAmount;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    public double getDamageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(double damageAmount) {
        this.damageAmount = damageAmount;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}