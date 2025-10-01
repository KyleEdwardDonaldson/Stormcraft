package dev.ked.stormcraft.config;

import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.model.StormProfile;
import dev.ked.stormcraft.model.StormType;
import dev.ked.stormcraft.zones.ZoneSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ConfigManager {
    private final StormcraftPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private FileConfiguration config;
    private FileConfiguration messages;

    // Config values
    private List<String> enabledWorlds;
    private int minDelayBetweenStormsSeconds;
    private int maxDelayBetweenStormsSeconds;
    private int countdownDurationSeconds;
    private int gracePeriodSeconds;
    private List<Integer> countdownAnnounceScheduleSeconds;

    private Map<StormType, Double> stormWeights;
    private Map<StormType, StormProfile> damageProfiles;

    private int exposureCheckIntervalTicks;
    private boolean treatLeavesAsCover;
    private boolean treatGlassAsCover;
    private Set<GameMode> ignoreGameModes;
    private int ignoreIfUnderBlocksMinDepth;
    private boolean worldGuardProtection;

    private boolean logExposureSamples;
    private boolean logScheduling;
    private boolean bStats;

    // Economy
    private boolean economyEnabled;
    private double essencePerTick;
    private Map<StormType, Double> essenceMultipliers;

    // Zone System
    private boolean zoneSystemEnabled;
    private double zoneCenterX;
    private double zoneCenterZ;
    private double stormlandsRadius;
    private double stormZoneRadius;
    private double safeZoneRadius;
    private ZoneSettings stormlandsSettings;
    private ZoneSettings stormZoneSettings;
    private ZoneSettings safeZoneSettings;

    // Traveling Storm System
    private boolean travelingStormsEnabled;
    private double stormMovementSpeed;
    private double stormDamageRadius;

    // Erratic Spawning
    private boolean erraticSpawningEnabled;
    private int minBurstSize;
    private int maxBurstSize;
    private int maxConcurrentStorms;
    private int minBurstDelaySeconds;
    private int maxBurstDelaySeconds;
    private Map<Integer, Double> burstChanceWeights;

    // Spawn Location
    private boolean spawnAtBorder;
    private double borderBias;
    private double borderSpread;

    // Damage Ramp-Up
    private boolean damageRampUpEnabled;
    private int damageRampUpSeconds;

    // Block Damage
    private boolean blockDamageEnabled;
    private double blockDamageChance;
    private int blockDamageMaxChecksPerTick;

    // Storm Drops
    private boolean stormDropsEnabled;
    private double stormDropChanceStormlands;
    private double stormDropChanceStormZone;
    private int stormDropsCheckIntervalTicks;

    // Ore Generation
    private boolean oreGenerationEnabled;
    private double oreGenerationChance;
    private int oreGenerationChunksPerTick;
    private int oreGenerationAttemptsPerChunk;

    // Storm Tracker
    private String stormTrackerMode;
    private double stormTrackerRange;
    private int stormTrackerUpdateInterval;

    // Messages
    private String messagePrefix;
    private String messageCountdown;
    private String messageGrace;
    private String messageLandfall;
    private String messageTickWarn;
    private String messageCleared;
    private String messageStatusIdle;
    private String messageStatusCountdown;
    private String messageStatusActive;

    public ConfigManager(StormcraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // Save default configs if they don't exist
        plugin.saveDefaultConfig();
        saveResourceIfNotExists("messages.yml");

        config = plugin.getConfig();
        messages = loadMessagesFile();

        loadMainConfig();
        loadMessages();

        validateConfig();
    }

    private void loadMainConfig() {
        enabledWorlds = config.getStringList("enabledWorlds");
        if (enabledWorlds.isEmpty()) {
            enabledWorlds = List.of("world");
        }

        minDelayBetweenStormsSeconds = config.getInt("minDelayBetweenStormsSeconds", 900);
        maxDelayBetweenStormsSeconds = config.getInt("maxDelayBetweenStormsSeconds", 2400);
        countdownDurationSeconds = config.getInt("countdownDurationSeconds", 900);
        gracePeriodSeconds = config.getInt("gracePeriodSeconds", 180);
        countdownAnnounceScheduleSeconds = config.getIntegerList("countdownAnnounceScheduleSeconds");
        if (countdownAnnounceScheduleSeconds.isEmpty()) {
            countdownAnnounceScheduleSeconds = List.of(900, 600, 420, 300, 120, 60, 10);
        }

        // Load storm weights
        stormWeights = new HashMap<>();
        ConfigurationSection weightsSection = config.getConfigurationSection("stormWeights");
        if (weightsSection != null) {
            for (StormType type : StormType.values()) {
                double weight = weightsSection.getDouble(type.getConfigKey(), 0.0);
                stormWeights.put(type, weight);
            }
        } else {
            // Defaults
            stormWeights.put(StormType.SHORT_WEAK, 0.25);
            stormWeights.put(StormType.MEDIUM, 0.60);
            stormWeights.put(StormType.LONG_DANGEROUS, 0.15);
        }

        // Load damage profiles
        damageProfiles = new HashMap<>();
        ConfigurationSection profilesSection = config.getConfigurationSection("damageProfiles");
        if (profilesSection != null) {
            for (StormType type : StormType.values()) {
                ConfigurationSection typeSection = profilesSection.getConfigurationSection(type.getConfigKey());
                if (typeSection != null) {
                    int minDuration = typeSection.getInt("minDurationSeconds", 120);
                    int maxDuration = typeSection.getInt("maxDurationSeconds", 600);
                    double minDps = typeSection.getDouble("minDamagePerSecond", 1.0);
                    double maxDps = typeSection.getDouble("maxDamagePerSecond", 3.0);
                    double minSpeed = typeSection.getDouble("minMovementSpeed", 0.1);
                    double maxSpeed = typeSection.getDouble("maxMovementSpeed", 1.0);
                    double minRadius = typeSection.getDouble("minRadius", 500.0);
                    double maxRadius = typeSection.getDouble("maxRadius", 1500.0);

                    ConfigurationSection effectsSection = typeSection.getConfigurationSection("extraEffects");
                    boolean blindness = effectsSection != null && effectsSection.getBoolean("blindness", false);
                    int slowness = effectsSection != null ? effectsSection.getInt("slownessAmplifier", -1) : -1;
                    double lightning = effectsSection != null ? effectsSection.getDouble("lightningStrikeChance", 0.0) : 0.0;

                    damageProfiles.put(type, new StormProfile(type, minDuration, maxDuration, minDps, maxDps, minSpeed, maxSpeed, minRadius, maxRadius, blindness, slowness, lightning));
                }
            }
        }

        // Load exposure settings
        ConfigurationSection exposureSection = config.getConfigurationSection("exposure");
        if (exposureSection != null) {
            exposureCheckIntervalTicks = exposureSection.getInt("checkIntervalTicks", 20);
            treatLeavesAsCover = exposureSection.getBoolean("treatLeavesAsCover", true);
            treatGlassAsCover = exposureSection.getBoolean("treatGlassAsCover", true);
            ignoreIfUnderBlocksMinDepth = exposureSection.getInt("ignoreIfUnderBlocksMinDepth", 1);

            List<String> gameModeStrings = exposureSection.getStringList("ignoreGameModes");
            ignoreGameModes = new HashSet<>();
            for (String gmStr : gameModeStrings) {
                try {
                    ignoreGameModes.add(GameMode.valueOf(gmStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid game mode in config: " + gmStr);
                }
            }

            ConfigurationSection hookSection = exposureSection.getConfigurationSection("protectedRegionsHook");
            worldGuardProtection = hookSection != null && hookSection.getBoolean("worldGuard", true);
        }

        // Load economy settings
        ConfigurationSection economySection = config.getConfigurationSection("economy");
        if (economySection != null) {
            economyEnabled = economySection.getBoolean("enabled", true);
            essencePerTick = economySection.getDouble("essencePerTick", 0.1);

            essenceMultipliers = new HashMap<>();
            ConfigurationSection multipliersSection = economySection.getConfigurationSection("essenceMultipliers");
            if (multipliersSection != null) {
                for (StormType type : StormType.values()) {
                    double multiplier = multipliersSection.getDouble(type.getConfigKey(), 1.0);
                    essenceMultipliers.put(type, multiplier);
                }
            } else {
                // Defaults
                essenceMultipliers.put(StormType.SHORT_WEAK, 1.0);
                essenceMultipliers.put(StormType.MEDIUM, 2.0);
                essenceMultipliers.put(StormType.LONG_DANGEROUS, 4.0);
            }
        } else {
            economyEnabled = false;
            essencePerTick = 0.1;
            essenceMultipliers = new HashMap<>();
        }

        // Load zone system settings
        loadZoneSettings();

        // Load traveling storm settings
        ConfigurationSection travelingStormSection = config.getConfigurationSection("travelingStorms");
        if (travelingStormSection != null) {
            travelingStormsEnabled = travelingStormSection.getBoolean("enabled", true);
            stormMovementSpeed = travelingStormSection.getDouble("movementSpeed", 5.0);
            stormDamageRadius = travelingStormSection.getDouble("damageRadius", 50.0);

            // Load erratic spawning settings
            ConfigurationSection erraticSection = travelingStormSection.getConfigurationSection("erraticSpawning");
            if (erraticSection != null) {
                erraticSpawningEnabled = erraticSection.getBoolean("enabled", true);
                minBurstSize = erraticSection.getInt("minBurstSize", 1);
                maxBurstSize = erraticSection.getInt("maxBurstSize", 6);
                maxConcurrentStorms = erraticSection.getInt("maxConcurrentStorms", 12);
                minBurstDelaySeconds = erraticSection.getInt("minDelaySeconds", 180);
                maxBurstDelaySeconds = erraticSection.getInt("maxDelaySeconds", 900);

                // Load burst chance weights
                burstChanceWeights = new HashMap<>();
                ConfigurationSection burstWeightsSection = erraticSection.getConfigurationSection("burstChanceWeights");
                if (burstWeightsSection != null) {
                    for (String key : burstWeightsSection.getKeys(false)) {
                        int burstSize = Integer.parseInt(key);
                        double weight = burstWeightsSection.getDouble(key);
                        burstChanceWeights.put(burstSize, weight);
                    }
                }
            } else {
                erraticSpawningEnabled = false;
                minBurstSize = 1;
                maxBurstSize = 1;
                maxConcurrentStorms = 12;
                minBurstDelaySeconds = 900;
                maxBurstDelaySeconds = 2400;
                burstChanceWeights = new HashMap<>();
                burstChanceWeights.put(1, 1.0);
            }

            // Load spawn location settings
            ConfigurationSection spawnLocationSection = travelingStormSection.getConfigurationSection("spawnLocation");
            if (spawnLocationSection != null) {
                spawnAtBorder = spawnLocationSection.getBoolean("spawnAtBorder", true);
                borderBias = spawnLocationSection.getDouble("borderBias", 0.7);
                borderSpread = spawnLocationSection.getDouble("borderSpread", 500.0);
            } else {
                spawnAtBorder = false;
                borderBias = 0.7;
                borderSpread = 500.0;
            }

            // Load damage ramp-up settings
            ConfigurationSection rampUpSection = travelingStormSection.getConfigurationSection("damageRampUp");
            if (rampUpSection != null) {
                damageRampUpEnabled = rampUpSection.getBoolean("enabled", true);
                damageRampUpSeconds = rampUpSection.getInt("rampUpSeconds", 60);
            } else {
                damageRampUpEnabled = false;
                damageRampUpSeconds = 0;
            }
        } else {
            travelingStormsEnabled = true;
            stormMovementSpeed = 5.0;
            stormDamageRadius = 50.0;
            erraticSpawningEnabled = false;
            minBurstSize = 1;
            maxBurstSize = 1;
            minBurstDelaySeconds = 900;
            maxBurstDelaySeconds = 2400;
            burstChanceWeights = new HashMap<>();
            burstChanceWeights.put(1, 1.0);
            spawnAtBorder = false;
            borderBias = 0.7;
            borderSpread = 500.0;
            damageRampUpEnabled = false;
            damageRampUpSeconds = 0;
        }

        // Load block damage settings
        ConfigurationSection blockDamageSection = config.getConfigurationSection("blockDamage");
        if (blockDamageSection != null) {
            blockDamageEnabled = blockDamageSection.getBoolean("enabled", true);
            blockDamageChance = blockDamageSection.getDouble("baseChance", 0.01);
            blockDamageMaxChecksPerTick = blockDamageSection.getInt("maxChecksPerTick", 50);
        } else {
            blockDamageEnabled = true;
            blockDamageChance = 0.01;
            blockDamageMaxChecksPerTick = 50;
        }

        // Load storm drops settings
        ConfigurationSection stormDropsSection = config.getConfigurationSection("stormDrops");
        if (stormDropsSection != null) {
            stormDropsEnabled = stormDropsSection.getBoolean("enabled", true);
            stormDropChanceStormlands = stormDropsSection.getDouble("chanceStormlands", 0.1);
            stormDropChanceStormZone = stormDropsSection.getDouble("chanceStormZone", 0.05);
            stormDropsCheckIntervalTicks = stormDropsSection.getInt("checkIntervalTicks", 100);
        } else {
            stormDropsEnabled = true;
            stormDropChanceStormlands = 0.1;
            stormDropChanceStormZone = 0.05;
            stormDropsCheckIntervalTicks = 100;
        }

        // Load storm tracker settings
        ConfigurationSection stormTrackerSection = config.getConfigurationSection("stormTracker");
        if (stormTrackerSection != null) {
            stormTrackerMode = stormTrackerSection.getString("mode", "actionbar");
            stormTrackerRange = stormTrackerSection.getDouble("range", 500.0);
            stormTrackerUpdateInterval = stormTrackerSection.getInt("updateInterval", 20);
        } else {
            stormTrackerMode = "actionbar";
            stormTrackerRange = 500.0;
            stormTrackerUpdateInterval = 20;
        }

        // Load ore generation settings
        ConfigurationSection oreGenSection = config.getConfigurationSection("oreGeneration");
        if (oreGenSection != null) {
            oreGenerationEnabled = oreGenSection.getBoolean("enabled", true);
            oreGenerationChance = oreGenSection.getDouble("baseChance", 0.001);
            oreGenerationChunksPerTick = oreGenSection.getInt("chunksPerTick", 2);
            oreGenerationAttemptsPerChunk = oreGenSection.getInt("attemptsPerChunk", 3);
        } else {
            oreGenerationEnabled = true;
            oreGenerationChance = 0.001;
            oreGenerationChunksPerTick = 2;
            oreGenerationAttemptsPerChunk = 3;
        }

        // Load debug settings
        ConfigurationSection debugSection = config.getConfigurationSection("debug");
        if (debugSection != null) {
            logExposureSamples = debugSection.getBoolean("logExposureSamples", false);
            logScheduling = debugSection.getBoolean("logScheduling", false);
            bStats = debugSection.getBoolean("bStats", true);
        }
    }

    private void loadMessages() {
        messagePrefix = messages.getString("prefix", "<dark_gray>[<yellow>Stormcraft</yellow>]</dark_gray> ");
        messageCountdown = messages.getString("countdown", "<yellow>⚠</yellow> <gold>Storm Warning:</gold> <white>{time}</white> until arrival — Type: <aqua>{type}</aqua> (<gold>{dps} HP/s</gold>)");
        messageGrace = messages.getString("grace", "<red>⛈ STORM INCOMING</red> <white>{time}</white> — <yellow>Seek shelter now!</yellow>");
        messageLandfall = messages.getString("landfall", "<red><bold>⚡ STORM ACTIVE!</bold></red> <gold>{dps} HP/s</gold> for <white>{duration}</white> — <gray>Stay indoors!</gray>");
        messageTickWarn = messages.getString("tickWarn", "<gray>You feel the air crackle…</gray>");
        messageCleared = messages.getString("cleared", "<green>✓ Storm cleared.</green> Next storm in <white>{next}</white>.");

        ConfigurationSection statusSection = messages.getConfigurationSection("status");
        if (statusSection != null) {
            messageStatusIdle = statusSection.getString("idle", "Next storm in {next}.");
            messageStatusCountdown = statusSection.getString("countdown", "Storm in {time} — {type} — {dps} HP/s");
            messageStatusActive = statusSection.getString("active", "Storm active: {type} — {time} left — {dps} HP/s");
        }
    }

    private void loadZoneSettings() {
        ConfigurationSection zonesSection = config.getConfigurationSection("zones");
        if (zonesSection != null) {
            zoneSystemEnabled = zonesSection.getBoolean("enabled", false);
            zoneCenterX = zonesSection.getDouble("centerX", 0);
            zoneCenterZ = zonesSection.getDouble("centerZ", 0);

            // Load Stormlands settings
            ConfigurationSection stormlandsSection = zonesSection.getConfigurationSection("stormlands");
            stormlandsRadius = stormlandsSection != null ? stormlandsSection.getDouble("radius", 2500) : 2500;
            stormlandsSettings = loadZoneSettingsFromSection(stormlandsSection, 3.0, true, true);

            // Load Storm Zone settings
            ConfigurationSection stormZoneSection = zonesSection.getConfigurationSection("stormZone");
            stormZoneRadius = stormZoneSection != null ? stormZoneSection.getDouble("radius", 5000) : 5000;
            stormZoneSettings = loadZoneSettingsFromSection(stormZoneSection, 1.5, false, true);

            // Load Safe Zone settings
            ConfigurationSection safeZoneSection = zonesSection.getConfigurationSection("safeZone");
            safeZoneRadius = safeZoneSection != null ? safeZoneSection.getDouble("radius", 10000) : 10000;
            safeZoneSettings = loadZoneSettingsFromSection(safeZoneSection, 0.3, false, false);
        } else {
            zoneSystemEnabled = false;
            zoneCenterX = 0;
            zoneCenterZ = 0;
            stormlandsRadius = 2500;
            stormZoneRadius = 5000;
            safeZoneRadius = 10000;

            // Default settings
            Map<StormType, Double> defaultWeights = new HashMap<>(stormWeights);
            stormlandsSettings = new ZoneSettings(3.0, defaultWeights, 2.5, 3.0, true, 0.01, true, 0.1, null);
            stormZoneSettings = new ZoneSettings(1.5, defaultWeights, 1.5, 1.5, false, 0.0, true, 0.05, null);
            safeZoneSettings = new ZoneSettings(0.3, defaultWeights, 1.0, 1.0, false, 0.0, false, 0.0, null);
        }
    }

    private ZoneSettings loadZoneSettingsFromSection(ConfigurationSection section, double defaultFreq,
                                                     boolean defaultBlockDamage, boolean defaultStormDrops) {
        if (section == null) {
            return new ZoneSettings(defaultFreq, new HashMap<>(stormWeights), 1.0, 1.0,
                                  defaultBlockDamage, 0.01, defaultStormDrops, 0.05, null);
        }

        double stormFreqMult = section.getDouble("stormFrequencyMultiplier", defaultFreq);
        double resourceMult = section.getDouble("resourceMultiplier", 1.0);
        double essenceMult = section.getDouble("essenceMultiplier", 1.0);
        boolean blockDmg = section.getBoolean("blockDamageEnabled", defaultBlockDamage);
        double blockDmgChance = section.getDouble("blockDamageChance", 0.01);
        boolean stormDrops = section.getBoolean("stormDropsEnabled", defaultStormDrops);
        double stormDropChance = section.getDouble("stormDropChance", 0.05);

        // Load storm type weights for this zone
        Map<StormType, Double> zoneWeights = new HashMap<>();
        ConfigurationSection weightsSection = section.getConfigurationSection("stormTypeWeights");
        if (weightsSection != null) {
            for (StormType type : StormType.values()) {
                double weight = weightsSection.getDouble(type.getConfigKey(), stormWeights.getOrDefault(type, 0.0));
                zoneWeights.put(type, weight);
            }
        } else {
            zoneWeights = new HashMap<>(stormWeights);
        }

        // Load biome preferences for this zone
        Map<org.bukkit.block.Biome, Double> biomePrefs = new HashMap<>();
        ConfigurationSection biomeSection = section.getConfigurationSection("biomePreferences");
        if (biomeSection != null) {
            for (String biomeKey : biomeSection.getKeys(false)) {
                try {
                    org.bukkit.block.Biome biome = org.bukkit.block.Biome.valueOf(biomeKey);
                    double weight = biomeSection.getDouble(biomeKey, 1.0);
                    biomePrefs.put(biome, weight);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid biome name in config: " + biomeKey);
                }
            }
        }

        return new ZoneSettings(stormFreqMult, zoneWeights, resourceMult, essenceMult,
                              blockDmg, blockDmgChance, stormDrops, stormDropChance, biomePrefs);
    }

    private void validateConfig() {
        double totalWeight = stormWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(totalWeight - 1.0) > 0.1) {
            plugin.getLogger().warning("Storm weights sum to " + totalWeight + " (expected ~1.0). Weights will be normalized.");
        }

        if (minDelayBetweenStormsSeconds >= maxDelayBetweenStormsSeconds) {
            plugin.getLogger().warning("minDelayBetweenStormsSeconds should be less than maxDelayBetweenStormsSeconds!");
        }
    }

    private FileConfiguration loadMessagesFile() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        return YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfigs();
        plugin.getLogger().info("Configuration reloaded.");
    }

    // Getters
    public List<String> getEnabledWorlds() { return enabledWorlds; }
    public int getMinDelayBetweenStormsSeconds() { return minDelayBetweenStormsSeconds; }
    public int getMaxDelayBetweenStormsSeconds() { return maxDelayBetweenStormsSeconds; }
    public int getCountdownDurationSeconds() { return countdownDurationSeconds; }
    public int getGracePeriodSeconds() { return gracePeriodSeconds; }
    public List<Integer> getCountdownAnnounceScheduleSeconds() { return countdownAnnounceScheduleSeconds; }
    public Map<StormType, Double> getStormWeights() { return stormWeights; }
    public Map<StormType, StormProfile> getDamageProfiles() { return damageProfiles; }
    public int getExposureCheckIntervalTicks() { return exposureCheckIntervalTicks; }
    public boolean isTreatLeavesAsCover() { return treatLeavesAsCover; }
    public boolean isTreatGlassAsCover() { return treatGlassAsCover; }
    public Set<GameMode> getIgnoreGameModes() { return ignoreGameModes; }
    public int getIgnoreIfUnderBlocksMinDepth() { return ignoreIfUnderBlocksMinDepth; }
    public boolean isWorldGuardProtection() { return worldGuardProtection; }
    public boolean isCustomWelcomeEnabled() { return config.getBoolean("customWelcome.enabled", true); }
    public boolean isLogExposureSamples() { return logExposureSamples; }
    public boolean isLogScheduling() { return logScheduling; }
    public boolean isBStats() { return bStats; }
    public boolean isEconomyEnabled() { return economyEnabled; }
    public double getEssencePerTick() { return essencePerTick; }
    public Map<StormType, Double> getEssenceMultipliers() { return essenceMultipliers; }

    // Zone System getters
    public boolean isZoneSystemEnabled() { return zoneSystemEnabled; }
    public double getZoneCenterX() { return zoneCenterX; }
    public double getZoneCenterZ() { return zoneCenterZ; }
    public double getStormlandsRadius() { return stormlandsRadius; }
    public double getStormZoneRadius() { return stormZoneRadius; }
    public double getSafeZoneRadius() { return safeZoneRadius; }
    public ZoneSettings getStormlandsSettings() { return stormlandsSettings; }
    public ZoneSettings getStormZoneSettings() { return stormZoneSettings; }
    public ZoneSettings getSafeZoneSettings() { return safeZoneSettings; }

    // Traveling Storm getters
    public boolean isTravelingStormsEnabled() { return travelingStormsEnabled; }
    public double getStormMovementSpeed() { return stormMovementSpeed; }
    public double getStormDamageRadius() { return stormDamageRadius; }

    public boolean isErraticSpawningEnabled() { return erraticSpawningEnabled; }
    public int getMinBurstSize() { return minBurstSize; }
    public int getMaxBurstSize() { return maxBurstSize; }
    public int getMaxConcurrentStorms() { return maxConcurrentStorms; }
    public int getMinBurstDelaySeconds() { return minBurstDelaySeconds; }
    public int getMaxBurstDelaySeconds() { return maxBurstDelaySeconds; }
    public Map<Integer, Double> getBurstChanceWeights() { return burstChanceWeights; }

    public boolean isSpawnAtBorder() { return spawnAtBorder; }
    public double getBorderBias() { return borderBias; }
    public double getBorderSpread() { return borderSpread; }

    public boolean isDamageRampUpEnabled() { return damageRampUpEnabled; }
    public int getDamageRampUpSeconds() { return damageRampUpSeconds; }

    // Block Damage getters
    public boolean isBlockDamageEnabled() { return blockDamageEnabled; }
    public double getBlockDamageChance() { return blockDamageChance; }
    public int getBlockDamageMaxChecksPerTick() { return blockDamageMaxChecksPerTick; }

    // Storm Drops getters
    public boolean isStormDropsEnabled() { return stormDropsEnabled; }
    public double getStormDropChanceStormlands() { return stormDropChanceStormlands; }
    public double getStormDropChanceStormZone() { return stormDropChanceStormZone; }
    public int getStormDropsCheckIntervalTicks() { return stormDropsCheckIntervalTicks; }

    // Ore Generation getters
    public boolean isOreGenerationEnabled() { return oreGenerationEnabled; }
    public double getOreGenerationChance() { return oreGenerationChance; }
    public int getOreGenerationChunksPerTick() { return oreGenerationChunksPerTick; }
    public int getOreGenerationAttemptsPerChunk() { return oreGenerationAttemptsPerChunk; }

    public String getStormTrackerMode() { return stormTrackerMode; }
    public double getStormTrackerRange() { return stormTrackerRange; }
    public int getStormTrackerUpdateInterval() { return stormTrackerUpdateInterval; }

    // Performance settings
    public double getStormActiveRange() { return config.getDouble("performance.stormActiveRange", 2000.0); }
    public int getDormantUpdateInterval() { return config.getInt("performance.dormantUpdateInterval", 1200); }
    public int getActiveUpdateInterval() { return config.getInt("performance.activeUpdateInterval", 20); }
    public int getMobDamageCheckInterval() { return config.getInt("performance.mobDamageCheckInterval", 100); }

    // Message formatters
    public Component formatMessage(String messageKey, Map<String, String> placeholders) {
        String message = switch (messageKey) {
            case "countdown" -> messageCountdown;
            case "grace" -> messageGrace;
            case "landfall" -> messageLandfall;
            case "tickWarn" -> messageTickWarn;
            case "cleared" -> messageCleared;
            case "status.idle" -> messageStatusIdle;
            case "status.countdown" -> messageStatusCountdown;
            case "status.active" -> messageStatusActive;
            default -> messageKey;
        };

        // Replace placeholders
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return miniMessage.deserialize(messagePrefix + message);
    }

    public Component formatMessageNoPrefix(String text) {
        return miniMessage.deserialize(text);
    }
}