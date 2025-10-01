package dev.ked.stormcraft.ui;

import dev.ked.stormcraft.StormcraftPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages user preferences for storm UI displays (actionbar and bossbar).
 */
public class StormUIPreferences {
    private final StormcraftPlugin plugin;
    private final File prefsFile;
    private FileConfiguration prefsConfig;

    // In-memory cache
    private final Map<UUID, Boolean> actionbarEnabled = new HashMap<>();
    private final Map<UUID, Boolean> bossbarEnabled = new HashMap<>();

    public StormUIPreferences(StormcraftPlugin plugin) {
        this.plugin = plugin;
        this.prefsFile = new File(plugin.getDataFolder(), "ui-preferences.yml");
        loadPreferences();
    }

    private void loadPreferences() {
        if (!prefsFile.exists()) {
            try {
                prefsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create ui-preferences.yml: " + e.getMessage());
            }
        }

        prefsConfig = YamlConfiguration.loadConfiguration(prefsFile);

        // Load all preferences into cache
        if (prefsConfig.contains("players")) {
            for (String uuidString : prefsConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                actionbarEnabled.put(uuid, prefsConfig.getBoolean("players." + uuidString + ".actionbar", true));
                bossbarEnabled.put(uuid, prefsConfig.getBoolean("players." + uuidString + ".bossbar", true));
            }
        }
    }

    private void savePreferences() {
        try {
            prefsConfig.save(prefsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ui-preferences.yml: " + e.getMessage());
        }
    }

    public boolean isActionbarEnabled(UUID playerId) {
        return actionbarEnabled.getOrDefault(playerId, true);
    }

    public boolean isBossbarEnabled(UUID playerId) {
        return bossbarEnabled.getOrDefault(playerId, true);
    }

    public void setActionbarEnabled(UUID playerId, boolean enabled) {
        actionbarEnabled.put(playerId, enabled);
        prefsConfig.set("players." + playerId.toString() + ".actionbar", enabled);
        savePreferences();
    }

    public void setBossbarEnabled(UUID playerId, boolean enabled) {
        bossbarEnabled.put(playerId, enabled);
        prefsConfig.set("players." + playerId.toString() + ".bossbar", enabled);
        savePreferences();
    }

    public void toggleActionbar(UUID playerId) {
        boolean current = isActionbarEnabled(playerId);
        setActionbarEnabled(playerId, !current);
    }

    public void toggleBossbar(UUID playerId) {
        boolean current = isBossbarEnabled(playerId);
        setBossbarEnabled(playerId, !current);
    }

    public void toggleBoth(UUID playerId) {
        boolean currentActionbar = isActionbarEnabled(playerId);
        boolean currentBossbar = isBossbarEnabled(playerId);

        // If either is enabled, disable both. Otherwise, enable both.
        boolean newState = !(currentActionbar || currentBossbar);

        setActionbarEnabled(playerId, newState);
        setBossbarEnabled(playerId, newState);
    }
}
