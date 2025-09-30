package dev.ked.stormcraft.model;

public enum StormType {
    SHORT_WEAK("shortWeak"),
    MEDIUM("medium"),
    LONG_DANGEROUS("longDangerous");

    private final String configKey;

    StormType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static StormType fromConfigKey(String key) {
        for (StormType type : values()) {
            if (type.configKey.equals(key)) {
                return type;
            }
        }
        return null;
    }
}