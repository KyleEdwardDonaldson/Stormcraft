package dev.ked.stormcraft.command;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.model.ActiveStorm;
import dev.ked.stormcraft.model.StormType;
import dev.ked.stormcraft.schedule.StormManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command handler for /stormcraft and all subcommands.
 */
public class StormcraftCommand implements CommandExecutor, TabCompleter {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final StormManager stormManager;

    public StormcraftCommand(StormcraftPlugin plugin, ConfigManager config, StormManager stormManager) {
        this.plugin = plugin;
        this.config = config;
        this.stormManager = stormManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                            String label, String[] args) {
        if (args.length == 0) {
            // Show status
            showStatus(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "next" -> handleNext(sender, args);
            case "reload" -> handleReload(sender);
            case "testdamage" -> handleTestDamage(sender, args);
            case "weights" -> handleWeights(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use /stormcraft for help.")
                    .color(NamedTextColor.RED));
        }

        return true;
    }

    private void showStatus(CommandSender sender) {
        if (stormManager.hasActiveStorm()) {
            ActiveStorm storm = stormManager.getActiveStorm();
            Map<String, String> placeholders = Map.of(
                    "type", formatStormType(storm.getProfile().getType().name()),
                    "time", formatTime(storm.getRemainingSeconds()),
                    "dps", String.format("%.1f", storm.getProfile().getDamagePerSecond())
            );
            sender.sendMessage(config.formatMessage("status.active", placeholders));
        } else if (stormManager.isCountdownActive()) {
            Map<String, String> placeholders = Map.of(
                    "type", formatStormType(stormManager.getUpcomingStormType().name()),
                    "time", formatTime(stormManager.getCountdownRemainingSeconds()),
                    "dps", String.format("%.1f", stormManager.getUpcomingProfile().getDamagePerSecond())
            );
            sender.sendMessage(config.formatMessage("status.countdown", placeholders));
        } else {
            Map<String, String> placeholders = Map.of(
                    "next", formatTime((int) stormManager.getSecondsUntilNextStorm())
            );
            sender.sendMessage(config.formatMessage("status.idle", placeholders));
        }
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("stormcraft.admin.start")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED));
            return;
        }

        StormType type = null;
        Integer duration = null;

        // Parse arguments: /stormcraft start [type] [seconds]
        if (args.length >= 2) {
            String typeArg = args[1].toLowerCase();
            type = switch (typeArg) {
                case "short", "shortweak", "short_weak" -> StormType.SHORT_WEAK;
                case "medium" -> StormType.MEDIUM;
                case "long", "longdangerous", "long_dangerous" -> StormType.LONG_DANGEROUS;
                default -> null;
            };

            if (type == null) {
                sender.sendMessage(Component.text("Invalid storm type. Use: short, medium, long")
                        .color(NamedTextColor.RED));
                return;
            }
        }

        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid duration. Must be a number.")
                        .color(NamedTextColor.RED));
                return;
            }
        }

        stormManager.forceStartStorm(type, duration);
        sender.sendMessage(Component.text("Storm started!").color(NamedTextColor.GREEN));
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("stormcraft.admin.stop")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED));
            return;
        }

        if (!stormManager.hasActiveStorm()) {
            sender.sendMessage(Component.text("No active storm to stop.").color(NamedTextColor.YELLOW));
            return;
        }

        stormManager.endStorm();
        sender.sendMessage(Component.text("Storm stopped.").color(NamedTextColor.GREEN));
    }

    private void handleNext(CommandSender sender, String[] args) {
        if (!sender.hasPermission("stormcraft.admin.next")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /stormcraft next <seconds>").color(NamedTextColor.RED));
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            stormManager.forceNextStorm(seconds);
            sender.sendMessage(Component.text("Next storm set to " + seconds + " seconds.")
                    .color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("stormcraft.admin.reload")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED));
            return;
        }

        config.reload();
        sender.sendMessage(Component.text("Configuration reloaded.").color(NamedTextColor.GREEN));
    }

    private void handleTestDamage(CommandSender sender, String[] args) {
        if (!sender.hasPermission("stormcraft.admin.test")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                    .color(NamedTextColor.RED));
            return;
        }

        int duration = 5; // Default 5 seconds
        if (args.length >= 2) {
            try {
                duration = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid duration.").color(NamedTextColor.RED));
                return;
            }
        }

        // Apply test damage (2 HP per second for duration)
        final int testDuration = duration;
        sender.sendMessage(Component.text("Testing damage for " + testDuration + " seconds...")
                .color(NamedTextColor.YELLOW));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            double totalDamage = 2.0 * testDuration;
            double newHealth = Math.max(0, player.getHealth() - totalDamage);
            player.setHealth(newHealth);
            sender.sendMessage(Component.text("Test complete. Dealt " + totalDamage + " damage.")
                    .color(NamedTextColor.GREEN));
        }, testDuration * 20L);
    }

    private void handleWeights(CommandSender sender) {
        if (!sender.hasPermission("stormcraft.admin")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("=== Storm Weights ===").color(NamedTextColor.GOLD));
        Map<StormType, Double> weights = config.getStormWeights();
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        for (Map.Entry<StormType, Double> entry : weights.entrySet()) {
            double percentage = (entry.getValue() / total) * 100.0;
            sender.sendMessage(Component.text(formatStormType(entry.getKey().name()) + ": " +
                    String.format("%.1f%% (%.2f)", percentage, entry.getValue()))
                    .color(NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                     String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "next", "reload", "testdamage", "weights").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Arrays.asList("short", "medium", "long").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%dm %ds", minutes, secs);
        } else {
            return seconds + "s";
        }
    }

    private String formatStormType(String enumName) {
        return switch (enumName) {
            case "SHORT_WEAK" -> "Short Weak";
            case "MEDIUM" -> "Medium";
            case "LONG_DANGEROUS" -> "Long Dangerous";
            default -> enumName;
        };
    }
}