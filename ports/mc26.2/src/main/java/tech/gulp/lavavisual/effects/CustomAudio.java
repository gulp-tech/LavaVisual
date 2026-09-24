package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

/** One shared sound library for hits, crits, totems and kills; each event may use any sound or a custom file. */
public final class CustomAudio {
    /** Sound event paths (see sounds.json): 29 synthesised in tools/make_sounds.py (8 saturated hits/crits since 2.11), 6 Kenney CC0. */
    public static final String[] IDS = {"lib.bell", "lib.bubble", "lib.click", "lib.tick", "lib.punch", "lib.metal", "lib.glass",
            "lib.laser", "lib.coin", "lib.crystal", "lib.retro", "lib.bass", "lib.snap", "lib.drop", "lib.wood", "lib.zap",
            "lib.sparkle", "lib.double", "lib.thunder", "lib.fanfare", "lib.magic",
            "hit_soft", "hit_heavy", "crit_metal", "crit_arcade", "totem_chime", "totem_arcade",
            "lib.juicy", "lib.power", "lib.whip", "lib.boom", "lib.blade", "lib.burst", "lib.storm", "lib.radiant"};
    public static final String[] NAMES = {"Колокол", "Пузырь", "Щелчок", "Хитмаркер", "Панч", "Металл", "Стекло",
            "Лазер", "Монетка", "Кристалл", "Ретро", "Бас", "Хлопок", "Капля", "Дерево", "Электро",
            "Искра", "Двойной звон", "Гром", "Фанфары", "Магия",
            "Мягкий взмах", "Тяжёлый удар", "Лязг", "Аркада", "Колокольчик", "Power Up",
            "Сочный удар", "Мощный панч", "Хлёсткий удар", "Глухой бум", "Крит · Клинок", "Крит · Взрыв", "Крит · Молния", "Крит · Сияние"};
    /** First index of the 2.11 saturated sounds: 4 hits, then 4 crits. */
    public static final int RICH = 27;
    public static final int CUSTOM = IDS.length;
    private static final int[] FALLBACK = {3, 16, 19, 17};
    static {
        if (IDS.length != HudConfig.SOUND_LIBRARY || NAMES.length != IDS.length) throw new IllegalStateException("Sound library size mismatch");
    }
    private CustomAudio() { }
    /** Choices for the menu: the library plus «Свой» when custom files exist. */
    public static int count() { return CustomSounds.names().isEmpty() ? IDS.length : IDS.length + 1; }
    public static String name(int index) {
        if (index < IDS.length) return NAMES[index];
        return CustomSounds.names().isEmpty() ? "Свой (папка пуста)" : "Свой файл";
    }
    public static int selected(int group) {
        var c = LavaVisualClient.config();
        return switch (group) { case 0 -> c.hitSound; case 1 -> c.critSound; case 2 -> c.totemSound; default -> c.killSound; };
    }
    public static void select(int group, int index) {
        var c = LavaVisualClient.config();
        switch (group) { case 0 -> c.hitSound = index; case 1 -> c.critSound = index; case 2 -> c.totemSound = index; default -> c.killSound = index; }
    }
    public static Identifier sound(int group) {
        int index = selected(group);
        if (index >= IDS.length) {
            var custom = CustomSounds.names();
            if (!custom.isEmpty()) return Identifier.fromNamespaceAndPath("lavavisual", "custom/" + custom.get(group % custom.size()));
            index = FALLBACK[Math.clamp(group, 0, 3)];
        }
        return Identifier.fromNamespaceAndPath("lavavisual", IDS[Math.clamp(index, 0, IDS.length - 1)]);
    }
    private static double volume(int group) {
        var c = LavaVisualClient.config();
        return switch (group) { case 0 -> c.hitVolume; case 1 -> c.critVolume; case 2 -> c.totemVolume; default -> c.killVolume; };
    }
    public static SoundInstance replace(SoundInstance original, net.minecraft.client.sounds.SoundManager manager) {
        if (!original.getIdentifier().getNamespace().equals("minecraft")) return original;
        var c = LavaVisualClient.config();
        int group = switch (original.getIdentifier().getPath()) {
            case "entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.sweep", "entity.player.attack.knockback" -> c.hitSoundEnabled ? 0 : -1;
            case "entity.player.attack.crit" -> c.critSoundEnabled ? 1 : -1;
            case "item.totem.use" -> c.totemSoundEnabled ? 2 : -1;
            default -> -1;
        };
        if (group < 0) return original;
        // SoundManager.play receives unresolved instances (including incoming sound packets).
        // AbstractSoundInstance volume/pitch depend on its selected Sound and cannot be read yet.
        if (original.getSound() == null && original.resolve(manager) == null) return original;
        if (original.getSound() == null) return original;
        // Keep the original position, category and attenuation; never turn distant world audio into UI audio.
        return new SimpleSoundInstance(sound(group), original.getSource(), (float) (original.getVolume() * volume(group)),
                original.getPitch(), SoundInstance.createUnseededRandom(), original.isLooping(), original.getDelay(),
                original.getAttenuation(), original.getX(), original.getY(), original.getZ(), original.isRelative());
    }
    public static void preview(int group) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound(group)), 1, (float) volume(group)));
    }
    /** Kill confirmation for your own target; played like a hit marker, only for you. */
    public static void kill() {
        if (LavaVisualClient.config().killSoundEnabled) preview(3);
    }
}
