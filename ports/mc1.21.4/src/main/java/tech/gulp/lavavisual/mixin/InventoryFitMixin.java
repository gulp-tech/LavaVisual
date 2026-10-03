package tech.gulp.lavavisual.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tech.gulp.lavavisual.effects.WorldCosmetics;

/**
 * The inventory draws the player into a picture-in-picture window exactly the size of the box it is asked for, so a
 * tall cosmetic — a hat, wings, a cape, the crewmate's suit — was cut off at the edge and only half of it showed.
 * This is a vanilla quirk, but with cosmetics on it is very visible: the window now gains a small margin and the
 * model is drawn a little smaller while anything of ours is worn, so the whole look is inside. The plain vanilla
 * portrait is untouched.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryFitMixin {
    /** The portrait box (x0, y0, x1, y1, size) gains a little room, and the model shrinks to fit it. */
    private static final int MARGIN_X = 8, MARGIN_TOP = 8, MARGIN_BOTTOM = 6;

    @ModifyVariable(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int lavavisual$boxLeft(int x0) {
        return WorldCosmetics.inInventory() ? x0 - MARGIN_X : x0;
    }

    @ModifyVariable(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int lavavisual$boxTop(int y0) {
        return WorldCosmetics.inInventory() ? y0 - MARGIN_TOP : y0;
    }

    @ModifyVariable(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int lavavisual$boxRight(int x1) {
        return WorldCosmetics.inInventory() ? x1 + MARGIN_X : x1;
    }

    @ModifyVariable(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static int lavavisual$boxBottom(int y1) {
        return WorldCosmetics.inInventory() ? y1 + MARGIN_BOTTOM : y1;
    }

    /**
     * The scale, on the other hand, goes down: vanilla draws the player at 30 px per block, which fills the box
     * exactly, so a hat, wings or a backpack always ran out of the window and showed as a cut-off half. At 0.72 of
     * that scale the whole look — skin, hat, wings, cape — fits the box with room to spare, and the plain vanilla
     * portrait is untouched (the box and the scale are only changed while something of ours is worn).
     */
    @ModifyVariable(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private static int lavavisual$scale(int size) {
        return WorldCosmetics.inInventory() ? Math.max(12, Math.round(size * 0.72f)) : size;
    }
}
