package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;

/** Runs only under the explicit CI client smoke switch, after resources have loaded. */
public final class AudioRegression {
    private AudioRegression() { }
    private static SimpleSoundInstance fresh(String path) {
        return new SimpleSoundInstance(Identifier.fromNamespaceAndPath("minecraft", path), SoundSource.PLAYERS,
                .8f, 1.2f, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.LINEAR, 12.5, 64, -7.25, false);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Audio regression: " + message);
    }
    public static void run(Minecraft client) {
        if (!Boolean.getBoolean("lavavisual.uiSmoke")) return;
        var c = LavaVisualClient.config(); var manager = client.getSoundManager();
        boolean hit = c.hitSoundEnabled, crit = c.critSoundEnabled, totem = c.totemSoundEnabled;
        int hs = c.hitSound, cs = c.critSound, ts = c.totemSound, ks = c.killSound;
        double hv = c.hitVolume, cv = c.critVolume, tv = c.totemVolume;
        try {
            c.hitSoundEnabled = c.critSoundEnabled = c.totemSoundEnabled = false;
            var disabled = fresh("entity.player.attack.strong");
            check(CustomAudio.replace(disabled, manager) == disabled && disabled.getSound() == null, "disabled must not resolve or replace");
            c.hitSoundEnabled = c.critSoundEnabled = c.totemSoundEnabled = true;
            c.hitVolume = .25; c.critVolume = .5; c.totemVolume = .75;
            String[] paths = {"entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.sweep",
                    "entity.player.attack.knockback", "entity.player.attack.crit", "item.totem.use"};
            check(CustomAudio.IDS.length == tech.gulp.lavavisual.config.HudConfig.SOUND_LIBRARY, "library size");
            for (int preset = 0; preset < CustomAudio.IDS.length; preset++) {
                c.hitSound = c.critSound = c.totemSound = c.killSound = preset;
                check(new SimpleSoundInstance(CustomAudio.sound(3), SoundSource.PLAYERS, 1, 1, SoundInstance.createUnseededRandom(), false, 0,
                        SoundInstance.Attenuation.NONE, 0, 0, 0, true).resolve(manager) != null, "kill sound resolves: " + CustomAudio.IDS[preset]);
                for (String path : paths) {
                    var original = fresh(path);
                    check(original.getSound() == null, "must start unresolved: " + path);
                    var replacement = CustomAudio.replace(original, manager);
                    int group = path.endsWith("crit") ? 1 : path.equals("item.totem.use") ? 2 : 0;
                    check(replacement != original && replacement.getIdentifier().equals(CustomAudio.sound(group)), "replacement: " + path);
                    check(original.getSound() != null, "original must resolve before volume/pitch are read");
                    check(replacement.resolve(manager) != null && replacement.getSound() != null, "custom asset resolves");
                    check(Float.isFinite(replacement.getVolume()) && Float.isFinite(replacement.getPitch()), "finite audio parameters");
                    double multiplier = group == 0 ? .25 : group == 1 ? .5 : .75;
                    check(Math.abs(replacement.getVolume() - original.getVolume() * multiplier) < .0001, "volume multiplier");
                    check(Math.abs(replacement.getPitch() - original.getPitch()) < .0001, "pitch preserved");
                    check(replacement.getX() == 12.5 && replacement.getY() == 64 && replacement.getZ() == -7.25, "position preserved");
                    check(replacement.getSource() == SoundSource.PLAYERS && replacement.getAttenuation() == SoundInstance.Attenuation.LINEAR
                            && !replacement.isRelative() && !replacement.isLooping() && replacement.getDelay() == 0, "spatial metadata preserved");
                    // Exercise the actual injected path with another unresolved packet-style instance.
                    if (preset < 2) manager.play(fresh(path));
                }
            }
            var unrelated = fresh("block.stone.break");
            check(CustomAudio.replace(unrelated, manager) == unrelated && unrelated.getSound() == null, "unrelated sound untouched");
            var missing = new SimpleSoundInstance(Identifier.fromNamespaceAndPath("minecraft", "entity.player.attack.strong"),
                    SoundSource.PLAYERS, 1, 1, SoundInstance.createUnseededRandom(), false, 0,
                    SoundInstance.Attenuation.LINEAR, 0, 0, 0, false) {
                @Override public WeighedSoundEvents resolve(SoundManager ignored) { return null; }
                @Override public float getVolume() { throw new IllegalStateException("unresolved volume read"); }
                @Override public float getPitch() { throw new IllegalStateException("unresolved pitch read"); }
            };
            check(CustomAudio.replace(missing, manager) == missing, "missing asset falls back without reading volume");
            LavaVisual.LOGGER.info("LavaVisual audio regression passed");
        } finally {
            c.hitSoundEnabled = hit; c.critSoundEnabled = crit; c.totemSoundEnabled = totem;
            c.hitSound = hs; c.critSound = cs; c.totemSound = ts; c.killSound = ks;
            c.hitVolume = hv; c.critVolume = cv; c.totemVolume = tv;
        }
    }
}
