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
    /** Sound event paths (see sounds.json): 29 synthesised in tools/make_sounds.py (8 of them saturated hits/crits), 6 Kenney CC0. */
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
    /** First index of the saturated sounds: 4 hits, then 4 crits. */
    public static final int RICH = 27;
    public static final int CUSTOM = IDS.length;
    private static final int[] FALLBACK = {3, 16, 19, 17};
    static {
        if (IDS.length != HudConfig.SOUND_LIBRARY || NAMES.length != IDS.length) throw new IllegalStateException("Sound library size mismatch");
    }
    private CustomAudio() { }
    /** Choices for one event: the library, then the user's files for it (its folder + the shared sounds folder). */
    public static int count(int group) { return IDS.length + CustomSounds.list(group).size(); }
    public static String name(int group, int index) {
        if (index < IDS.length) return NAMES[Math.max(0, index)];
        var list = CustomSounds.list(group);
        int i = index - IDS.length;
        return i < list.size() ? "★ " + list.get(i).name() + (list.get(i).playable() ? "" : " · не читается") : "★ файл удалён";
    }
    private static String key(int group) {
        var c = LavaVisualClient.config();
        String key = switch (group) { case 0 -> c.hitCustom; case 1 -> c.critCustom; case 2 -> c.totemCustom; default -> c.killCustom; };
        return key == null ? "" : key;
    }
    /** The chosen user file for the event, or null (library sound). */
    public static CustomSounds.Entry custom(int group) {
        var c = LavaVisualClient.config();
        int raw = switch (group) { case 0 -> c.hitSound; case 1 -> c.critSound; case 2 -> c.totemSound; default -> c.killSound; };
        if (raw < IDS.length) return null;
        var list = CustomSounds.list(group);
        String key = key(group);
        for (var entry : list) if (entry.key().equals(key)) return entry;
        return key.isEmpty() && !list.isEmpty() ? list.getFirst() : null; // an old single «Свой файл» choice
    }
    public static int selected(int group) {
        var c = LavaVisualClient.config();
        int raw = switch (group) { case 0 -> c.hitSound; case 1 -> c.critSound; case 2 -> c.totemSound; default -> c.killSound; };
        if (raw < IDS.length) return Math.max(0, raw);
        var entry = custom(group);
        return entry == null ? FALLBACK[Math.clamp(group, 0, 3)] : IDS.length + CustomSounds.list(group).indexOf(entry);
    }
    public static void select(int group, int index) {
        var c = LavaVisualClient.config();
        String key = "";
        if (index >= IDS.length) {
            var list = CustomSounds.list(group);
            int i = index - IDS.length;
            if (i >= list.size()) return;
            key = list.get(i).key();
            index = CUSTOM;
        }
        switch (group) {
            case 0 -> { c.hitSound = index; c.hitCustom = key; }
            case 1 -> { c.critSound = index; c.critCustom = key; }
            case 2 -> { c.totemSound = index; c.totemCustom = key; }
            default -> { c.killSound = index; c.killCustom = key; }
        }
        prewarm();
    }
    /** Library sound id for the vanilla pipeline (a user file falls back to the event's default when it is missing). */
    public static Identifier sound(int group) {
        int index = selected(group);
        if (index >= IDS.length) index = FALLBACK[Math.clamp(group, 0, 3)];
        return Identifier.fromNamespaceAndPath("lavavisual", IDS[Math.clamp(index, 0, IDS.length - 1)]);
    }
    /** Decodes the chosen user sounds in the background, so the first hit plays them without a hitch. */
    public static void prewarm() {
        java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>(4);
        for (int g = 0; g < 4; g++) {
            var entry = custom(g);
            if (entry != null && entry.playable()) files.add(entry.file());
        }
        if (!files.isEmpty()) tech.gulp.lavavisual.audio.LavaAudio.prewarm(files);
    }
    /** Plays the event's user file through OpenAL; false when there is none (or it cannot play). */
    private static boolean playCustom(int group, double gain) {
        var entry = custom(group);
        return entry != null && entry.playable() && tech.gulp.lavavisual.audio.LavaAudio.play(entry.file(), (float) gain);
    }
    private static float category(net.minecraft.sounds.SoundSource source) {
        var options = Minecraft.getInstance().options;
        return options == null ? 1 : options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) * options.getSoundSourceVolume(source);
    }
    private static SoundInstance silent(SoundInstance original) {
        return new SimpleSoundInstance(original.getIdentifier(), original.getSource(), 0f, 1f, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, original.getX(), original.getY(), original.getZ(), original.isRelative());
    }
    private static double volume(int group) {
        var c = LavaVisualClient.config();
        return switch (group) { case 0 -> c.hitVolume; case 1 -> c.critVolume; case 2 -> c.totemVolume; default -> c.killVolume; };
    }
    public static SoundInstance replace(SoundInstance original, net.minecraft.client.sounds.SoundManager manager) {
        if (!original.getIdentifier().getNamespace().equals("minecraft")) return original;
        var c = LavaVisualClient.config();
        int group = switch (original.getIdentifier().getPath()) {
            case "entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.sweep", "entity.player.attack.knockback",
                 "entity.player.attack.nodamage" -> c.hitSoundEnabled ? 0 : -1;
            // Without a separate crit sound a crit plays your hit sound (vanilla plays only the crit sound on crits).
            case "entity.player.attack.crit" -> c.critSoundEnabled ? 1 : c.hitSoundEnabled ? 0 : -1;
            case "item.totem.use" -> c.totemSoundEnabled ? 2 : -1;
            default -> -1;
        };
        if (group < 0) {
            // Your hit sound replaces vanilla completely: the target's hurt sound right after your own hit is muted.
            String path = original.getIdentifier().getPath();
            if (c.hitSoundEnabled && c.muteVanillaHits && path.startsWith("entity.") && path.endsWith(".hurt")
                    && WorldCosmetics.recentHitNear(original.getX(), original.getY(), original.getZ()))
                return silent(original);
            return original;
        }
        // A user file: played directly (OpenAL), the vanilla sound is muted. Distant hits of other players get quieter.
        if (custom(group) != null) {
            double distance = 0;
            var player = Minecraft.getInstance().player;
            if (!original.isRelative() && player != null) distance = Math.sqrt(player.distanceToSqr(original.getX(), original.getY(), original.getZ()));
            double gain = volume(group) * category(original.getSource()) * Math.max(0, 1 - distance / 24);
            if (gain <= 0.001 || playCustom(group, gain)) return silent(original);
        }
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
        if (playCustom(group, volume(group) * category(net.minecraft.sounds.SoundSource.PLAYERS))) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound(group)), 1, (float) volume(group)));
    }
    /** Kill confirmation for your own target; played like a hit marker, only for you. */
    public static void kill() {
        if (LavaVisualClient.config().killSoundEnabled) preview(3);
    }
}
