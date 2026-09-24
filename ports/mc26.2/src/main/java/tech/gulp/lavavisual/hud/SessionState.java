package tech.gulp.lavavisual.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

public final class SessionState {
    public record Notice(String text, long created) { }
    private final Deque<Notice> notices = new ArrayDeque<>();
    private long sessionStart = System.nanoTime(), started, accumulated;
    private boolean running;
    public String coordinates = "X 0   Y 64   Z 0", performance = "FPS --", memory = "Heap --", session = "00:00";
    public void joined() { sessionStart = System.nanoTime(); reset(); notify("World connected"); }
    public void leave() { notices.clear(); reset(); coordinates = "Not in a world"; }
    public void notify(String message) {
        if (notices.size() >= 4) notices.removeFirst();
        notices.addLast(new Notice(message.substring(0, Math.min(72, message.length())), System.nanoTime()));
    }
    public Notice currentNotice() {
        long now = System.nanoTime();
        while (!notices.isEmpty() && now - notices.peekFirst().created() > TimeUnit.SECONDS.toNanos(4)) notices.removeFirst();
        return notices.peekFirst();
    }
    public void toggleTimer() {
        long now = System.nanoTime();
        if (running) accumulated += now - started; else started = now;
        running = !running;
        notify(running ? "Stopwatch started" : "Stopwatch paused");
    }
    public void reset() { running = false; accumulated = 0; }
    public boolean running() { return running; }
    public String stopwatch() { return format(accumulated + (running ? System.nanoTime() - started : 0)); }
    public void updateSession() { session = format(System.nanoTime() - sessionStart); }
    public static String format(long nanos) {
        long seconds = Math.max(0, TimeUnit.NANOSECONDS.toSeconds(nanos));
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
