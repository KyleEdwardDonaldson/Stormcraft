package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.api.events.StormcraftEssenceAwardEvent;
import dev.ked.stormcraft.api.events.StormcraftExposureCheckEvent;
import dev.ked.stormcraft.api.events.StormcraftStormTickEvent;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.exposure.PlayerExposureUtil;
import dev.ked.stormcraft.integration.WorldGuardIntegration;
import dev.ked.stormcraft.model.ActiveStorm;
import dev.ked.stormcraft.model.PlayerStormExposure;
import dev.ked.stormcraft.model.StormProfile;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.zones.ZoneManager;
import dev.ked.stormcraft.zones.ZoneSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles periodic exposure checks and damage application during active storms.
 */
public class DamageTask extends BukkitRunnable {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final PlayerExposureUtil exposureUtil;
    private final WorldGuardIntegration worldGuardIntegration;
    private final ZoneManager zoneManager;
    private final Random random = new Random();

    private ActiveStorm activeStorm;
    private TravelingStorm travelingStorm;
    private List<TravelingStorm> activeStorms = new ArrayList<>();

    // Player exposure tracking
    private final Map<UUID, PlayerStormExposure> playerExposure = new HashMap<>();

    // Performance optimization: track tick count for mob damage checks
    private int tickCounter = 0;

    public DamageTask(StormcraftPlugin plugin, ConfigManager config,
                     PlayerExposureUtil exposureUtil, WorldGuardIntegration worldGuardIntegration,
                     ZoneManager zoneManager) {
        this.plugin = plugin;
        this.config = config;
        this.exposureUtil = exposureUtil;
        this.worldGuardIntegration = worldGuardIntegration;
        this.zoneManager = zoneManager;
    }

    public void setActiveStorm(ActiveStorm activeStorm) {
        this.activeStorm = activeStorm;
    }

    public void setTravelingStorm(TravelingStorm travelingStorm) {
        this.travelingStorm = travelingStorm;
    }

    public void setActiveStorms(List<TravelingStorm> storms) {
        this.activeStorms = storms;
    }

    /**
     * Grants storm immunity to a player (e.g., after defeating a boss).
     * @param player The player to grant immunity to
     * @param durationSeconds Duration of immunity in seconds
     * @param source Source of immunity (for display)
     */
    public void grantStormImmunity(Player player, int durationSeconds, String source) {
        UUID playerId = player.getUniqueId();
        PlayerStormExposure exposure = playerExposure.computeIfAbsent(playerId, PlayerStormExposure::new);
        exposure.grantImmunity(durationSeconds, source);

        player.sendMessage(net.kyori.adventure.text.Component.text(
            "⛡ You have been granted " + durationSeconds + " seconds of storm immunity!",
            net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /**
     * Checks if a player has storm immunity.
     */
    public boolean hasStormImmunity(Player player) {
        PlayerStormExposure exposure = playerExposure.get(player.getUniqueId());
        return exposure != null && exposure.hasImmunity();
    }

    /**
     * Clears all exposure data for a player (used on quit/death).
     */
    public void clearPlayerExposure(UUID playerId) {
        playerExposure.remove(playerId);
    }

    /**
     * Gets the storm status component for a player (immunity or exposure).
     * Returns null if player has no special status.
     */
    public net.kyori.adventure.text.Component getStormStatusForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerStormExposure exposure = playerExposure.get(playerId);

        if (exposure == null) {
            return null;
        }

        // Show immunity status if active
        if (exposure.hasImmunity()) {
            return net.kyori.adventure.text.Component.text(
                "⛡ Immunity: " + exposure.getRemainingImmunitySeconds() + "s",
                net.kyori.adventure.text.format.NamedTextColor.GREEN);
        }

        // Show exposure status during grace period ramp-up
        if (exposure.isInStorm()) {
            double gracePeriodSeconds = config.getStormGracePeriodSeconds();
            double damageMultiplier = exposure.getDamageMultiplier(gracePeriodSeconds);

            if (damageMultiplier < 1.0) {
                int percentage = (int)(damageMultiplier * 100);
                return net.kyori.adventure.text.Component.text(
                    "⚠ Exposure: " + percentage + "%",
                    percentage < 50 ? net.kyori.adventure.text.format.NamedTextColor.YELLOW :
                    net.kyori.adventure.text.format.NamedTextColor.GOLD);
            }
        }

        return null;
    }

    @Override
    public void run() {
        tickCounter++;

        // Multi-storm system (erratic spawning)
        if (!activeStorms.isEmpty()) {
            runMultiStormCheck();
            return;
        }

        // Support both ActiveStorm (old) and TravelingStorm (new) systems
        if (activeStorm == null && travelingStorm == null) {
            return;
        }

        StormProfile profile = (activeStorm != null) ? activeStorm.getProfile() : travelingStorm.getProfile();
        // Use getCurrentDamagePerSecond for traveling storms (includes ramp-up)
        double actualDamage = (activeStorm != null) ? activeStorm.getActualDamagePerSecond() : travelingStorm.getCurrentDamagePerSecond();
        List<Player> exposedPlayers = new ArrayList<>();

        // Check all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean exposed = isPlayerExposedToStorm(player);

            // Debug logging
            if (config.isLogExposureSamples()) {
                plugin.getLogger().info(String.format("Player %s: exposed=%s, world=%s, gamemode=%s",
                    player.getName(), exposed, player.getWorld().getName(), player.getGameMode()));
            }

            if (exposed) {
                exposedPlayers.add(player);

                // Update exposure tracking
                UUID playerId = player.getUniqueId();
                PlayerStormExposure exposure = playerExposure.computeIfAbsent(playerId, PlayerStormExposure::new);
                exposure.enterStorm();
            } else {
                // Player not in storm - update tracking if they were previously
                UUID playerId = player.getUniqueId();
                PlayerStormExposure exposure = playerExposure.get(playerId);
                if (exposure != null && exposure.isInStorm()) {
                    exposure.leaveStorm();
                }
            }
        }

        // Fire tick event (use activeStorm if available, for backwards compatibility)
        if (activeStorm != null) {
            StormcraftStormTickEvent tickEvent = new StormcraftStormTickEvent(activeStorm, exposedPlayers);
            Bukkit.getPluginManager().callEvent(tickEvent);
        }

        // Apply damage and effects to exposed players
        for (Player player : exposedPlayers) {
            applyStormEffects(player, profile, actualDamage);

            // Award essence for being exposed (if economy enabled)
            awardEssence(player, profile);
        }

        // Apply damage to exposed mobs (less frequently than players for performance)
        int mobCheckInterval = config.getMobDamageCheckInterval();
        if (tickCounter % mobCheckInterval == 0) {
            Location stormLoc = (activeStorm != null) ?
                new Location(Bukkit.getWorld(config.getEnabledWorlds().get(0)), 0, 64, 0) :
                travelingStorm.getCurrentLocation();

            checkMobsNearStorm(stormLoc, profile, actualDamage);
        }

        // Log exposure samples if enabled
        if (config.isLogExposureSamples() && !exposedPlayers.isEmpty()) {
            plugin.getLogger().info("Exposed players (" + exposedPlayers.size() + "): " +
                    String.join(", ", exposedPlayers.stream().map(Player::getName).toList()));
        }
    }

    /**
     * Handles damage checks for multiple simultaneous storms.
     * DPS from multiple overlapping storms stacks together.
     */
    private void runMultiStormCheck() {
        List<Player> exposedPlayers = new ArrayList<>();
        Map<Player, Double> playerTotalDamage = new HashMap<>();
        Map<Player, StormProfile> playerStrongestProfile = new HashMap<>();

        // Check all online players against all active storms
        for (Player player : Bukkit.getOnlinePlayers()) {
            double totalDamage = 0;
            StormProfile strongestProfile = null;
            double maxDamage = 0;

            // Check player against each storm
            for (TravelingStorm storm : activeStorms) {
                if (isPlayerExposedToMultiStorm(player, storm)) {
                    double stormDamage = storm.getCurrentDamagePerSecond();
                    totalDamage += stormDamage;

                    // Track strongest storm for effects/essence
                    if (stormDamage > maxDamage) {
                        maxDamage = stormDamage;
                        strongestProfile = storm.getProfile();
                    }
                }
            }

            // Apply stacked damage if exposed to any storms
            if (totalDamage > 0 && strongestProfile != null) {
                exposedPlayers.add(player);
                playerTotalDamage.put(player, totalDamage);
                playerStrongestProfile.put(player, strongestProfile);
            }
        }

        // Apply damage and effects to exposed players
        for (Player player : exposedPlayers) {
            double totalDamage = playerTotalDamage.get(player);
            StormProfile profile = playerStrongestProfile.get(player);

            applyStormEffects(player, profile, totalDamage);
            awardEssence(player, profile);
        }

        // Apply damage to exposed mobs (less frequently for performance)
        int mobCheckInterval = config.getMobDamageCheckInterval();
        if (tickCounter % mobCheckInterval == 0) {
            checkMobsNearMultiStorms();
        }

        // Log exposure samples if enabled
        if (config.isLogExposureSamples() && !exposedPlayers.isEmpty()) {
            plugin.getLogger().info("Exposed players (" + exposedPlayers.size() + "): " +
                    String.join(", ", exposedPlayers.stream().map(Player::getName).toList()));
        }
    }

    /**
     * Finds the closest storm to a player.
     */
    private TravelingStorm findClosestStormToPlayer(Player player) {
        TravelingStorm closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (TravelingStorm storm : activeStorms) {
            Location stormLoc = storm.getCurrentLocation();
            if (stormLoc.getWorld().equals(player.getWorld())) {
                double distance = player.getLocation().distance(stormLoc);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = storm;
                }
            }
        }

        return closest;
    }

    /**
     * Finds the closest storm to a location.
     */
    private TravelingStorm findClosestStormToLocation(Location location) {
        TravelingStorm closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (TravelingStorm storm : activeStorms) {
            Location stormLoc = storm.getCurrentLocation();
            if (stormLoc.getWorld().equals(location.getWorld())) {
                double distance = location.distance(stormLoc);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = storm;
                }
            }
        }

        return closest;
    }

    /**
     * Checks if a player is exposed to a specific storm in multi-storm mode.
     */
    private boolean isPlayerExposedToMultiStorm(Player player, TravelingStorm storm) {
        Location playerLoc = player.getLocation();

        // Check if player is within storm radius (use storm's current radius)
        if (!storm.isLocationInStorm(playerLoc, storm.getCurrentRadius())) {
            return false;
        }

        // Basic exposure check (sky access)
        boolean exposed = exposureUtil.isPlayerExposed(player);
        if (!exposed) {
            return false;
        }

        // Check WorldGuard protection if enabled
        if (worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
            if (worldGuardIntegration.isInProtectedRegion(playerLoc)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if an entity is exposed to a specific storm in multi-storm mode.
     */
    private boolean isEntityExposedToMultiStorm(LivingEntity entity, TravelingStorm storm) {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        // Check if in enabled world
        if (world == null || !config.getEnabledWorlds().contains(world.getName())) {
            return false;
        }

        // Check if entity is within storm radius (use storm's current radius)
        if (!storm.isLocationInStorm(loc, storm.getCurrentRadius())) {
            return false;
        }

        // Quick check: compare entity Y to highest block Y
        int highestBlockY = world.getHighestBlockYAt(loc);
        int entityY = loc.getBlockY();
        int minDepth = config.getIgnoreIfUnderBlocksMinDepth();

        // If entity is below the highest block by at least minDepth, check if they have overhead protection
        if (highestBlockY - entityY >= minDepth) {
            return !hasBlocksOverheadEntity(entity, minDepth);
        }

        // Entity is at or near surface level = exposed
        return true;
    }

    /**
     * Checks if a player is exposed to the storm, including WorldGuard region checks.
     */
    private boolean isPlayerExposedToStorm(Player player) {
        // If using traveling storm, check if player is within storm's current radius
        if (travelingStorm != null && config.isTravelingStormsEnabled()) {
            Location playerLoc = player.getLocation();
            if (!travelingStorm.isLocationInStorm(playerLoc, travelingStorm.getCurrentRadius())) {
                return false; // Player not in storm radius
            }
        }

        // Basic exposure check (sky access)
        boolean exposed = exposureUtil.isPlayerExposed(player);

        if (!exposed) {
            return false;
        }

        // Check WorldGuard protection if enabled
        if (worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
            if (worldGuardIntegration.isInProtectedRegion(player.getLocation())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Applies damage and effects to an exposed player.
     */
    private void applyStormEffects(Player player, StormProfile profile, double actualDamagePerSecond) {
        // Safety check: Don't damage dead or offline players
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0) {
            return;
        }

        int checkInterval = config.getExposureCheckIntervalTicks();
        double checkIntervalSeconds = checkInterval / 20.0;

        // Get player exposure state
        UUID playerId = player.getUniqueId();
        PlayerStormExposure exposure = playerExposure.computeIfAbsent(playerId, PlayerStormExposure::new);

        // Check for immunity
        if (exposure.hasImmunity()) {
            return; // No damage during immunity (status shown via StormTracker)
        }

        // Update accumulated exposure time
        exposure.updateExposure(checkIntervalSeconds);

        // Get grace period from config (default 15 seconds)
        double gracePeriodSeconds = config.getStormGracePeriodSeconds();

        // Calculate damage with grace period multiplier
        double damageMultiplier = exposure.getDamageMultiplier(gracePeriodSeconds);
        double damageAmount = actualDamagePerSecond * checkIntervalSeconds * damageMultiplier;

        // Fire exposure check event (allows other plugins to modify)
        StormcraftExposureCheckEvent exposureEvent = new StormcraftExposureCheckEvent(player, true, damageAmount);
        Bukkit.getPluginManager().callEvent(exposureEvent);

        if (!exposureEvent.isExposed()) {
            return; // Another plugin marked player as not exposed
        }

        damageAmount = exposureEvent.getDamageAmount();

        // Apply damage
        if (damageAmount > 0) {
            double currentHealth = player.getHealth();
            double newHealth = Math.max(0, currentHealth - damageAmount);

            // Debug logging
            if (config.isLogExposureSamples()) {
                plugin.getLogger().info(String.format("Damaging %s: %.2f damage (%.0f%% multiplier), %.1f -> %.1f health",
                    player.getName(), damageAmount, damageMultiplier * 100, currentHealth, newHealth));
            }

            player.setHealth(newHealth);
            exposure.recordDamage();
        } else if (config.isLogExposureSamples()) {
            plugin.getLogger().info(String.format("No damage for %s: damageAmount=%.2f, multiplier=%.0f%%",
                player.getName(), damageAmount, damageMultiplier * 100));
        }

        // Blindness effect removed - too disruptive for gameplay

        // Apply slowness
        if (profile.getSlownessAmplifier() >= 0) {
            int duration = checkInterval + 10;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration,
                    profile.getSlownessAmplifier(), false, false, false));
        }

        // Lightning strike chance
        if (profile.getLightningStrikeChance() > 0) {
            if (random.nextDouble() < profile.getLightningStrikeChance()) {
                strikeLightningNearPlayer(player);
            }
        }
    }

    /**
     * Optimized mob damage check - only checks mobs within storm's damage radius.
     * Called less frequently than player checks for performance.
     */
    private void checkMobsNearStorm(Location stormLoc, StormProfile profile, double damage) {
        if (stormLoc == null || stormLoc.getWorld() == null) {
            return;
        }

        World world = stormLoc.getWorld();
        // Use current storm damage radius (or traveling storm radius if available)
        double damageRadius = (travelingStorm != null) ?
            travelingStorm.getCurrentRadius() :
            config.getStormDamageRadius();
        double damageRadiusSquared = damageRadius * damageRadius;

        // Only check mobs within storm's actual damage radius
        world.getLivingEntities().stream()
            .filter(entity -> !(entity instanceof Player))
            .filter(entity -> {
                Location loc = entity.getLocation();
                double dx = loc.getX() - stormLoc.getX();
                double dz = loc.getZ() - stormLoc.getZ();
                return (dx * dx + dz * dz) <= damageRadiusSquared;
            })
            .filter(this::isEntityExposedToStorm)
            .forEach(entity -> applyMobDamage(entity, damage));
    }

    /**
     * Optimized mob damage check for multiple storms.
     * Only checks mobs within the actual damage radius of storms.
     */
    private void checkMobsNearMultiStorms() {
        for (String worldName : config.getEnabledWorlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            // Only check storms in this world
            List<TravelingStorm> worldStorms = activeStorms.stream()
                .filter(s -> s.getCurrentLocation().getWorld().equals(world))
                .toList();

            if (worldStorms.isEmpty()) continue;

            // Get all mobs in this world
            world.getLivingEntities().stream()
                .filter(entity -> !(entity instanceof Player))
                .forEach(entity -> {
                    Location entityLoc = entity.getLocation();
                    double totalDamage = 0;

                    // Check entity against each storm in this world
                    for (TravelingStorm storm : worldStorms) {
                        Location stormLoc = storm.getCurrentLocation();
                        double damageRadius = storm.getCurrentRadius();
                        double damageRadiusSquared = damageRadius * damageRadius;

                        // Only check if entity is within storm's actual damage radius
                        double dx = entityLoc.getX() - stormLoc.getX();
                        double dz = entityLoc.getZ() - stormLoc.getZ();
                        double distSquared = dx * dx + dz * dz;

                        if (distSquared <= damageRadiusSquared) {
                            if (isEntityExposedToMultiStorm(entity, storm)) {
                                totalDamage += storm.getCurrentDamagePerSecond();
                            }
                        }
                    }

                    if (totalDamage > 0) {
                        applyMobDamage(entity, totalDamage);
                    }
                });
        }
    }

    /**
     * Checks if a mob/entity is exposed to the storm.
     * Only requires overhead block cover for protection.
     */
    private boolean isEntityExposedToStorm(LivingEntity entity) {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        // Check if in enabled world
        if (world == null || !config.getEnabledWorlds().contains(world.getName())) {
            return false;
        }

        // If using traveling storm, check if entity is within storm's current radius
        if (travelingStorm != null && config.isTravelingStormsEnabled()) {
            if (!travelingStorm.isLocationInStorm(loc, travelingStorm.getCurrentRadius())) {
                return false; // Entity not in storm radius
            }
        }

        // Quick check: compare entity Y to highest block Y
        int highestBlockY = world.getHighestBlockYAt(loc);
        int entityY = loc.getBlockY();
        int minDepth = config.getIgnoreIfUnderBlocksMinDepth();

        // If entity is below the highest block by at least minDepth, check if they have overhead protection
        if (highestBlockY - entityY >= minDepth) {
            // Only need overhead blocks for protection
            return !hasBlocksOverheadEntity(entity, minDepth);
        }

        // Entity is at or near surface level = exposed
        return true;
    }

    /**
     * Checks if there are solid blocks overhead for an entity.
     */
    private boolean hasBlocksOverheadEntity(LivingEntity entity, int minDepth) {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        if (world == null) {
            return false;
        }

        int solidBlockCount = 0;
        int maxHeight = world.getMaxHeight();

        // Raycast upward from entity location
        for (int y = loc.getBlockY() + 1; y < maxHeight; y++) {
            org.bukkit.block.Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            org.bukkit.Material material = block.getType();

            if (material.isAir()) {
                continue;
            }

            // Count solid blocks (simplified - could use isCoverMaterial if needed)
            if (material.isSolid()) {
                solidBlockCount++;
                if (solidBlockCount >= minDepth) {
                    return true;
                }
            }
        }

        return solidBlockCount >= minDepth;
    }

    /**
     * Checks if there is at least one solid block adjacent to the entity.
     */
    private boolean hasAdjacentWallEntity(LivingEntity entity) {
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        if (world == null) {
            return false;
        }

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Check all 8 horizontal neighbors at entity's feet level
        int[][] neighbors = {
            {-1, 0}, {1, 0},   // West, East
            {0, -1}, {0, 1},   // North, South
            {-1, -1}, {-1, 1}, // NW, SW
            {1, -1}, {1, 1}    // NE, SE
        };

        for (int[] offset : neighbors) {
            org.bukkit.block.Block block = world.getBlockAt(x + offset[0], y, z + offset[1]);
            org.bukkit.Material material = block.getType();

            // Check if this is a solid wall block
            if (!material.isAir() && material.isSolid()) {
                return true; // Found at least one wall
            }
        }

        return false; // No adjacent walls found
    }

    /**
     * Applies damage to exposed mobs.
     */
    private void applyMobDamage(LivingEntity entity, double actualDamagePerSecond) {
        int checkInterval = config.getExposureCheckIntervalTicks();
        double damageAmount = actualDamagePerSecond * (checkInterval / 20.0);

        if (damageAmount > 0) {
            double newHealth = Math.max(0, entity.getHealth() - damageAmount);
            entity.setHealth(newHealth);
        }
    }

    /**
     * Fires essence award event for exposed players.
     * Note: Stormcraft no longer directly awards essence.
     * The Stormcraft-Essence plugin listens for this event and handles the actual deposit.
     */
    private void awardEssence(Player player, StormProfile profile) {
        if (!config.isEconomyEnabled()) {
            return;
        }

        // Calculate base essence based on rate and storm type multiplier
        double baseEssence = config.getEssencePerTick();
        double multiplier = config.getEssenceMultipliers().getOrDefault(profile.getType(), 1.0);

        // Apply zone multiplier if zones enabled
        if (zoneManager != null && zoneManager.isEnabled()) {
            ZoneSettings zoneSettings = zoneManager.getSettingsAt(player.getLocation());
            multiplier *= zoneSettings.getEssenceMultiplier();
        }

        double essence = baseEssence * multiplier;

        // Fire event - let handlers (Stormcraft-Essence) deposit the essence
        int checkInterval = config.getExposureCheckIntervalTicks();
        StormcraftEssenceAwardEvent essenceEvent = new StormcraftEssenceAwardEvent(
                player, essence, profile.getType(), checkInterval, player.getLocation()
        );
        Bukkit.getPluginManager().callEvent(essenceEvent);
    }

    /**
     * Strikes lightning near (but not directly on) a player.
     */
    private void strikeLightningNearPlayer(Player player) {
        Location loc = player.getLocation();

        // Offset by 3-7 blocks in random direction
        double offsetX = (random.nextDouble() * 4 + 3) * (random.nextBoolean() ? 1 : -1);
        double offsetZ = (random.nextDouble() * 4 + 3) * (random.nextBoolean() ? 1 : -1);

        Location strikeLoc = loc.clone().add(offsetX, 0, offsetZ);

        // Strike lightning (visual + damage)
        loc.getWorld().strikeLightning(strikeLoc);
    }
}