package tech.gulp.lavavisual.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.gulp.lavavisual.effects.PlayerTags;

/**
 * Puts the LV logo into the player list (Tab) rows of LavaVisual players, just like the name tags. Also flips
 * PlayerTags.tabMixinLoaded, so the smoke log says whether this version really has the hook.
 *
 * The full descriptor is spelled out on purpose: tools/check_mixins.py verifies it against every Minecraft version
 * in the CI, so a renamed method shows up as a build error instead of a badge that silently disappears. require = 0
 * keeps the game starting even if it ever does change.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    static { PlayerTags.tabMixinLoaded = true; }

    @Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void lavavisual$tabBadge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(PlayerTags.tabBadge(info, cir.getReturnValue()));
    }
}
