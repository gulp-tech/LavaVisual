package tech.gulp.lavavisual.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tech.gulp.lavavisual.ui.LavaTitleScreen;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {
    /** The vanilla title screen (also the one shown for "no screen and no world") becomes the LavaVisual one. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen lava$title(Screen screen) { return LavaTitleScreen.replace(screen); }
}
