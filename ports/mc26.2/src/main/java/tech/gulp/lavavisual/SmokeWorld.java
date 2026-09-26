package tech.gulp.lavavisual;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.Difficulty;

/**
 * CI only (-Dlavavisual.uiSmoke=true): creates a flat peaceful world after the menu smoke and asks
 * tools/client_smoke.py for third-person screenshots of every wing model (back and front) and several hats, so the
 * cosmetics are judged in the real game renderer instead of the Python preview.
 */
final class SmokeWorld {
    private static final int[] HATS = {1, 3, 5, 13};
    private static final int WING_STEP = 60, HAT_STEP = 40, HATS_AT = 60 + 5 * WING_STEP + 10,
            DUMMY_AT = HATS_AT + HATS.length * HAT_STEP + 10, HANDS_AT = DUMMY_AT + 70, CRIT_AT = HANDS_AT + 50,
            TRAIL_AT = CRIT_AT + 50, ZOOM_AT = TRAIL_AT + 95, FREE_AT = ZOOM_AT + 40, MAP_AT = FREE_AT + 40, SOUND_AT = MAP_AT + 20,
            MUSIC_AT = SOUND_AT + 12, END_AT = MUSIC_AT + 72;
    private static double p1, p2, p3, p4;
    private static boolean musicPlaying, musicPaused, musicStable, musicSeek, musicNext, musicPrevious;
    private static int stage = -1, ticks;
    private SmokeWorld() { }

    static void start(Minecraft mc) {
        stage = 0;
        ticks = 0;
        mc.gui.setScreen(null);
        CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(null));
    }

    static void tick(Minecraft mc) {
        if (stage < 0 || stage > 2) return;
        ticks++;
        if (stage == 0) {
            if (mc.gui.screen() instanceof CreateWorldScreen screen) {
                var ui = screen.getUiState();
                ui.setName("LavaSmoke");
                ui.setAllowCommands(true);
                ui.setDifficulty(Difficulty.PEACEFUL);
                for (var entry : ui.getNormalPresetList()) {
                    if (String.valueOf(entry).contains("minecraft:flat")) { ui.setWorldType(entry); break; }
                }
                try {
                    var create = CreateWorldScreen.class.getDeclaredMethod("onCreate");
                    create.setAccessible(true);
                    create.invoke(screen);
                    LavaVisual.LOGGER.info("LavaVisual smoke world creating");
                    stage = 1;
                    ticks = 0;
                } catch (ReflectiveOperationException | RuntimeException error) {
                    finish("LavaVisual smoke world could not be created: " + error);
                }
            } else if (ticks > 900) finish("LavaVisual smoke world: the create screen never opened");
            return;
        }
        var player = mc.player;
        if (stage == 1) {
            if (mc.level != null && player != null && mc.gui.screen() == null) {
                stage = 2;
                ticks = 0;
                var c = LavaVisualClient.config();
                c.hatEnabled = true; c.hatType = 1; c.wingsEnabled = true; c.wingsType = 1;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                player.connection.sendCommand("time set 6000");
                player.connection.sendCommand("weather clear");
                player.connection.sendCommand("attribute @s minecraft:camera_distance base set 2.6");
                LavaVisual.LOGGER.info("LavaVisual smoke world ready");
            } else if (ticks > 2400) finish("LavaVisual smoke world: the world never loaded");
            return;
        }
        if (player == null) { finish("LavaVisual smoke world: player left"); return; }
        // Hold a fixed pose so every screenshot shows the same angle.
        player.setYRot(180f); player.yRotO = 180f; player.setXRot(10f); player.xRotO = 10f;
        player.yBodyRot = 180f; player.yBodyRotO = 180f; player.yHeadRot = 180f; player.yHeadRotO = 180f;
        var c = LavaVisualClient.config();
        if (ticks >= 60 && ticks < HATS_AT - 10) {
            int w = (ticks - 60) / WING_STEP + 1, at = (ticks - 60) % WING_STEP;
            if (at == 0) { c.wingsType = w; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            if (at == 35) LavaVisual.LOGGER.info("LavaVisual smoke shot world_wings" + w + "_back");
            if (at == 40) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (at == 55) LavaVisual.LOGGER.info("LavaVisual smoke shot world_wings" + w + "_front");
        }
        if (ticks == HATS_AT - 10) { c.wingsEnabled = false; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
        if (ticks >= HATS_AT && ticks < HATS_AT + HATS.length * HAT_STEP) {
            int i = (ticks - HATS_AT) / HAT_STEP, at = (ticks - HATS_AT) % HAT_STEP;
            if (at == 0) c.hatType = HATS[i];
            if (at == 30) LavaVisual.LOGGER.info("LavaVisual smoke shot world_hat" + HATS[i]);
        }
        // Local dummy wearing your hat and wings, seen from behind you; then one local hit (no packets).
        if (ticks == DUMMY_AT) {
            c.hatEnabled = true; c.hatType = 1; c.wingsEnabled = true; c.wingsType = 1;
            tech.gulp.lavavisual.effects.Dummy.spawn(mc);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks == DUMMY_AT + 30) LavaVisual.LOGGER.info("LavaVisual smoke shot world_dummy");
        if (ticks == DUMMY_AT + 45) {
            float before = tech.gulp.lavavisual.effects.Dummy.health();
            tech.gulp.lavavisual.effects.Dummy.testHit(mc);
            float after = tech.gulp.lavavisual.effects.Dummy.health();
            if (tech.gulp.lavavisual.effects.Dummy.active() && after < before) LavaVisual.LOGGER.info("LavaVisual smoke dummy ok " + before + " -> " + after);
            else LavaVisual.LOGGER.warn("LavaVisual smoke dummy failed: active=" + tech.gulp.lavavisual.effects.Dummy.active() + " " + before + " -> " + after);
        }
        // Hand editor: opening it must switch the view model on, and the edited values must move the real hand.
        if (ticks == HANDS_AT) {
            tech.gulp.lavavisual.effects.Dummy.remove();
            c.hatEnabled = false; c.wingsEnabled = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            c.viewModelEnabled = false;
            mc.gui.setScreen(new tech.gulp.lavavisual.ui.HandEditorScreen(null));
            c.mainHand.x = -0.3; c.mainHand.y = 0.12; c.mainHand.yaw = 35; c.mainHand.roll = -20;
            LavaVisual.LOGGER.info(c.viewModelEnabled ? "LavaVisual smoke hand editor ok" : "LavaVisual smoke hand editor failed: view model stayed off");
        }
        if (ticks == HANDS_AT + 30) LavaVisual.LOGGER.info("LavaVisual smoke shot world_hands");
        if (ticks == HANDS_AT + 40) { mc.gui.setScreen(null); c.mainHand = new tech.gulp.lavavisual.config.HudConfig.Hand(); c.viewModelEnabled = false; }
        // Saturated crit on the dummy (extra vanilla crit emitters + coloured stars).
        if (ticks == CRIT_AT) {
            tech.gulp.lavavisual.effects.Dummy.spawn(mc);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            c.critBoost = true; c.critColored = true; c.critMagic = true; c.critMultiplier = 4;
        }
        if (ticks == CRIT_AT + 25 && mc.level.getEntity(tech.gulp.lavavisual.effects.Dummy.ID) instanceof net.minecraft.world.entity.LivingEntity target)
            tech.gulp.lavavisual.effects.WorldCosmetics.testCrit(mc, target);
        if (ticks == CRIT_AT + 27) LavaVisual.LOGGER.info("LavaVisual smoke shot world_crit");
        // Trails: walk sideways so the trail stretches across the view; ribbon, then helix.
        if (ticks == TRAIL_AT) { tech.gulp.lavavisual.effects.Dummy.remove(); c.trailEnabled = true; c.trailStyle = 0; c.trailLength = 1.6; }
        if (ticks == TRAIL_AT + 55) c.trailStyle = 2;
        if (ticks >= TRAIL_AT && ticks < TRAIL_AT + 90) {
            double dir = ticks < TRAIL_AT + 50 ? 1 : -1;
            player.setPos(player.getX() + 0.28 * dir, player.getY(), player.getZ());
        }
        if (ticks == TRAIL_AT + 32) LavaVisual.LOGGER.info("LavaVisual smoke shot world_trail");
        if (ticks == TRAIL_AT + 86) LavaVisual.LOGGER.info("LavaVisual smoke shot world_trail_helix");
        // Zoom and FreeLook (camera only).
        if (ticks == ZOOM_AT) { c.trailEnabled = false; tech.gulp.lavavisual.effects.CameraControl.force(4, false); }
        if (ticks == ZOOM_AT + 25) {
            LavaVisual.LOGGER.info(tech.gulp.lavavisual.effects.CameraControl.zooming() ? "LavaVisual smoke zoom ok" : "LavaVisual smoke zoom failed");
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_zoom");
        }
        if (ticks == ZOOM_AT + 32) tech.gulp.lavavisual.effects.CameraControl.force(0, false);
        if (ticks == FREE_AT) tech.gulp.lavavisual.effects.CameraControl.force(0, true);
        if (ticks == FREE_AT + 3) tech.gulp.lavavisual.effects.CameraControl.orbit(140);
        if (ticks == FREE_AT + 25) {
            boolean ok = tech.gulp.lavavisual.effects.CameraControl.freeLook() && tech.gulp.lavavisual.effects.CameraControl.hooked
                    && Math.abs(player.getYRot() - 180f) < 0.01f;
            LavaVisual.LOGGER.info(ok ? "LavaVisual smoke freelook ok" : "LavaVisual smoke freelook failed: hooked=" + tech.gulp.lavavisual.effects.CameraControl.hooked);
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_freelook");
        }
        if (ticks == FREE_AT + 32) tech.gulp.lavavisual.effects.CameraControl.force(0, false);
        // Minimap: round window, no letters.
        if (ticks == MAP_AT) { c.widgets.get("minimap").visible = true; c.mapShape = 0; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (ticks == MAP_AT + 3) tech.gulp.lavavisual.map.Minimap.resetCost();
        if (ticks == MAP_AT + 12) LavaVisual.LOGGER.info("LavaVisual smoke shot world_minimap");
        if (ticks == MAP_AT + 16) {
            double micros = tech.gulp.lavavisual.map.Minimap.averageMicros();
            long frames = tech.gulp.lavavisual.map.Minimap.frames();
            LavaVisual.LOGGER.info("LavaVisual smoke minimap cost {} us over {} frames", String.format(java.util.Locale.ROOT, "%.1f", micros), frames);
            if (frames > 0 && micros < 2500) LavaVisual.LOGGER.info("LavaVisual smoke minimap fast");
        }
        // Custom sounds: Cyrillic file names in the per-event folders, listed, previewed, and replacing the totem sound.
        if (ticks == SOUND_AT) {
            try {
                tech.gulp.lavavisual.effects.CustomSounds.ensureFolders();
                java.nio.file.Path dir = tech.gulp.lavavisual.effects.CustomSounds.dir();
                copy("/assets/lavavisual/sounds/crit_arcade.ogg", dir.resolve("hits").resolve("Мой удар.ogg"));
                copy("/assets/lavavisual/sounds/totem_chime.ogg", dir.resolve("totems").resolve("Тотем тест.ogg"));
                tech.gulp.lavavisual.effects.CustomSounds.scan();
                int hit = index(0, "Мой удар"), totem = index(2, "Тотем тест");
                boolean listed = hit >= 0 && totem >= 0;
                if (listed) {
                    tech.gulp.lavavisual.effects.CustomAudio.select(0, tech.gulp.lavavisual.effects.CustomAudio.IDS.length + hit);
                    tech.gulp.lavavisual.effects.CustomAudio.select(2, tech.gulp.lavavisual.effects.CustomAudio.IDS.length + totem);
                }
                c.hitSoundEnabled = true; c.totemSoundEnabled = true;
                boolean named = tech.gulp.lavavisual.effects.CustomAudio.name(2, tech.gulp.lavavisual.effects.CustomAudio.selected(2)).contains("Тотем тест");
                tech.gulp.lavavisual.effects.CustomAudio.preview(2);
                boolean preview = tech.gulp.lavavisual.audio.LavaAudio.lastStarted();
                var vanilla = new net.minecraft.client.resources.sounds.SimpleSoundInstance(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "item.totem.use"),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1, 1, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(), false, 0,
                        net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR, player.getX(), player.getY(), player.getZ(), false);
                var replaced = tech.gulp.lavavisual.effects.CustomAudio.replace(vanilla, mc.getSoundManager());
                boolean silent = replaced != vanilla && replaced.resolve(mc.getSoundManager()) != null && replaced.getVolume() == 0f;
                boolean played = tech.gulp.lavavisual.audio.LavaAudio.lastStarted();
                boolean ok = listed && named && preview && silent && played;
                LavaVisual.LOGGER.info(ok ? "LavaVisual smoke custom sounds ok" : "LavaVisual smoke custom sounds failed: listed=" + listed + " named=" + named
                        + " preview=" + preview + " silent=" + silent + " played=" + played + " openal=" + tech.gulp.lavavisual.audio.LavaAudio.ready() + " files=" + tech.gulp.lavavisual.effects.CustomSounds.files());
            } catch (Exception error) {
                LavaVisual.LOGGER.warn("LavaVisual smoke custom sounds failed", error);
            }
        }
        // Music: a 12 s test track with a cover next to it, play, pause, stay paused, seek, next, previous, HUD and player.
        if (ticks == MUSIC_AT) {
            try {
                java.nio.file.Path music = tech.gulp.lavavisual.effects.CustomSounds.musicDir();
                String test = System.getProperty("lavavisual.testAudio");
                if (test != null) {
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.ogg"), music.resolve("Тестовый трек.ogg"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.png"), music.resolve("Тестовый трек.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                copy("/assets/lavavisual/sounds/crit_arcade.ogg", music.resolve("Второй трек.ogg"));
                tech.gulp.lavavisual.audio.MusicPlayer.rescan(music);
                c.musicRepeat = 1; c.musicShuffle = false; c.musicVolume = 0.5; c.musicHudAuto = true;
                c.widgets.get("music").visible = true;
                var tracks = tech.gulp.lavavisual.audio.MusicPlayer.tracks();
                for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).name().equals("Тестовый трек")) tech.gulp.lavavisual.audio.MusicPlayer.play(i);
            } catch (Exception error) {
                LavaVisual.LOGGER.warn("LavaVisual smoke music setup failed", error);
            }
        }
        if (ticks == MUSIC_AT + 20) {
            p1 = tech.gulp.lavavisual.audio.MusicPlayer.position();
            musicPlaying = tech.gulp.lavavisual.audio.MusicPlayer.playing() && tech.gulp.lavavisual.audio.MusicPlayer.alState() == org.lwjgl.openal.AL10.AL_PLAYING && p1 > 0.2;
            tech.gulp.lavavisual.audio.MusicPlayer.toggle();
        }
        if (ticks == MUSIC_AT + 24) {
            p2 = tech.gulp.lavavisual.audio.MusicPlayer.position();
            musicPaused = tech.gulp.lavavisual.audio.MusicPlayer.paused() && tech.gulp.lavavisual.audio.MusicPlayer.alState() == org.lwjgl.openal.AL10.AL_PAUSED;
        }
        if (ticks == MUSIC_AT + 32) {
            p3 = tech.gulp.lavavisual.audio.MusicPlayer.position();
            musicStable = Math.abs(p3 - p2) < 0.05;
            tech.gulp.lavavisual.audio.MusicPlayer.toggle();
            tech.gulp.lavavisual.audio.MusicPlayer.seek(6.0);
        }
        if (ticks == MUSIC_AT + 36) {
            p4 = tech.gulp.lavavisual.audio.MusicPlayer.position();
            musicSeek = p4 >= 5.9 && p4 < 7.6;
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_music_hud");
        }
        if (ticks == MUSIC_AT + 44) tech.gulp.lavavisual.audio.MusicPlayer.next(false);
        if (ticks == MUSIC_AT + 47) {
            var now = tech.gulp.lavavisual.audio.MusicPlayer.current();
            musicNext = now != null && now.name().equals("Второй трек") && tech.gulp.lavavisual.audio.MusicPlayer.playing();
            tech.gulp.lavavisual.audio.MusicPlayer.previous();
        }
        if (ticks == MUSIC_AT + 50) {
            var now = tech.gulp.lavavisual.audio.MusicPlayer.current();
            musicPrevious = now != null && now.name().equals("Тестовый трек");
            mc.gui.setScreen(new tech.gulp.lavavisual.ui.MusicScreen(null));
        }
        if (ticks == MUSIC_AT + 64) LavaVisual.LOGGER.info("LavaVisual smoke shot world_music_player");
        if (ticks == MUSIC_AT + 70) {
            boolean ok = musicPlaying && musicPaused && musicStable && musicSeek && musicNext && musicPrevious;
            LavaVisual.LOGGER.info((ok ? "LavaVisual smoke music ok" : "LavaVisual smoke music failed") + ": playing=" + musicPlaying + " paused=" + musicPaused
                    + " stable=" + musicStable + " seek=" + musicSeek + " next=" + musicNext + " previous=" + musicPrevious
                    + String.format(java.util.Locale.ROOT, " positions=%.2f/%.2f/%.2f/%.2f", p1, p2, p3, p4) + " openal=" + tech.gulp.lavavisual.audio.LavaAudio.ready());
            mc.gui.setScreen(null);
            tech.gulp.lavavisual.audio.MusicPlayer.stop();
        }
        if (ticks == END_AT) finish(null);
    }

    private static void finish(String problem) {
        if (problem != null) LavaVisual.LOGGER.warn(problem);
        stage = 3;
        LavaVisual.LOGGER.info("LavaVisual UI smoke complete");
    }
    private static void copy(String asset, java.nio.file.Path target) throws java.io.IOException {
        try (var in = SmokeWorld.class.getResourceAsStream(asset)) {
            if (in == null) throw new java.io.IOException("missing " + asset);
            java.nio.file.Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static int index(int group, String name) {
        var list = tech.gulp.lavavisual.effects.CustomSounds.list(group);
        for (int i = 0; i < list.size(); i++) if (list.get(i).name().equals(name)) return i;
        return -1;
    }
}
