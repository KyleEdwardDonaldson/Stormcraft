package dev.ked.stormcraft.command;

import dev.ked.stormcraft.StormcraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

/**
 * Handles the /storm infuse command to place infusion pedestals.
 */
public class InfuseCommand implements CommandExecutor {
    private final StormcraftPlugin plugin;
    private final Economy economy;
    private final double essenceCost;

    public InfuseCommand(StormcraftPlugin plugin, Economy economy, double essenceCost) {
        this.plugin = plugin;
        this.economy = economy;
        this.essenceCost = essenceCost;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return true;
        }

        // Check if player has Stormcraft-Essence plugin
        if (!Bukkit.getPluginManager().isPluginEnabled("Stormcraft-Essence")) {
            player.sendMessage(Component.text("Stormcraft-Essence plugin not found!", NamedTextColor.RED));
            return true;
        }

        // Check balance
        if (!economy.has(player, essenceCost)) {
            player.sendMessage(Component.text("Insufficient essence! Need " + String.format("%.0f", essenceCost) +
                ", have " + String.format("%.0f", economy.getBalance(player)), NamedTextColor.RED));
            return true;
        }

        // Get block player is looking at
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            player.sendMessage(Component.text("Look at a block to place the infusion pedestal!", NamedTextColor.YELLOW));
            return true;
        }

        Block targetBlock = rayTrace.getHitBlock();
        Location pedestalLocation = targetBlock.getRelative(rayTrace.getHitBlockFace()).getLocation();

        // Check if block is air
        if (pedestalLocation.getBlock().getType() != Material.AIR) {
            player.sendMessage(Component.text("Cannot place pedestal here - block is not empty!", NamedTextColor.RED));
            return true;
        }

        // Deduct essence
        economy.withdrawPlayer(player, essenceCost);

        // Call Stormcraft-Essence API to place pedestal
        try {
            Object essencePlugin = Bukkit.getPluginManager().getPlugin("Stormcraft-Essence");
            if (essencePlugin != null) {
                // Use reflection to call the pedestal manager
                Object pedestalManager = essencePlugin.getClass().getMethod("getInfusionPedestalManager").invoke(essencePlugin);
                boolean success = (boolean) pedestalManager.getClass()
                    .getMethod("placePedestal", Player.class, Location.class)
                    .invoke(pedestalManager, player, pedestalLocation);

                if (success) {
                    player.sendMessage(Component.text("Infusion pedestal placed! Cost: " + String.format("%.0f", essenceCost) + " essence", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("Right-click the pedestal with armor to infuse it!", NamedTextColor.YELLOW));
                    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
                } else {
                    // Refund essence if placement failed
                    economy.depositPlayer(player, essenceCost);
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Error placing pedestal: " + e.getMessage(), NamedTextColor.RED));
            economy.depositPlayer(player, essenceCost); // Refund on error
            e.printStackTrace();
        }

        return true;
    }
}
