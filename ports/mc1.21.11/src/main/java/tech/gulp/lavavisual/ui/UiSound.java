package tech.gulp.lavavisual.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import tech.gulp.lavavisual.LavaVisualClient;

/** The vanilla button click for LavaVisual's own menus (Interface tab: "Звук кнопок"). */
public final class UiSound {
    private static final Identifier CLICK = Identifier.fromNamespaceAndPath("minecraft", "ui.button.click");
    private UiSound() { }

    public static void click() {
        if (!LavaVisualClient.config().menuSounds) return;
        try {
            // Same volume and pitch as a vanilla button, not positioned in the world.
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(CLICK, SoundSource.MASTER, 0.25f, 1f,
                    SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        } catch (RuntimeException ignored) {
            // no sound device: the click just stays silent
        }
    }
}
