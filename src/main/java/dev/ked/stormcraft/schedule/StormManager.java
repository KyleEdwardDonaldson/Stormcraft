package dev.ked.stormcraft.schedule;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.api.events.StormcraftPreStormEvent;
import dev.ked.stormcraft.api.events.StormcraftStormEndEvent;
import dev.ked.stormcraft.api.events.StormcraftStormStartEvent;
import dev.ked.stormcraft.config.ConfigManager;
import dev.ked.stormcraft.exposure.PlayerExposureUtil;
import dev.ked.stormcraft.integration.DynmapIntegration;
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
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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
    private final DynmapIntegration dynmapIntegration;
    private final Random random = new Random();

    // Storm state
    private StormPhase currentPhase = StormPhase.IDLE;
    private long nextStormTimeMillis = 0;
    private ActiveStorm activeStorm = null;
    private TravelingStorm travelingStorm = null;
    private StormProfile upcomingProfile = null;

    // Tasks
    private BukkitTask scheduleCheckTask;
    private CountdownTask countdownTask;
    private DamageTask damageTask;
    private TravelingStormManager travelingStormManager;
    private BlockDamageTask blockDamageTask;
    private StormDropsManager stormDropsManager;
    private OreGenerationManager oreGenerationManager;
    private BukkitTask stormDurationTask;
    private dev.ked.stormcraft.ui.StormTracker stormTracker;

    public StormManager(StormcraftPlugin plugin, ConfigManager config,
                       PlayerExposureUtil exposureUtil, WorldGuardIntegration worldGuardIntegration,
                       VaultIntegration vaultIntegration, ZoneManager zoneManager,
                       DynmapIntegration dynmapIntegration) {
        this.plugin = plugin;
        this.config = config;
        this.exposureUtil = exposureUtil;
        this.worldGuardIntegration = worldGuardIntegration;
        this.vaultIntegration = vaultIntegration;
        this.zoneManager = zoneManager;
        this.dynmapIntegration = dynmapIntegration;
    }

    public void start() {
        if (nextStormTimeMillis == 0) {
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
        if (currentPhase != StormPhase.IDLE) {
            return;
        }

        long now = System.currentTimeMillis();
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
            World world = Bukkit.getWorld(config.getEnabledWorlds().get(0));
            travelingStormManager = new TravelingStormManager(plugin, config, zoneManager, dynmapIntegration);
            travelingStormManager.startTravelingStorm(upcomingProfile, actualDuration, actualDamage, world, this::endStorm);
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
            stormTracker = new dev.ked.stormcraft.ui.StormTracker(plugin, config, zoneManager);
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
     */
    private void setStormWeather() {
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
     */
    private void clearStormWeather() {
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

    public enum StormPhase {
        IDLE, COUNTDOWN, ACTIVE
    }
}