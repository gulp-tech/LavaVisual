package tech.gulp.lavavisual.ui;

/**
 * Whether one of LavaVisual's own menus is on screen outside a world. Vanilla caps the frame rate at 60 FPS there
 * (FramerateLimitTracker, OUT_OF_LEVEL_MENU) whatever Max Framerate says, so the menu animations ran at half the rate
 * of the in-game ones; the mixin asks for the player's Max Framerate instead while a menu is marked. Vanilla menus
 * keep the cap, and the AFK and minimised limits are other throttle reasons that stay untouched.
 *
 * Screens mark themselves by identity, so a screen that replaces another (the title screen opens the menu) cannot
 * clear the mark of the one that took over, whatever order the game calls removed() and the constructors in.
 */
public final class MenuRate {
    private static Object menu;
    private MenuRate() { }
    public static void opened(Object screen) { menu = screen; }
    public static void closed(Object screen) { if (menu == screen) menu = null; }
    public static boolean menu() { return menu != null; }
}
