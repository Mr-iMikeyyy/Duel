package net.madmike.duel;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DuelTimerManager {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private DuelTrackedTimer duelRequestTimer;
    private DuelTrackedTimer countdownTimer;
    private DuelTrackedTimer duelTimer;

    public void startDuelRequestTimer(Runnable onExpire, int seconds) {
        cancelDuelRequestTimer();
        ScheduledFuture<?> future = scheduler.schedule(onExpire, seconds, TimeUnit.SECONDS);
        duelRequestTimer = new DuelTrackedTimer(future, seconds);
    }

    public void startCountdown(ServerPlayerEntity p1, ServerPlayerEntity p2, Runnable onComplete) {
        cancelCountdown();
        final int totalSeconds = 5;

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(new Runnable() {
            int secondsLeft = totalSeconds;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    cancelCountdown();
                    onComplete.run();
                    return;
                }

                Text msg = Text.literal("§eDuel starts in §c" + secondsLeft + "...");
                p1.sendMessage(msg);
                p2.sendMessage(msg);
                secondsLeft--;
            }
        }, 0, 1, TimeUnit.SECONDS);

        countdownTimer = new DuelTrackedTimer(future, totalSeconds);
    }

    public void startDuelTimer(Runnable onExpire, int minutes) {
        cancelDuelTimer();
        long seconds = minutes * 60L;
        ScheduledFuture<?> future = scheduler.schedule(onExpire, seconds, TimeUnit.SECONDS);
        duelTimer = new DuelTrackedTimer(future, seconds);
    }

    public void cancelDuelRequestTimer() {
        if (duelRequestTimer != null) {
            duelRequestTimer.cancel();
            duelRequestTimer = null;
        }
    }

    public void cancelCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    public void cancelDuelTimer() {
        if (duelTimer != null) {
            duelTimer.cancel();
            duelTimer = null;
        }
    }

    public String getDebugStatus() {
        return """
            §b--- Duel Timer Debug Info ---
            §7Duel Request Timer: §f%s
            §7Countdown Timer: §f%s
            §7Duel Timer: §f%s
            """.formatted(
                duelRequestTimer != null ? duelRequestTimer.getStatus() : "Not started",
                countdownTimer != null ? countdownTimer.getStatus() : "Not started",
                duelTimer != null ? duelTimer.getStatus() : "Not started"
        );
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
