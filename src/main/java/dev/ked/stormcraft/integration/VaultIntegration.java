package dev.ked.stormcraft.integration;

import dev.ked.stormcraft.StormcraftPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Integration with Vault economy API.
 */
public class VaultIntegration {
    private final StormcraftPlugin plugin;
    private Economy economy = null;
    private boolean enabled = false;

    public VaultIntegration(StormcraftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes Vault economy integration.
     * @return true if successfully initialized, false otherwise
     */
    public boolean initialize() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        enabled = economy != null;

        if (enabled) {
            plugin.getLogger().info("Vault economy integration enabled. Currency: " + economy.currencyNamePlural());
        }

        return enabled;
    }

    /**
     * Checks if Vault integration is enabled and working.
     */
    public boolean isEnabled() {
        return enabled && economy != null;
    }

    /**
     * Awards essence (currency) to a player.
     * @param player The player to award
     * @param amount The amount to award
     * @return true if successful, false otherwise
     */
    public boolean awardEssence(Player player, double amount) {
        if (!isEnabled() || amount <= 0) {
            return false;
        }

        try {
            economy.depositPlayer(player, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to award essence to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the player's current balance.
     */
    public double getBalance(Player player) {
        if (!isEnabled()) {
            return 0.0;
        }

        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Gets the currency name (singular).
     */
    public String getCurrencyName() {
        if (!isEnabled()) {
            return "Essence";
        }
        return economy.currencyNameSingular();
    }

    /**
     * Gets the currency name (plural).
     */
    public String getCurrencyNamePlural() {
        if (!isEnabled()) {
            return "Essence";
        }
        return economy.currencyNamePlural();
    }
}