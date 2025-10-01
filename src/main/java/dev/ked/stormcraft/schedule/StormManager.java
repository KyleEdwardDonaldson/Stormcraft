package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.api.events.StormcraftPreStormEvent;
import dev.ked.stormcraft.api.events.StormcraftStormEndEvent;
import dev.ked.stormcraft.api.events.StormcraftStormStartEvent;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.exposure.PlayerExposureUtil;
import dev.ked.stormcraft.integration.MapIntegrationManager;
import dev.ked.stormcraft.integration.VaultIntegration;
import dev.ked.stormcraft.integration.WorldGuardIntegration;
import dev.ked.stormcraft.model.ActiveStorm;
import dev.ked.stormcraft.model.TravelingStorm;
import dev.ked.stormcraft.resources.OreGenerationManager;
import dev.ked.stormcraft.resources.StormDropsManager;
import dev.ked.stormcraft.zones.ZoneManager;
import dev.ked.stormcraft.model.StormProfile;
import dev.ked.stormcraft.model.StormType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the storm lifecycle: scheduling, countdown, landfall, and clearing.
 */
public class StormManager {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final PlayerExposureUtil exposureUtil;
    private final WorldGuardIntegration worldGuardIntegration;
    private final VaultIntegration vaultIntegration;
    private final ZoneManager zoneManager;
    private final MapIntegrationManager mapIntegrationManager;
    private final Random random = new Random();

    // Storm state
    private StormPhase currentPhase = StormPhase.IDLE;
    private long nextStormTimeMillis = 0;
    private long nextBurstTimeMillis = 0; // For erratic spawning
    private ActiveStorm activeStorm = null;
    private TravelingStorm travelingStorm = null; // Legacy single storm support
    private List<TravelingStorm> activeStorms = new ArrayList<>(); // Multiple storms
    private StormProfile upcomingProfile = null;

    // Tasks
    private BukkitTask scheduleCheckTask;
    private CountdownTask countdownTask;
    private DamageTask damageTask;
    private TravelingStormManager travelingStormManager; // Legacy single storm
    private List<TravelingStormManager> activeStormManagers = new ArrayList<>(); // Multiple storms
    private BlockDamageTask blockDamageTask;
    private StormDropsManager stormDropsManager;
    private OreGenerationManager oreGenerationManager;
    private BukkitTask stormDurationTask;
    private dev.ked.stormcraft.ui.StormTracker stormTracker;

    public StormManager(StormcraftPlugin plugin, ConfigManager config,
                       PlayerExposureUtil exposureUtil, WorldGuardIntegration worldGuardIntegration,
                       VaultIntegration vaultIntegration, ZoneManager zoneManager,
                       MapIntegrationManager mapIntegrationManager) {
        this.plugin = plugin;
        this.config = config;
        this.exposureUtil = exposureUtil;
        this.worldGuardIntegration = worldGuardIntegration;
        this.vaultIntegration = vaultIntegration;
        this.zoneManager = zoneManager;
        this.mapIntegrationManager = mapIntegrationManager;
    }

    public void start() {
        // For erratic spawning mode, always seed initial storms on server start
        if (config.isErraticSpawningEnabled() && config.isTravelingStormsEnabled() && zoneManager.isEnabled()) {
            // Seed initial storm burst with varying lifetimes
            spawnStormBurst(true);
            scheduleNextBurst();
        } else if (nextStormTimeMillis == 0) {
            // Traditional storm system - only initialize if not loaded from data
            scheduleNextStorm();
        }

        // Start periodic check for storm scheduling (every 20 ticks = 1 second)
        scheduleCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkStormSchedule, 20L, 20L);

        // Start ore generation manager (passive ore spawning)
        if (config.isOreGenerationEnabled() && zoneManager.isEnabled()) {
            oreGenerationManager = new OreGenerationManager(plugin, config, zoneManager);
            oreGenerationManager.runTaskTimer(plugin, 100L, 100L); // Every 5 seconds
            plugin.getLogger().info("Ore generation enabled in Stormlands");
        }

        if (config.isLogScheduling()) {
            plugin.getLogger().info("StormManager started. Next storm in " + getSecondsUntilNextStorm() + "s");
        }
    }

    public void stop() {
        if (scheduleCheckTask != null) {
            scheduleCheckTask.cancel();
        }
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (damageTask != null) {
            damageTask.cancel();
        }
        if (travelingStormManager != null) {
            travelingStormManager.cancel();
        }
        if (blockDamageTask != null) {
            blockDamageTask.cancel();
        }
        if (stormDropsManager != null) {
            stormDropsManager.cancel();
        }
        if (oreGenerationManager != null) {
            oreGenerationManager.cancel();
        }
        if (stormDurationTask != null) {
            stormDurationTask.cancel();
        }
        clearStormWeather();
    }

    /**
     * Checks if it's time to start a storm.
     */
    private void checkStormSchedule() {
        long now = System.currentTimeMillis();

        // Check for erratic burst spawning
        if (config.isErraticSpawningEnabled() && config.isTravelingStormsEnabled() && zoneManager.isEnabled()) {
            if (now >= nextBurstTimeMillis) {
                spawnStormBurst(false);
                scheduleNextBurst();
            }
            return;
        }

        // Traditional single storm spawning
        if (currentPhase != StormPhase.IDLE) {
            return;
        }

        if (now >= nextStormTimeMillis) {
            startCountdown();
        }
    }

    /**
     * Schedules the next storm with a random delay.
     */
    public void scheduleNextStorm() {
        int minDelay = config.getMinDelayBetweenStormsSeconds();
        int maxDelay = config.getMaxDelayBetweenStormsSeconds();
        int delaySeconds = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);

        nextStormTimeMillis = System.currentTimeMillis() + (delaySeconds * 1000L);

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Next storm scheduled in " + delaySeconds + "s");
        }
    }

    /**
     * Schedules the next storm burst (for erratic spawning).
     */
    private void scheduleNextBurst() {
        int minDelay = config.getMinBurstDelaySeconds();
        int maxDelay = config.getMaxBurstDelaySeconds();
        int delaySeconds = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);

        nextBurstTimeMillis = System.currentTimeMillis() + (delaySeconds * 1000L);

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Next storm burst scheduled in " + delaySeconds + "s");
        }
    }

    /**
     * Selects a burst size using weighted random selection.
     */
    private int selectWeightedBurstSize() {
        Map<Integer, Double> weights = config.getBurstChanceWeights();
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * totalWeight;

        double cumulative = 0.0;
        for (Map.Entry<Integer, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (randomValue <= cumulative) {
                return entry.getKey();
            }
        }

        return config.getMinBurstSize(); // Fallback
    }

    /**
     * Forces the next storm timer.
     * @param seconds Seconds until next storm (or start countdown if 0)
     */
    public void forceNextStorm(int seconds) {
        if (seconds == 0) {
            nextStormTimeMillis = System.currentTimeMillis();
            checkStormSchedule();
        } else {
            nextStormTimeMillis = System.currentTimeMillis() + (seconds * 1000L);
        }
        plugin.getLogger().info("Next storm forced in " + seconds + "s");
    }

    /**
     * Starts the countdown phase.
     */
    private void startCountdown() {
        currentPhase = StormPhase.COUNTDOWN;
        upcomingProfile = selectRandomStormProfile();

        // Fire pre-storm event
        int countdownSeconds = config.getCountdownDurationSeconds();
        StormcraftPreStormEvent preEvent = new StormcraftPreStormEvent(upcomingProfile, countdownSeconds);
        Bukkit.getPluginManager().callEvent(preEvent);

        if (preEvent.isCancelled()) {
            if (config.isLogScheduling()) {
                plugin.getLogger().info("Storm countdown cancelled by another plugin.");
            }
            currentPhase = StormPhase.IDLE;
            scheduleNextStorm();
            return;
        }

        // Start countdown task (runs every second = 20 ticks)
        countdownTask = new CountdownTask(plugin, config, zoneManager, upcomingProfile, countdownSeconds, this::startStorm);
        countdownTask.runTaskTimer(plugin, 0L, 20L);

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Storm countdown started: " + countdownSeconds + "s");
        }
    }


    /**
     * Starts the active storm (landfall).
     */
    private void startStorm() {
        currentPhase = StormPhase.ACTIVE;

        // Randomize duration within profile's range
        int minDuration = upcomingProfile.getMinDurationSeconds();
        int maxDuration = upcomingProfile.getMaxDurationSeconds();
        int actualDuration = ThreadLocalRandom.current().nextInt(minDuration, maxDuration + 1);

        // Randomize damage within profile's range
        double minDamage = upcomingProfile.getMinDamagePerSecond();
        double maxDamage = upcomingProfile.getMaxDamagePerSecond();
        double actualDamage = minDamage + (ThreadLocalRandom.current().nextDouble() * (maxDamage - minDamage));

        // Determine if using traveling storm system
        boolean useTravelingStorm = config.isTravelingStormsEnabled() && zoneManager.isEnabled();

        if (useTravelingStorm) {
            // Use traveling storm system
            World world = null;
            if (!config.getEnabledWorlds().isEmpty()) {
                world = Bukkit.getWorld(config.getEnabledWorlds().get(0));
            }

            // Fallback to default world if configured world not found
            if (world == null) {
                world = Bukkit.getWorlds().get(0);
                plugin.getLogger().warning("Configured world not found for storm spawn. Using default world: " + world.getName());
            }

            travelingStormManager = new TravelingStormManager(plugin, config, zoneManager, mapIntegrationManager);
            travelingStormManager.startTravelingStorm(upcomingProfile, actualDuration, actualDamage, world, actualDuration, this::endStorm);
            travelingStorm = travelingStormManager.getActiveStorm();
        } else {
            // Use traditional stationary storm system
            activeStorm = new ActiveStorm(upcomingProfile, actualDuration, actualDamage);
            stormDurationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickStormDuration, 20L, 20L);

            // Fire storm start event
            StormcraftStormStartEvent startEvent = new StormcraftStormStartEvent(activeStorm);
            Bukkit.getPluginManager().callEvent(startEvent);
        }

        // Set weather to thunder in enabled worlds
        setStormWeather();

        // Announce landfall
        announceLandfall();

        // Start damage task
        int checkInterval = config.getExposureCheckIntervalTicks();
        damageTask = new DamageTask(plugin, config, exposureUtil, worldGuardIntegration, vaultIntegration, zoneManager);
        if (useTravelingStorm) {
            damageTask.setTravelingStorm(travelingStorm);
        } else {
            damageTask.setActiveStorm(activeStorm);
        }
        damageTask.runTaskTimer(plugin, checkInterval, checkInterval);

        // Start block damage task (only in Stormlands)
        if (config.isBlockDamageEnabled() && zoneManager.isEnabled()) {
            blockDamageTask = new BlockDamageTask(plugin, config, zoneManager, worldGuardIntegration);
            if (useTravelingStorm) {
                blockDamageTask.setActiveStorm(travelingStorm);
            }
            blockDamageTask.runTaskTimer(plugin, 100L, 100L); // Every 5 seconds
        }

        // Start storm drops manager
        if (config.isStormDropsEnabled() && zoneManager.isEnabled()) {
            stormDropsManager = new StormDropsManager(plugin, config, zoneManager);
            if (useTravelingStorm) {
                stormDropsManager.setActiveStorm(travelingStorm);
            }
            int dropInterval = config.getStormDropsCheckIntervalTicks();
            stormDropsManager.runTaskTimer(plugin, dropInterval, dropInterval);
        }

        // Start storm tracker (for traveling storms)
        if (useTravelingStorm) {
            stormTracker = new dev.ked.stormcraft.ui.StormTracker(plugin, config, zoneManager, plugin.getUIPreferences());
            stormTracker.setActiveStorm(travelingStorm);
            int trackerInterval = config.getStormTrackerUpdateInterval();
            stormTracker.runTaskTimer(plugin, 0L, trackerInterval);
        }

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Storm active: " + upcomingProfile.getType() + ", " +
                    actualDuration + "s" + (useTravelingStorm ? " (traveling)" : ""));
        }
    }

    /**
     * Spawns a burst of traveling storms (erratic spawning mode).
     * @param isInitialSeed If true, storms will have varying time remaining to simulate already-active storms
     */
    private void spawnStormBurst(boolean isInitialSeed) {
        int maxConcurrent = config.getMaxConcurrentStorms();

        // For initial seed, spawn ~70% of max concurrent storms
        int burstSize = isInitialSeed ? (int) (maxConcurrent * 0.7) : selectWeightedBurstSize();

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Spawning storm burst: isInitialSeed=" + isInitialSeed +
                    ", burstSize=" + burstSize + ", maxConcurrent=" + maxConcurrent);
        }

        int currentCount = activeStorms.size();

        // Limit burst size to not exceed max concurrent storms
        if (currentCount >= maxConcurrent) {
            if (config.isLogScheduling()) {
                plugin.getLogger().info("Skipping storm burst: Already at max concurrent storms (" + currentCount + "/" + maxConcurrent + ")");
            }
            return;
        }

        // Reduce burst size if it would exceed the limit
        int actualBurstSize = Math.min(burstSize, maxConcurrent - currentCount);

        // Get world with null check
        World world = null;
        if (!config.getEnabledWorlds().isEmpty()) {
            world = Bukkit.getWorld(config.getEnabledWorlds().get(0));
        }

        // Fallback to default world if configured world not found
        if (world == null) {
            world = Bukkit.getWorlds().get(0); // Default world
            plugin.getLogger().warning("Configured world not found for storm spawn. Using default world: " + world.getName());
        }

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Spawning storm burst: " + actualBurstSize + " storms (total: " + (currentCount + actualBurstSize) + "/" + maxConcurrent + ") in world: " + world.getName());
        }

        for (int i = 0; i < actualBurstSize; i++) {
            StormProfile profile = selectRandomStormProfile();

            // Randomize duration
            int minDuration = profile.getMinDurationSeconds();
            int maxDuration = profile.getMaxDurationSeconds();
            int actualDuration = ThreadLocalRandom.current().nextInt(minDuration, maxDuration + 1);

            // Randomize damage
            double minDamage = profile.getMinDamagePerSecond();
            double maxDamage = profile.getMaxDamagePerSecond();
            double actualDamage = minDamage + (ThreadLocalRandom.current().nextDouble() * (maxDamage - minDamage));

            // For initial seed, randomize remaining time to simulate storms already in progress
            int initialRemainingSeconds = actualDuration;
            if (isInitialSeed) {
                // Storms can be anywhere from 10% to 100% of their lifetime remaining
                double remainingPercent = 0.1 + (ThreadLocalRandom.current().nextDouble() * 0.9);
                initialRemainingSeconds = (int) (actualDuration * remainingPercent);

                if (config.isLogScheduling()) {
                    plugin.getLogger().info("Initial seed storm: duration=" + actualDuration +
                            "s, remaining=" + initialRemainingSeconds + "s (" +
                            String.format("%.0f", remainingPercent * 100) + "%), damage=" +
                            String.format("%.1f", actualDamage) + " HP/s");
                }
            }

            // Create individual storm manager
            TravelingStormManager manager = new TravelingStormManager(plugin, config, zoneManager, mapIntegrationManager);
            manager.startTravelingStorm(profile, actualDuration, actualDamage, world, initialRemainingSeconds, () -> onStormEnd(manager));
            activeStormManagers.add(manager);

            TravelingStorm storm = manager.getActiveStorm();
            if (storm != null) {
                activeStorms.add(storm);
            }
        }

        // Set weather and update tasks
        setStormWeather();
        startMultiStormTasks();
    }

    /**
     * Callback when an individual storm ends.
     */
    private void onStormEnd(TravelingStormManager endedManager) {
        activeStormManagers.remove(endedManager);
        if (endedManager.getActiveStorm() != null) {
            activeStorms.remove(endedManager.getActiveStorm());
        }

        // If all storms ended, clean up
        if (activeStormManagers.isEmpty()) {
            clearMultiStormTasks();
            clearStormWeather();

            if (config.isLogScheduling()) {
                plugin.getLogger().info("All storms in burst have ended");
            }
        }
    }

    /**
     * Starts tasks for multiple storms.
     */
    private void startMultiStormTasks() {
        // Only start once if not already running
        if (damageTask != null) {
            return;
        }

        // Start damage task (handles all storms)
        int checkInterval = config.getExposureCheckIntervalTicks();
        damageTask = new DamageTask(plugin, config, exposureUtil, worldGuardIntegration, vaultIntegration, zoneManager);
        damageTask.setActiveStorms(activeStorms);
        damageTask.runTaskTimer(plugin, checkInterval, checkInterval);

        // Start block damage task
        if (config.isBlockDamageEnabled() && zoneManager.isEnabled()) {
            blockDamageTask = new BlockDamageTask(plugin, config, zoneManager, worldGuardIntegration);
            blockDamageTask.setActiveStorms(activeStorms);
            blockDamageTask.runTaskTimer(plugin, 100L, 100L);
        }

        // Start storm drops
        if (config.isStormDropsEnabled() && zoneManager.isEnabled()) {
            stormDropsManager = new StormDropsManager(plugin, config, zoneManager);
            stormDropsManager.setActiveStorms(activeStorms);
            int dropInterval = config.getStormDropsCheckIntervalTicks();
            stormDropsManager.runTaskTimer(plugin, dropInterval, dropInterval);
        }

        // Start storm tracker (shows closest storm)
        stormTracker = new dev.ked.stormcraft.ui.StormTracker(plugin, config, zoneManager, plugin.getUIPreferences());
        stormTracker.setActiveStorms(activeStorms);
        int trackerInterval = config.getStormTrackerUpdateInterval();
        stormTracker.runTaskTimer(plugin, 0L, trackerInterval);
    }

    /**
     * Clears all multi-storm tasks.
     */
    private void clearMultiStormTasks() {
        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }
        if (blockDamageTask != null) {
            blockDamageTask.cancel();
            blockDamageTask = null;
        }
        if (stormDropsManager != null) {
            stormDropsManager.cancel();
            stormDropsManager = null;
        }
        if (stormTracker != null) {
            stormTracker.shutdown();
            stormTracker = null;
        }
    }

    /**
     * Ticks down the storm duration.
     */
    private void tickStormDuration() {
        if (activeStorm == null) {
            return;
        }

        activeStorm.decrementRemaining(1);

        if (activeStorm.isExpired()) {
            endStorm();
        }
    }

    /**
     * Ends the active storm and returns to idle phase.
     */
    public void endStorm() {
        if (activeStorm == null && travelingStorm == null) {
            return;
        }

        StormProfile profile = (activeStorm != null) ? activeStorm.getProfile() : travelingStorm.getProfile();
        int duration = (activeStorm != null) ? activeStorm.getOriginalDurationSeconds() : travelingStorm.getOriginalDurationSeconds();

        // Cancel all storm-related tasks
        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }
        if (stormDurationTask != null) {
            stormDurationTask.cancel();
            stormDurationTask = null;
        }
        if (travelingStormManager != null) {
            travelingStormManager.endStorm();
            travelingStormManager = null;
        }
        if (blockDamageTask != null) {
            blockDamageTask.cancel();
            blockDamageTask = null;
        }
        if (stormDropsManager != null) {
            stormDropsManager.cancel();
            stormDropsManager = null;
        }
        if (stormTracker != null) {
            stormTracker.shutdown();
            stormTracker = null;
        }

        // Clear weather
        clearStormWeather();

        // Fire storm end event
        StormcraftStormEndEvent endEvent = new StormcraftStormEndEvent(profile, duration);
        Bukkit.getPluginManager().callEvent(endEvent);

        // Reset state
        activeStorm = null;
        travelingStorm = null;
        upcomingProfile = null;
        currentPhase = StormPhase.IDLE;

        // Schedule next storm FIRST so we know the time
        scheduleNextStorm();

        // Announce clear with correct next storm time
        announceCleared();

        if (config.isLogScheduling()) {
            plugin.getLogger().info("Storm ended. Next storm scheduled.");
        }
    }

    /**
     * Forces a storm to start immediately.
     * @param type The storm type (null for random)
     * @param customDuration Custom duration in seconds (null for profile default)
     */
    public void forceStartStorm(StormType type, Integer customDuration) {
        // Stop any active storm or countdown
        if (currentPhase == StormPhase.ACTIVE) {
            endStorm();
        } else if (currentPhase == StormPhase.COUNTDOWN && countdownTask != null) {
            countdownTask.cancel();
        }

        // Select profile
        upcomingProfile = (type != null) ? config.getDamageProfiles().get(type) : selectRandomStormProfile();

        if (upcomingProfile == null) {
            plugin.getLogger().warning("Invalid storm type or profile not found.");
            return;
        }

        // Override duration if specified
        if (customDuration != null) {
            upcomingProfile = new StormProfile(
                    upcomingProfile.getType(),
                    customDuration,
                    customDuration,
                    upcomingProfile.getMinDamagePerSecond(),
                    upcomingProfile.getMaxDamagePerSecond(),
                    upcomingProfile.getMinMovementSpeed(),
                    upcomingProfile.getMaxMovementSpeed(),
                    upcomingProfile.getMinRadius(),
                    upcomingProfile.getMaxRadius(),
                    upcomingProfile.hasBlindness(),
                    upcomingProfile.getSlownessAmplifier(),
                    upcomingProfile.getLightningStrikeChance()
            );
        }

        currentPhase = StormPhase.IDLE; // Reset to idle so startStorm works correctly
        startStorm();
    }

    /**
     * Selects a random storm profile based on configured weights.
     */
    private StormProfile selectRandomStormProfile() {
        Map<StormType, Double> weights = config.getStormWeights();
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        double randomValue = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Map.Entry<StormType, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (randomValue <= cumulative) {
                return config.getDamageProfiles().get(entry.getKey());
            }
        }

        // Fallback to medium
        return config.getDamageProfiles().get(StormType.MEDIUM);
    }

    /**
     * Sets storm weather in all enabled worlds.
     * Skipped for traveling storms (localized weather only).
     */
    private void setStormWeather() {
        // Skip global weather for traveling storms
        if (config.isTravelingStormsEnabled() && zoneManager.isEnabled()) {
            return;
        }

        for (String worldName : config.getEnabledWorlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(Integer.MAX_VALUE);
            }
        }
    }

    /**
     * Clears storm weather in all enabled worlds.
     * Skipped for traveling storms (localized weather only).
     */
    private void clearStormWeather() {
        // Skip global weather for traveling storms
        if (config.isTravelingStormsEnabled() && zoneManager.isEnabled()) {
            return;
        }

        for (String worldName : config.getEnabledWorlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
            }
        }
    }

    private void announceLandfall() {
        // Skip global announcements if using traveling storms (tracker handles it)
        if (config.isTravelingStormsEnabled() && zoneManager.isEnabled()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dps", String.format("%.1f", upcomingProfile.getDamagePerSecond()));
        placeholders.put("duration", formatTime(upcomingProfile.getDurationSeconds()));

        Component message = config.formatMessage("landfall", placeholders);
        Bukkit.broadcast(message);
    }

    private void announceCleared() {
        // Skip global announcements if using traveling storms
        if (config.isTravelingStormsEnabled() && zoneManager.isEnabled()) {
            return;
        }

        long nextStormSeconds = getSecondsUntilNextStorm();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("next", formatTime((int) nextStormSeconds));

        Component message = config.formatMessage("cleared", placeholders);
        Bukkit.broadcast(message);
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

    private String formatStormType(String type) {
        return switch (type) {
            case "SHORT_WEAK" -> "Weak";
            case "MEDIUM" -> "Medium";
            case "LONG_DANGEROUS" -> "Dangerous";
            default -> type;
        };
    }

    // Getters for external access
    public boolean hasActiveStorm() {
        return activeStorm != null || travelingStorm != null;
    }

    public ActiveStorm getActiveStorm() {
        return activeStorm;
    }

    public TravelingStorm getTravelingStorm() {
        return travelingStorm;
    }

    public boolean isCountdownActive() {
        return currentPhase == StormPhase.COUNTDOWN;
    }

    public int getCountdownRemainingSeconds() {
        return (countdownTask != null) ? countdownTask.getRemainingSeconds() : 0;
    }

    public StormType getUpcomingStormType() {
        return (upcomingProfile != null) ? upcomingProfile.getType() : null;
    }

    public StormProfile getUpcomingProfile() {
        return upcomingProfile;
    }

    public long getSecondsUntilNextStorm() {
        long now = System.currentTimeMillis();
        return Math.max(0, (nextStormTimeMillis - now) / 1000);
    }

    public void setNextStormTimeMillis(long timeMillis) {
        this.nextStormTimeMillis = timeMillis;
    }

    public void restoreActiveStorm(ActiveStorm storm) {
        this.activeStorm = storm;
        this.upcomingProfile = storm.getProfile();
        this.currentPhase = StormPhase.ACTIVE;

        // Restart damage task
        int checkInterval = config.getExposureCheckIntervalTicks();
        damageTask = new DamageTask(plugin, config, exposureUtil, worldGuardIntegration, vaultIntegration, zoneManager);
        damageTask.setActiveStorm(activeStorm);
        damageTask.runTaskTimer(plugin, checkInterval, checkInterval);

        // Restart duration task
        stormDurationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickStormDuration, 20L, 20L);

        setStormWeather();
    }

    public void restoreCountdown(StormProfile profile, int remainingSeconds) {
        this.upcomingProfile = profile;
        this.currentPhase = StormPhase.COUNTDOWN;

        countdownTask = new CountdownTask(plugin, config, zoneManager, profile, remainingSeconds, this::startStorm);
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    public StormPhase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Gets the list of active storms (for multi-storm system).
     */
    public List<TravelingStorm> getActiveStorms() {
        return new ArrayList<>(activeStorms);
    }

    /**
     * Checks if there are any active storms (for multi-storm system).
     */
    public boolean hasActiveStorms() {
        return !activeStorms.isEmpty();
    }

    /**
     * Checks if a location is inside any active storm.
     */
    public boolean isLocationInAnyStorm(Location location) {
        for (TravelingStorm storm : activeStorms) {
            Location stormLoc = storm.getCurrentLocation();
            if (stormLoc.getWorld().equals(location.getWorld())) {
                double distance = location.distance(stormLoc);
                if (distance <= storm.getDamageRadius()) {
                    return true;
                }
            }
        }
        return false;
    }

    public enum StormPhase {
        IDLE, COUNTDOWN, ACTIVE
    }
}