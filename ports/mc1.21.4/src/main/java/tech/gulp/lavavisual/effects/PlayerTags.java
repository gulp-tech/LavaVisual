package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("lavavisual", "badge");
    private static final String GLYPH = "\uE000";
    private static final Component LOGO = Component.literal(GLYPH).withStyle(style -> style.withFont(FONT).withColor(0xFFFFFF));
    /** Rows badged in the player list (the CI smoke log prints it). */
    public static int tabBadges;
    /** Set by PlayerTabOverlayMixin when it is applied: proves the player list is hooked in this version. */
    public static boolean tabMixinLoaded;
    private PlayerTags() { }

    /** The name tag with the LV logo in front when it belongs to another player showing the mark. */
    public static Component badge(Entity entity, Component name) {
        if (name == null || !(entity instanceof Player player)) return name;
        var mc = Minecraft.getInstance();
        if (player == mc.player || !LavaVisualClient.config().badgeEnabled || !HatSync.marked(player)) return name;
        return decorate(name);
    }

    /** The player list row with the LV logo in front; here your own row is marked too, so you see your badge. */
    public static Component tabBadge(PlayerInfo info, Component name) {
        if (name == null || info == null || !LavaVisualClient.config().badgeEnabled) return name;
        var mc = Minecraft.getInstance();
        Player player = byRow(info, mc);
        if (player == null || !HatSync.marked(player)) return name;
        tabBadges++;
        return decorate(name);
    }

    /** The player behind a player-list row. The profile accessors moved between versions, so the name is matched. */
    private static Player byRow(PlayerInfo info, Minecraft mc) {
        if (info == null || mc.level == null) return null;
        Object profile = info.getProfile();
        String name = call(profile, "getName");
        if (name == null) name = call(profile, "name");
        if (name == null) return null;
        for (Player player : mc.level.players()) if (name.equals(player.getName().getString())) return player;
        return null;
    }

    private static String call(Object target, String method) {
        try {
            return String.valueOf(target.getClass().getMethod(method).invoke(target));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** Adds the glyph unless the name already carries it: name tags and player list rows both pass through here. */
    private static Component decorate(Component name) {
        return name.getString().startsWith(GLYPH) ? name : Component.empty().append(LOGO).append(" ").append(name);
    }

    /** CI check: the glyph comes from the badge font (without it the font falls back to a narrow box). */
    public static String selfTest() {
        int width = Minecraft.getInstance().font.width(LOGO);
        return (width >= 10 ? "LavaVisual badge glyph ok: " : "LavaVisual badge glyph failed: ") + width
                + " · player list " + (tabMixinLoaded ? "hooked" : "not hooked") + ", rows " + tabBadges;
    }
}
