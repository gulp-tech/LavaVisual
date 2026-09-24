package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import tech.gulp.lavavisual.LavaVisualClient;

public final class CustomAudio {
    private static final String[][] SOUNDS = {{"hit_soft", "hit_heavy"}, {"crit_metal", "crit_arcade"}, {"totem_chime", "totem_arcade"}};
    private CustomAudio() { }
    public static Identifier sound(int group) {
        var c = LavaVisualClient.config();
        int preset = group == 0 ? c.hitPreset : group == 1 ? c.critPreset : c.totemPreset;
        return Identifier.fromNamespaceAndPath("lavavisual", SOUNDS[group][Math.floorMod(preset, 2)]);
    }
    private static double volume(int group) {
        var c = LavaVisualClient.config();
        return group == 0 ? c.hitVolume : group == 1 ? c.critVolume : c.totemVolume;
    }
    public static SoundInstance replace(SoundInstance original) {
        if (!original.getIdentifier().getNamespace().equals("minecraft")) return original;
        var c = LavaVisualClient.config();
        int group = switch (original.getIdentifier().getPath()) {
            case "entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.sweep", "entity.player.attack.knockback" -> c.hitSoundEnabled ? 0 : -1;
            case "entity.player.attack.crit" -> c.critSoundEnabled ? 1 : -1;
            case "item.totem.use" -> c.totemSoundEnabled ? 2 : -1;
            default -> -1;
        };
        if (group < 0) return original;
        // Keep the original position, category and attenuation; never turn distant world audio into UI audio.
        return new SimpleSoundInstance(sound(group), original.getSource(), (float) (original.getVolume() * volume(group)),
                original.getPitch(), SoundInstance.createUnseededRandom(), original.isLooping(), original.getDelay(),
                original.getAttenuation(), original.getX(), original.getY(), original.getZ(), original.isRelative());
    }
    public static void preview(int group) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound(group)), 1, (float) volume(group)));
    }
}
