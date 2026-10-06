package tech.gulp.lavavisual.effects;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.player.Player;

/** Server-free LavaVisual detection. Opt-in (badgeShare): sets the unused 0x80 bit of the skin-parts byte that
    servers relay to other players; vanilla renders nothing for this bit. HatSync decides when the bit is set: never
    while joining, only after ten seconds in the world, and it briefly spells out the hat. Reading marks is always safe. */
public final class Badge {
    public static final int BIT = 0x80;
    private Badge() { }
    public static ClientInformation mark(ClientInformation info) { return withBit(info, HatSync.advertised()); }
    public static ClientInformation withBit(ClientInformation info, boolean bit) {
        if (info == null || !bit && !marked(info)) return info;
        try {
            var components = ClientInformation.class.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            boolean found = false;
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                values[i] = components[i].getAccessor().invoke(info);
                if (components[i].getName().equals("modelCustomisation") && values[i] instanceof Integer parts) {
                    values[i] = bit ? parts | BIT : parts & ~BIT;
                    found = true;
                }
            }
            return found ? ClientInformation.class.getDeclaredConstructor(types).newInstance(values) : info;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return info;
        }
    }
    public static boolean marked(ClientInformation info) {
        try {
            for (var component : ClientInformation.class.getRecordComponents())
                if (component.getName().equals("modelCustomisation") && component.getAccessor().invoke(info) instanceof Integer parts)
                    return (parts & BIT) != 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }
    /** Whether the player carries the mark. Read through the mixin on Player, never by a reflected field name. */
    public static boolean marked(Player player) {
        return player instanceof SkinParts parts && (parts.lavavisual$skinParts() & BIT) != 0;
    }
}
