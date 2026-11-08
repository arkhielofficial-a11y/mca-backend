package com.mca.client.core;

import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/**
 * Tracks real-time and active world-tick timing for an MCA run.
 */
public final class MCATimer {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static long runStartNanos;
    private static long igtTicks;
    private static long lastWorldTime;
    private static ClientWorld lastWorld;
    private static long stoppedRtaMilliseconds;
    private static boolean running;

    private MCATimer() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MCATimer::onClientTick);
        start();
    }

    /**
     * Starts both timer sources for a new run.
     */
    public static synchronized void start() {
        runStartNanos = System.nanoTime();
        igtTicks = 0L;
        stoppedRtaMilliseconds = 0L;
        lastWorld = null;
        lastWorldTime = -1L;
        running = true;
    }

    /**
     * Stops timers without destroying split data.
     */
    public static synchronized void stop() {
        if (running) stoppedRtaMilliseconds = getRtaMillisecondsUnlocked();
        running = false;
    }

    private static void onClientTick(MinecraftClient client) {
        if (!running || client.world == null) return;

        // A paused game does not advance world time, so it contributes no IGT.
        if (client.isPaused()) return;
        synchronized (MCATimer.class) {
            ClientWorld world = client.world;
            if (world != lastWorld || lastWorldTime < 0L) {
                lastWorld = world;
                lastWorldTime = world.getTime();
                return;
            }
            long current = world.getTime();
            if (current >= lastWorldTime) igtTicks += current - lastWorldTime;
            lastWorldTime = current;
        }
    }

    /**
     * Returns elapsed real time in milliseconds.
     */
    public static synchronized long getRtaMilliseconds() {
        return getRtaMillisecondsUnlocked();
    }

    private static long getRtaMillisecondsUnlocked() {
        if (!running) return stoppedRtaMilliseconds;
        return stoppedRtaMilliseconds + (System.nanoTime() - runStartNanos) / 1_000_000L;
    }

    /**
     * Returns active world time. Minecraft advances 20 ticks per second.
     */
    public static synchronized long getIgtMilliseconds() {
        return igtTicks * 50L;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static String format(long milliseconds) {
        long totalTenths = Math.max(0L, milliseconds) / 100L;
        long tenths = totalTenths % 10L;
        long totalSeconds = totalTenths / 10L;
        long seconds = totalSeconds % 60L;
        long minutes = (totalSeconds / 60L) % 60L;
        long hours = totalSeconds / 3600L;
        return hours > 0L
            ? String.format(Locale.ROOT, "%02d:%02d:%02d.%d", hours, minutes, seconds, tenths)
            : String.format(Locale.ROOT, "%02d:%02d.%d", minutes, seconds, tenths);
    }
}
