package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * LV logo in front of the name tag of other LavaVisual users who share their mark (see Badge).
 *
 * The logo is one glyph of the lavavisual:badge bitmap font, so it is part of the vanilla name tag itself: same line,
 * same background, centred together with the name and any rank prefix, hidden and dimmed exactly like the name.
 * Purely client-side: no custom packets or channels.
 */
public final class PlayerTags {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("lavavisual", "badge"));
    private static final Component LOGO = Component.literal("\uE000").withStyle(style -> style.withFont(FONT).withColor(0xFFFFFF));
    private PlayerTags() { }

    /** The name tag with the LV logo in front when it belongs to another player showing the mark. */
    public static Component badge(Entity entity, Component name) {
        if (name == null || !(entity instanceof Player player)) return name;
        var mc = Minecraft.getInstance();
        if (player == mc.player || !LavaVisualClient.config().badgeEnabled || !HatSync.marked(player)) return name;
        return Component.empty().append(LOGO).append(" ").append(name);
    }

    /** CI check: the glyph comes from the badge font (without it the font falls back to a narrow box). */
    public static String selfTest() {
        int width = Minecraft.getInstance().font.width(LOGO);
        return (width >= 10 ? "LavaVisual badge glyph ok: " : "LavaVisual badge glyph failed: ") + width;
    }
}
