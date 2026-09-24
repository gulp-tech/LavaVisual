package tech.gulp.lavavisual.effects;

import java.lang.reflect.Field;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.player.Player;
import tech.gulp.lavavisual.LavaVisualClient;

/** Server-free LavaVisual detection. Opt-in (badgeShare): sets the unused 0x80 bit of the skin-parts byte that
    servers relay to other players; vanilla renders nothing for this bit, but strict anti-bot filters may reject it,
    so it is off by default. Reading other players' marks is always safe. */
public final class Badge {
    public static final int BIT = 0x80;
    private static Field field;
    private static boolean searched;
    private Badge() { }
    public static ClientInformation mark(ClientInformation info) {
        if (info == null || !LavaVisualClient.config().badgeShare) return info;
        try {
            var components = ClientInformation.class.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            boolean found = false;
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                values[i] = components[i].getAccessor().invoke(info);
                if (components[i].getName().equals("modelCustomisation") && values[i] instanceof Integer parts) {
                    values[i] = parts | BIT;
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
    public static boolean marked(Player player) {
        EntityDataAccessor<Byte> accessor = accessor(player);
        if (accessor == null) return false;
        try {
            Object value = player.getEntityData().get(accessor);
            return value instanceof Byte parts && (parts & BIT) != 0;
        } catch (RuntimeException error) {
            return false;
        }
    }
    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> accessor(Player player) {
        if (!searched) {
            searched = true;
            for (Class<?> type = player.getClass(); type != null && field == null; type = type.getSuperclass()) {
                try {
                    Field candidate = type.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
                    candidate.setAccessible(true);
                    field = candidate;
                } catch (NoSuchFieldException | RuntimeException ignored) { }
            }
        }
        try {
            return field == null ? null : (EntityDataAccessor<Byte>) field.get(null);
        } catch (IllegalAccessException | RuntimeException error) {
            return null;
        }
    }
}
