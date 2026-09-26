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
    public static String version() {
        return FabricLoader.getInstance().getModContainer("lavavisual").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }
}
