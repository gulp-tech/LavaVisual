package tech.gulp.lavavisual.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tech.gulp.lavavisual.effects.WorldCosmetics;

/**
 * The inventory draws the player into a picture-in-picture texture exactly the size of the box it is asked for, so a
 * tall cosmetic — a hat, wings, a cape — is cut off at the edge and only half of it shows. This is a vanilla quirk,
 * but with cosmetics on it is very visible, so the box gains a margin above and below while anything of ours is
 * worn. The model keeps its size and stays centred, and the plain vanilla look is untouched.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryFitMixin {
    /** Pixels of margin the box gains at the top and at the bottom. */
    private static final int MARGIN = 15;

    @ModifyVariable(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int lavavisual$boxTop(int y0) {
        return WorldCosmetics.inInventory() ? y0 - MARGIN : y0;
    }

    @ModifyVariable(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static int lavavisual$boxBottom(int y1) {
        return WorldCosmetics.inInventory() ? y1 + MARGIN : y1;
    }
}
