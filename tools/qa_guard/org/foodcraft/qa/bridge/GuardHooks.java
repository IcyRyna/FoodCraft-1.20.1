package org.foodcraft.qa.bridge;

import java.util.concurrent.atomic.AtomicInteger;

/** Bootstrap-visible bridge shared by Forge's module loader and Fabric's Knot loader. */
public final class GuardHooks {
    private static final AtomicInteger warps = new AtomicInteger();
    private static final AtomicInteger modes = new AtomicInteger();
    private static final AtomicInteger focus = new AtomicInteger();
    private static final AtomicInteger sounds = new AtomicInteger();

    public static boolean blockInputMode(int mode) {
        if (mode == 0x00033001 || mode == 0x00033005) {
            modes.incrementAndGet();
            return true;
        }
        return false;
    }
    public static void blockedWarp() { warps.incrementAndGet(); }
    public static void blockedFocus() { focus.incrementAndGet(); }
    public static void blockedSound() { sounds.incrementAndGet(); }
    public static String summary() {
        return " blocked_cursor_warps=" + warps.get() + " blocked_cursor_modes=" + modes.get()
            + " blocked_focus_requests=" + focus.get() + " blocked_audio_plays=" + sounds.get();
    }
}
