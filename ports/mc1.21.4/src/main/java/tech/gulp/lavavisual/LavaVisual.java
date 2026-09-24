package tech.gulp.lavavisual;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LavaVisual implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lavavisual");

    @Override
    public void onInitialize() {
        LOGGER.info("LavaVisual 1.0.0 — Minecraft 1.21.4");
    }
}
