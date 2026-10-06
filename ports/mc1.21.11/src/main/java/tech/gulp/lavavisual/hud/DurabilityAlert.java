package tech.gulp.lavavisual.hud;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.input.Binds;
import tech.gulp.lavavisual.ui.UiSound;

/**
 * One toast and one soft chime per item that drops below the low-durability threshold set in the armor widget
 * settings. The slot is named in words (the item name API is not the same in every port), the warning re-arms when
 * the item is repaired or replaced, and the same switch that dims the bars turns it off.
 */
public final class DurabilityAlert {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"шлем", "нагрудник", "штаны", "ботинки"};
    /** slot -> identity of the item already warned about, so a worn pickaxe does not nag every tick. */
    private static final Map<String, Integer> WARNED = new HashMap<>();
    private static int alerts;

    private DurabilityAlert() { }

    /** Number of warnings shown since the game started (the CI smoke test reads this). */
    public static int count() { return alerts; }

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            WARNED.clear();
            return;
        }
        HudConfig c = LavaVisualClient.config();
        if (!c.durabilityAlert && !c.durabilityAlertSound) {
            WARNED.clear();
            return;
        }
        check(c, "рука", mc.player.getMainHandItem());
        check(c, "вторая рука", mc.player.getOffhandItem());
        for (int i = 0; i < ARMOR.length; i++) check(c, ARMOR_NAMES[i], mc.player.getItemBySlot(ARMOR[i]));
        var inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) check(c, "слот " + (i + 1), inventory.getItem(i));
    }

    private static void check(HudConfig c, String slot, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            WARNED.remove(slot);
            return;
        }
        double ratio = Math.clamp(1 - (double) stack.getDamageValue() / stack.getMaxDamage(), 0, 1);
        if (ratio > c.durabilityThreshold) {
            WARNED.remove(slot);   // repaired or replaced: the next drop warns again
            return;
        }
        int item = System.identityHashCode(stack.getItem());
        if (WARNED.get(slot) != null && WARNED.get(slot) == item) return;
        WARNED.put(slot, item);
        alerts++;
        String message = "Почти сломано: " + slot + " · " + Math.round(ratio * 100) + "%";
        LavaVisual.LOGGER.info("LavaVisual durability alert: {} ({}%)", slot, Math.round(ratio * 100));
        if (c.durabilityAlert) Binds.Toast.show(message);
        if (c.durabilityAlertSound) UiSound.alert();
    }
}
