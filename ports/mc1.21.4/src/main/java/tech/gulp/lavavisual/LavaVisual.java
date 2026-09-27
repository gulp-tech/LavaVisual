package tech.gulp.lavavisual;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: LavaVisual is client-only, so nothing is registered here (no packets, channels or commands). */
public final class LavaVisual implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lavavisual");

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer("lavavisual")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
        LOGGER.info("LavaVisual {} (client-only visuals)", version);
    }
}
