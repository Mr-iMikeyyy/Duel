package net.madmike.duel;
import java.util.concurrent.ScheduledFuture;

public class DuelTrackedTimer {
    private final ScheduledFuture<?> future;
    private final long startTimeMillis;
    private final long durationSeconds;

    public DuelTrackedTimer(ScheduledFuture<?> future, long durationSeconds) {
        this.future = future;
        this.durationSeconds = durationSeconds;
        this.startTimeMillis = System.currentTimeMillis();
    }

    public boolean isActive() {
        return future != null && !future.isCancelled() && !future.isDone();
    }

    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    public long getRemainingSeconds() {
        long remaining = durationSeconds - getElapsedSeconds();
        return Math.max(0, remaining);
    }

    public void cancel() {
        if (isActive()) {
            future.cancel(false);
        }
    }

    public String getStatus() {
        if (future == null) return "Not started";
        if (future.isCancelled()) return "Cancelled";
        if (future.isDone()) return "Completed";
        return "Active - " + getRemainingSeconds() + "s remaining";
    }
}
