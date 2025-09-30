package dev.ked.stormcraft.model;

public class ActiveStorm {
    private final StormProfile profile;
    private final long startTimeMillis;
    private final int originalDurationSeconds;
    private final double actualDamagePerSecond;
    private int remainingSeconds;

    public ActiveStorm(StormProfile profile, int durationSeconds, double actualDamagePerSecond) {
        this.profile = profile;
        this.startTimeMillis = System.currentTimeMillis();
        this.originalDurationSeconds = durationSeconds;
        this.actualDamagePerSecond = actualDamagePerSecond;
        this.remainingSeconds = durationSeconds;
    }

    public ActiveStorm(StormProfile profile, int originalDurationSeconds, int remainingSeconds, long startTimeMillis, double actualDamagePerSecond) {
        this.profile = profile;
        this.originalDurationSeconds = originalDurationSeconds;
        this.remainingSeconds = remainingSeconds;
        this.startTimeMillis = startTimeMillis;
        this.actualDamagePerSecond = actualDamagePerSecond;
    }

    public StormProfile getProfile() {
        return profile;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public int getOriginalDurationSeconds() {
        return originalDurationSeconds;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public double getActualDamagePerSecond() {
        return actualDamagePerSecond;
    }

    public void decrementRemaining(int seconds) {
        this.remainingSeconds = Math.max(0, this.remainingSeconds - seconds);
    }

    public boolean isExpired() {
        return remainingSeconds <= 0;
    }
}