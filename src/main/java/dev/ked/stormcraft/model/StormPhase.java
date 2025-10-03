package dev.ked.stormcraft.model;

/**
 * Represents the current phase of a traveling storm's lifecycle.
 */
public enum StormPhase {
    /**
     * FORMING phase - Storm is building up strength.
     * Damage ramps from 0% to 100% over this phase.
     */
    FORMING("Forming", "§7⬆"),

    /**
     * PEAK phase - Storm is at maximum intensity.
     * Damage at 100% throughout this phase.
     */
    PEAK("Peak", "§c⚡"),

    /**
     * DISSIPATING phase - Storm is weakening.
     * Damage ramps down from 100% to 0% over this phase.
     */
    DISSIPATING("Dissipating", "§7⬇");

    private final String displayName;
    private final String symbol;

    StormPhase(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets a colored display name for the phase.
     */
    public String getColoredName() {
        return switch (this) {
            case FORMING -> "§7Forming";
            case PEAK -> "§cPeak";
            case DISSIPATING -> "§7Dissipating";
        };
    }
}
