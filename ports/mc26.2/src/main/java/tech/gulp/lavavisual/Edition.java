package tech.gulp.lavavisual;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod or client. The LavaVisual Client package is the same jar plus config/lavavisual-client.json; with that file the
 * window is titled "LavaVisual Client". A separate marker keeps the user's own settings untouched on updates.
 */
public final class Edition {
    private static Boolean client;
    private Edition() { }

    public static boolean client() {
        if (client == null) {
            try { client = java.nio.file.Files.exists(FabricLoader.getInstance().getConfigDir().resolve("lavavisual-client.json")); }
            catch (RuntimeException error) { client = false; }
        }
        return client;
    }
    /** "Minecraft* 26.2 - Singleplayer" becomes "LavaVisual Client 26.2 - Singleplayer" in the client edition. */
    public static String title(String vanilla) { return client() ? retitle(vanilla) : vanilla; }
    public static String retitle(String vanilla) { return vanilla == null ? null : vanilla.replaceFirst("^Minecraft\\*?", "LavaVisual Client"); }
    /** "Beta 1.0.0" for 1.0.0-beta-mc26.2; plain numbers otherwise. */
    public static String label() {
        String v = version().replaceFirst("-mc.*$", "");
        int dash = v.indexOf('-');
        if (dash <= 0 || dash == v.length() - 1) return v;
        String tag = v.substring(dash + 1);
        return Character.toUpperCase(tag.charAt(0)) + tag.substring(1) + " " + v.substring(0, dash);
    }
    public static String version() {
        return FabricLoader.getInstance().getModContainer("lavavisual").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }
}
