package tech.gulp.lavavisual;

/** Where the game runs: Android launchers (PojavLauncher, MojoLauncher and forks) run the desktop game on a phone. */
public final class Platform {
    private static Boolean android;
    private Platform() { }

    public static boolean android() {
        if (android == null) {
            boolean found;
            try {
                found = System.getenv("POJAV_RENDERER") != null || System.getenv("POJAV_NATIVEDIR") != null
                        || System.getProperty("java.vendor", "").toLowerCase(java.util.Locale.ROOT).contains("android")
                        || new java.io.File("/system/build.prop").isFile();
            } catch (RuntimeException error) {
                found = false;
            }
            android = found;
        }
        return android;
    }
}
