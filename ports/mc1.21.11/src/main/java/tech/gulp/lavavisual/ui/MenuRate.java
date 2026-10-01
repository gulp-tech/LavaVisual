package tech.gulp.lavavisual.ui;

/**
 * Whether one of LavaVisual's own menus is on screen outside a world. Vanilla caps the frame rate at 60 FPS there
 * (FramerateLimitTracker, OUT_OF_LEVEL_MENU) whatever Max Framerate says, so the menu animations ran at half the rate
 * of the in-game ones; the mixin asks for the player's Max Framerate instead while this is set. Vanilla menus keep the
 * cap, and the AFK and minimised limits are other throttle reasons that stay untouched.
 */
public final class MenuRate {
    private static boolean menu;
    private MenuRate() { }
    public static void opened() { menu = true; }
    public static void closed() { menu = false; }
    public static boolean menu() { return menu; }
}
