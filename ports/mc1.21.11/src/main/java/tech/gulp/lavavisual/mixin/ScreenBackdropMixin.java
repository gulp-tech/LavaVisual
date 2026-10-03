package tech.gulp.lavavisual.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.ui.MenuTheme;

/** Decorates the world and server screens with the LavaVisual frame after they have drawn (see MenuTheme). */
@Mixin(Screen.class)
public abstract class ScreenBackdropMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void lavavisual$frame(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MenuTheme.frame(graphics, (Screen) (Object) this, mouseX, mouseY);
    }
}
