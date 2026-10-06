package tech.gulp.lavavisual.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import tech.gulp.lavavisual.effects.SkinParts;

/**
 * Gives the mod the skin-parts byte of any player.
 *
 * The field is shadowed instead of looked up by name on purpose: a shipped jar only carries the loader's names, so a
 * lookup written with the source names finds nothing outside the development client and the badge would quietly never
 * appear. The mixin remapper rewrites this shadow along with the rest of the mod, and a version that moves the field
 * fails the build instead of hiding the badge.
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements SkinParts {
    @Shadow protected static EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION;

    @Override public int lavavisual$skinParts() {
        try {
            Byte parts = ((Player) (Object) this).getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION);
            return parts == null ? 0 : parts & 0xFF;
        } catch (RuntimeException error) {
            return 0;
        }
    }
}
