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
        if (player == null) return name;
        // Your own row trusts what you share right now: the server does not echo the mark back to its sender at once.
        boolean shows = player == mc.player ? HatSync.advertised() : HatSync.marked(player);
        if (!shows) return name;
        tabBadges++;
        return decorate(name);
    }

    /** The player behind a player-list row: the connection caches exactly one row per player, so identity matches. */
    private static Player byRow(PlayerInfo info, Minecraft mc) {
        if (info == null || mc.level == null || mc.getConnection() == null) return null;
        for (Player player : mc.level.players())
            if (mc.getConnection().getPlayerInfo(player.getUUID()) == info) return player;
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

    /** CI: the row of the local player, run through the very code the player list uses for every row. */
    public static String selfCheck(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return "no connection";
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) return "no player info";
        Component row = tabBadge(info, Component.literal("smoke"));
        String text = row == null ? "" : row.getString();
        return (text.startsWith(GLYPH) ? "ok " : "no badge ") + '"' + text + '"';
    }

    /** CI check: the glyph comes from the badge font (without it the font falls back to a narrow box). */
    public static String selfTest() {
        int width = Minecraft.getInstance().font.width(LOGO);
        return (width >= 10 ? "LavaVisual badge glyph ok: " : "LavaVisual badge glyph failed: ") + width
                + " · player list " + (tabMixinLoaded ? "hooked" : "not hooked") + ", rows " + tabBadges;
    }
}
