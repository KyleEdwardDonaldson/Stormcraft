package dev.ked.stormcraft.resources;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages "storm drops" - valuable items that spawn during storms in the Stormlands.
 * Provides resource rewards for braving the dangerous zones.
 */
public class StormDropsManager extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final ZoneManager zoneManager;
    private final Random random = new Random();

    private TravelingStorm activeStorm;
    private List<TravelingStorm> activeStorms = new ArrayList<>();

    // Loot tables for different storm types
    private static final Map<Material, Integer> COMMON_DROPS = new HashMap<>();
    private static final Map<Material, Integer> RARE_DROPS = new HashMap<>();
    private static final Map<Material, Integer> EPIC_DROPS = new HashMap<>();

    static {
        // Common drops (weighted)
        COMMON_DROPS.put(Material.IRON_INGOT, 50);
        COMMON_DROPS.put(Material.GOLD_INGOT, 30);
        COMMON_DROPS.put(Material.COAL, 40);
        COMMON_DROPS.put(Material.COPPER_INGOT, 40);
        COMMON_DROPS.put(Material.REDSTONE, 35);
        COMMON_DROPS.put(Material.LAPIS_LAZULI, 25);

        // Rare drops
        RARE_DROPS.put(Material.DIAMOND, 20);
        RARE_DROPS.put(Material.EMERALD, 15);
        RARE_DROPS.put(Material.AMETHYST_SHARD, 25);
        RARE_DROPS.put(Material.QUARTZ, 30);
        RARE_DROPS.put(Material.GLOWSTONE_DUST, 20);
        RARE_DROPS.put(Material.ENDER_PEARL, 10);

        // Epic drops
        EPIC_DROPS.put(Material.NETHERITE_SCRAP, 5);
        EPIC_DROPS.put(Material.ANCIENT_DEBRIS, 3);
        EPIC_DROPS.put(Material.DIAMOND_BLOCK, 8);
        EPIC_DROPS.put(Material.EMERALD_BLOCK, 6);
        EPIC_DROPS.put(Material.NETHER_STAR, 1);
        EPIC_DROPS.put(Material.TOTEM_OF_UNDYING, 2);
        EPIC_DROPS.put(Material.ELYTRA, 1);
    }

    public StormDropsManager(StormcraftPlugin plugin, ConfigManager config, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
    }

    public void setActiveStorm(TravelingStorm storm) {
        this.activeStorm = storm;
    }

    public void setActiveStorms(List<TravelingStorm> storms) {
        this.activeStorms = storms;
    }

    @Override
    public void run() {
        // Multi-storm system
        if (!activeStorms.isEmpty()) {
            runMultiStormDrops();
            return;
        }

        // Single storm system (legacy)
        if (activeStorm == null || !config.isStormDropsEnabled()) {
            return;
        }

        Location stormCenter = activeStorm.getCurrentLocation();
        World world = stormCenter.getWorld();

        if (world == null) {
            return;
        }

        // Only spawn drops in Stormlands or Storm Zone
        ZoneManager.ZoneType zone = zoneManager.getZoneAt(stormCenter);
        if (zone == ZoneManager.ZoneType.SAFE_ZONE) {
            return;
        }

        // Determine drop chance based on zone
        double dropChance = switch (zone) {
            case STORMLANDS -> config.getStormDropChanceStormlands();
            case STORM_ZONE -> config.getStormDropChanceStormZone();
            default -> 0.0;
        };

        // Roll for drop
        if (random.nextDouble() >= dropChance) {
            return;
        }

        // Spawn a drop at a random location within the storm
        Location dropLocation = getRandomLocationInStorm(stormCenter, config.getStormDamageRadius());

        // Determine drop rarity based on zone and storm type
        ItemStack drop = selectRandomDrop(zone, activeStorm.getProfile().getType());

        if (drop != null) {
            spawnDrop(dropLocation, drop);

            if (config.isLogScheduling()) {
                plugin.getLogger().info("Storm dropped " + drop.getType() + " x" + drop.getAmount() +
                                      " at (" + (int)dropLocation.getX() + ", " +
                                      (int)dropLocation.getY() + ", " + (int)dropLocation.getZ() + ")");
            }
        }
    }

    /**
     * Handles storm drops for multiple simultaneous storms.
     */
    private void runMultiStormDrops() {
        if (!config.isStormDropsEnabled()) {
            return;
        }

        // Check each storm for potential drops
        for (TravelingStorm storm : activeStorms) {
            Location stormCenter = storm.getCurrentLocation();
            World world = stormCenter.getWorld();

            if (world == null) {
                continue;
            }

            // Only spawn drops in Stormlands or Storm Zone
            ZoneManager.ZoneType zone = zoneManager.getZoneAt(stormCenter);
            if (zone == ZoneManager.ZoneType.SAFE_ZONE) {
                continue;
            }

            // Determine drop chance based on zone
            double dropChance = switch (zone) {
                case STORMLANDS -> config.getStormDropChanceStormlands();
                case STORM_ZONE -> config.getStormDropChanceStormZone();
                default -> 0.0;
            };

            // Roll for drop
            if (random.nextDouble() >= dropChance) {
                continue;
            }

            // Spawn a drop at a random location within the storm
            Location dropLocation = getRandomLocationInStorm(stormCenter, config.getStormDamageRadius());

            // Determine drop rarity based on zone and storm type
            ItemStack drop = selectRandomDrop(zone, storm.getProfile().getType());

            if (drop != null) {
                spawnDrop(dropLocation, drop);

                if (config.isLogScheduling()) {
                    plugin.getLogger().info("Storm dropped " + drop.getType() + " x" + drop.getAmount() +
                                          " at (" + (int)dropLocation.getX() + ", " +
                                          (int)dropLocation.getY() + ", " + (int)dropLocation.getZ() + ")");
                }
            }
        }
    }

    /**
     * Gets a random location within the storm radius.
     */
    private Location getRandomLocationInStorm(Location center, double radius) {
        World world = center.getWorld();

        // Random angle and distance
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * radius;

        double x = center.getX() + (Math.cos(angle) * distance);
        double z = center.getZ() + (Math.sin(angle) * distance);

        // Find highest solid block at this XZ
        int y = world.getHighestBlockYAt((int)x, (int)z);

        return new Location(world, x, y + 1, z);
    }

    /**
     * Selects a random drop based on zone and storm type.
     */
    private ItemStack selectRandomDrop(ZoneManager.ZoneType zone, dev.ked.stormcraft.model.StormType stormType) {
        // Determine rarity weights based on zone and storm type
        double commonWeight = 70;
        double rareWeight = 25;
        double epicWeight = 5;

        // Stormlands has better loot
        if (zone == ZoneManager.ZoneType.STORMLANDS) {
            commonWeight = 50;
            rareWeight = 35;
            epicWeight = 15;
        }

        // Dangerous storms have better loot
        if (stormType == dev.ked.stormcraft.model.StormType.LONG_DANGEROUS) {
            rareWeight *= 1.5;
            epicWeight *= 2.0;
            commonWeight *= 0.7;
        }

        // Roll for rarity
        double totalWeight = commonWeight + rareWeight + epicWeight;
        double roll = random.nextDouble() * totalWeight;

        Map<Material, Integer> lootTable;
        if (roll < epicWeight) {
            lootTable = EPIC_DROPS;
        } else if (roll < epicWeight + rareWeight) {
            lootTable = RARE_DROPS;
        } else {
            lootTable = COMMON_DROPS;
        }

        // Select random item from loot table
        Material material = selectWeightedRandom(lootTable);
        if (material == null) {
            return null;
        }

        // Determine stack size
        int amount = 1;
        if (lootTable == COMMON_DROPS) {
            amount = random.nextInt(4) + 1; // 1-4
        } else if (lootTable == RARE_DROPS) {
            amount = random.nextInt(2) + 1; // 1-2
        }

        return new ItemStack(material, amount);
    }

    /**
     * Selects a random item from a weighted map.
     */
    private Material selectWeightedRandom(Map<Material, Integer> weightedMap) {
        int totalWeight = weightedMap.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(totalWeight);

        int cumulative = 0;
        for (Map.Entry<Material, Integer> entry : weightedMap.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Spawns a drop in the world.
     */
    private void spawnDrop(Location location, ItemStack item) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Item droppedItem = world.dropItemNaturally(location, item);
        droppedItem.setPickupDelay(20); // 1 second delay
        droppedItem.setGlowing(true); // Make it glow so players can see it
    }
}