package dev.ked.stormcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.ked.stormcraft.StormcraftPlugin;
import dev.ked.stormcraft.model.ActiveStorm;
import dev.ked.stormcraft.model.StormProfile;
import dev.ked.stormcraft.model.StormType;
import dev.ked.stormcraft.schedule.StormManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Handles persistence of storm state to data.json for resuming across server restarts.
 */
public class PersistenceManager {
    private final StormcraftPlugin plugin;
    private final ConfigManager config;
    private final File dataFile;
    private final Gson gson;

    public PersistenceManager(StormcraftPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.dataFile = new File(plugin.getDataFolder(), "data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Saves the current storm state to data.json.
     */
    public void saveState(StormManager stormManager) {
        JsonObject data = new JsonObject();

        data.addProperty("nextStormTimeMillis", stormManager.getSecondsUntilNextStorm() * 1000 + System.currentTimeMillis());
        data.addProperty("phase", stormManager.getCurrentPhase().name());

        // Save active storm if present
        if (stormManager.hasActiveStorm()) {
            ActiveStorm storm = stormManager.getActiveStorm();
            if (storm != null) {
                JsonObject stormData = new JsonObject();
                stormData.addProperty("type", storm.getProfile().getType().name());
                stormData.addProperty("originalDurationSeconds", storm.getOriginalDurationSeconds());
                stormData.addProperty("remainingSeconds", storm.getRemainingSeconds());
                stormData.addProperty("startTimeMillis", storm.getStartTimeMillis());
                stormData.addProperty("actualDamagePerSecond", storm.getActualDamagePerSecond());
                data.add("activeStorm", stormData);
            }
        }

        // Save countdown if present
        if (stormManager.isCountdownActive()) {
            JsonObject countdownData = new JsonObject();
            countdownData.addProperty("type", stormManager.getUpcomingStormType().name());
            countdownData.addProperty("remainingSeconds", stormManager.getCountdownRemainingSeconds());
            data.add("countdown", countdownData);
        }

        try {
            ensureDataFolderExists();
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save storm state: " + e.getMessage());
        }
    }

    /**
     * Loads storm state from data.json and restores it to StormManager.
     */
    public void loadState(StormManager stormManager) {
        if (!dataFile.exists()) {
            return; // No saved state
        }

        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject data = gson.fromJson(reader, JsonObject.class);

            if (data == null) {
                return;
            }

            // Restore next storm time
            if (data.has("nextStormTimeMillis")) {
                long nextStormTime = data.get("nextStormTimeMillis").getAsLong();
                stormManager.setNextStormTimeMillis(nextStormTime);
            }

            // Restore active storm
            if (data.has("activeStorm")) {
                JsonObject stormData = data.getAsJsonObject("activeStorm");
                StormType type = StormType.valueOf(stormData.get("type").getAsString());
                int originalDuration = stormData.get("originalDurationSeconds").getAsInt();
                int remaining = stormData.get("remainingSeconds").getAsInt();
                long startTime = stormData.get("startTimeMillis").getAsLong();
                double actualDamage = stormData.has("actualDamagePerSecond")
                    ? stormData.get("actualDamagePerSecond").getAsDouble()
                    : 1.0; // Fallback for old saves

                StormProfile profile = config.getDamageProfiles().get(type);
                if (profile != null) {
                    ActiveStorm storm = new ActiveStorm(profile, originalDuration, remaining, startTime, actualDamage);
                    stormManager.restoreActiveStorm(storm);
                    plugin.getLogger().info("Restored active storm: " + type + ", " + remaining + "s remaining");
                }
            }
            // Restore countdown
            else if (data.has("countdown")) {
                JsonObject countdownData = data.getAsJsonObject("countdown");
                StormType type = StormType.valueOf(countdownData.get("type").getAsString());
                int remaining = countdownData.get("remainingSeconds").getAsInt();

                StormProfile profile = config.getDamageProfiles().get(type);
                if (profile != null) {
                    stormManager.restoreCountdown(profile, remaining);
                    plugin.getLogger().info("Restored countdown: " + type + ", " + remaining + "s remaining");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load storm state: " + e.getMessage());
        }
    }

    /**
     * Clears the saved state file.
     */
    public void clearState() {
        if (dataFile.exists()) {
            dataFile.delete();
        }
    }

    private void ensureDataFolderExists() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }
}