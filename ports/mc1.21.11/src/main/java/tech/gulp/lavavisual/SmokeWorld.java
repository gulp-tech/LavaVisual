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
    private static final int[] HATS = {1, 3, 5, 13, 14};
    private static final int WING_STEP = 60, HAT_STEP = 40, HATS_AT = 60 + 5 * WING_STEP + 10,
            DUMMY_AT = HATS_AT + HATS.length * HAT_STEP + 10, HANDS_AT = DUMMY_AT + 70, CRIT_AT = HANDS_AT + 50,
            TRAIL_AT = CRIT_AT + 50, ZOOM_AT = TRAIL_AT + 95, FREE_AT = ZOOM_AT + 40, WINGS_EDIT_AT = FREE_AT + 40,
            MAP_AT = WINGS_EDIT_AT + 45, SOUND_AT = MAP_AT + 110, MUSIC_AT = SOUND_AT + 12, FORMATS_AT = MUSIC_AT + 72, TIME_AT = FORMATS_AT + 104, ITEMS_AT = TIME_AT + 80,
            PROJ_AT = ITEMS_AT + 70, OUTFIT_AT = PROJ_AT + 50, END_AT = OUTFIT_AT + 94;
    private static float walkLift;
    private static double p1, p2, p3, p4;
    private static boolean musicPlaying, musicPaused, musicStable, musicSeek, musicNext, musicPrevious;
    private static double hiddenMs, shownMs, timeOnMs;
    private static int buildsBefore;
    private static boolean mp3Ok, mp3Seek, opusOk, wavOk, renamedOk, clipsOk, clickOk, bindOk;
    private static String formatNotes = "";
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
        // Live ping through the integrated server: the same request and answer as on a real server.
        if (ticks == 5) tech.gulp.lavavisual.hud.PingMeter.test(true);
        if (ticks == 205) { LavaVisual.LOGGER.info(tech.gulp.lavavisual.hud.PingMeter.selfTest()); tech.gulp.lavavisual.hud.PingMeter.test(false); }
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
        if (ticks == TRAIL_AT + 30) {
            int skipped = tech.gulp.lavavisual.effects.WorldCosmetics.trailSkipped, ahead = tech.gulp.lavavisual.effects.WorldCosmetics.trailAhead;
            LavaVisual.LOGGER.info((ahead == 0 && skipped > 0 ? "LavaVisual smoke trail clear ok" : "LavaVisual smoke trail clear failed") + ": skipped " + skipped + ", ahead " + ahead);
        }
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
        // Wings editor: live preview from behind with custom placement.
        if (ticks == WINGS_EDIT_AT) {
            c.wingsEnabled = true; c.wingsType = 1; c.wingsLift = 0.08; c.wingsTilt = 12; c.wingsSpread = 18; c.wingsSpeed = 1.4;
            mc.gui.setScreen(new tech.gulp.lavavisual.ui.WingsEditorScreen(null));
        }
        if (ticks == WINGS_EDIT_AT + 16) {
            boolean ok = mc.gui.screen() instanceof tech.gulp.lavavisual.ui.WingsEditorScreen && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
            LavaVisual.LOGGER.info(ok ? "LavaVisual smoke wings editor ok" : "LavaVisual smoke wings editor failed: screen=" + mc.gui.screen() + " camera=" + mc.options.getCameraType());
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_wings_editor");
        }
        if (ticks == WINGS_EDIT_AT + 40) {
            mc.gui.setScreen(null);
            c.wingsEnabled = false; c.wingsLift = 0; c.wingsTilt = 0; c.wingsSpread = 0; c.wingsSpeed = 1;
        }
        // Minimap: round window, no letters. Rainbow on the map and on the HUD background is the worst case: the
        // frame textures must not be repainted for it.
        if (ticks == MAP_AT) {
            c.widgets.get("minimap").visible = true; c.mapShape = 0; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (!c.chroma.contains("minimap")) c.chroma.add("minimap");
            if (!c.chroma.contains("hud_bg")) c.chroma.add("hud_bg");
        }
        if (ticks == MAP_AT + 3) tech.gulp.lavavisual.map.Minimap.resetCost();
        if (ticks == MAP_AT + 12) LavaVisual.LOGGER.info("LavaVisual smoke shot world_minimap");
        if (ticks == MAP_AT + 16) {
            double micros = tech.gulp.lavavisual.map.Minimap.averageMicros();
            long frames = tech.gulp.lavavisual.map.Minimap.frames();
            LavaVisual.LOGGER.info("LavaVisual smoke minimap cost {} us over {} frames", String.format(java.util.Locale.ROOT, "%.1f", micros), frames);
            if (frames > 0 && micros < 2500) LavaVisual.LOGGER.info("LavaVisual smoke minimap fast");
        }
        // Frame time with the map hidden, then shown (same scene, 2 s each).
        if (ticks == MAP_AT + 30) { c.widgets.get("minimap").visible = false; tech.gulp.lavavisual.map.Minimap.clock(true); }
        if (ticks == MAP_AT + 65) {
            hiddenMs = tech.gulp.lavavisual.map.Minimap.clockMillis();
            c.widgets.get("minimap").visible = true;
            tech.gulp.lavavisual.map.Minimap.clock(false);
        }
        if (ticks == MAP_AT + 68) { buildsBefore = tech.gulp.lavavisual.map.Minimap.builds(); tech.gulp.lavavisual.map.Minimap.clock(true); tech.gulp.lavavisual.map.Minimap.resetCost(); }
        if (ticks == MAP_AT + 103) {
            shownMs = tech.gulp.lavavisual.map.Minimap.clockMillis();
            long frames = tech.gulp.lavavisual.map.Minimap.clockFrames();
            int rebuilt = tech.gulp.lavavisual.map.Minimap.builds() - buildsBefore;
            tech.gulp.lavavisual.map.Minimap.clock(false);
            boolean ok = frames > 5 && rebuilt == 0 && shownMs <= hiddenMs * 1.25 + 2;
            LavaVisual.LOGGER.info((ok ? "LavaVisual smoke minimap fps ok" : "LavaVisual smoke minimap fps failed")
                    + String.format(java.util.Locale.ROOT, ": hidden %.2f ms, shown %.2f ms per frame (%d frames), frame repaints %d, draw %.1f us, scan tick %.1f us",
                    hiddenMs, shownMs, frames, rebuilt, tech.gulp.lavavisual.map.Minimap.averageMicros(), tech.gulp.lavavisual.map.Minimap.tickMicros()));
            c.chroma.remove("minimap"); c.chroma.remove("hud_bg");
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
                if (test != null) {
                    var replace = java.nio.file.StandardCopyOption.REPLACE_EXISTING;
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.mp3"), music.resolve("Формат MP3.mp3"), replace);
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.opus"), music.resolve("Формат Opus.opus"), replace);
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.wav"), music.resolve("Формат WAV.wav"), replace);
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.mp3"), music.resolve("MP3 под видом ogg.ogg"), replace);
                }
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
            var list = tech.gulp.lavavisual.audio.MusicPlayer.tracks();
            int at = -1;
            for (int i = 0; i < list.size(); i++) if (list.get(i).name().equals("Тестовый трек")) at = i;
            String expected = at < 0 ? "" : list.get((at + 1) % list.size()).name();
            musicNext = now != null && now.name().equals(expected) && tech.gulp.lavavisual.audio.MusicPlayer.playing();
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
        // Every format by content: MP3 with an ID3 tag and cover, Opus, WAV, and an MP3 renamed to .ogg; seeking,
        // durations and tags; clips; the real play button of the player screen and the pause bind.
        if (ticks == FORMATS_AT) mp3Ok = playNamed("Формат MP3");
        if (ticks == FORMATS_AT + 16) {
            var t = tech.gulp.lavavisual.audio.MusicPlayer.current();
            double pos = tech.gulp.lavavisual.audio.MusicPlayer.position(), len = tech.gulp.lavavisual.audio.MusicPlayer.duration();
            mp3Ok &= playingAt(0.2) && Math.abs(len - 8.05) < 0.4 && t != null && t.title().equals("Тест MP3") && t.artist().equals("LavaVisual");
            formatNotes += String.format(java.util.Locale.ROOT, " mp3 %.2f/%.2f '%s' '%s' %s;", pos, len, t == null ? "" : t.title(), t == null ? "" : t.artist(), tech.gulp.lavavisual.audio.MusicPlayer.error());
            tech.gulp.lavavisual.audio.MusicPlayer.seek(5.0);
        }
        if (ticks == FORMATS_AT + 22) {
            double pos = tech.gulp.lavavisual.audio.MusicPlayer.position();
            mp3Seek = pos >= 4.9 && pos < 6.6 && tech.gulp.lavavisual.audio.MusicPlayer.playing();
            formatNotes += String.format(java.util.Locale.ROOT, " seek %.2f;", pos);
        }
        if (ticks == FORMATS_AT + 26) opusOk = playNamed("Формат Opus");
        if (ticks == FORMATS_AT + 42) {
            var t = tech.gulp.lavavisual.audio.MusicPlayer.current();
            double len = tech.gulp.lavavisual.audio.MusicPlayer.duration();
            opusOk &= playingAt(0.2) && Math.abs(len - 6.0) < 0.4 && t != null && t.title().equals("Тест Opus");
            formatNotes += String.format(java.util.Locale.ROOT, " opus %.2f/%.2f %s;", tech.gulp.lavavisual.audio.MusicPlayer.position(), len, tech.gulp.lavavisual.audio.MusicPlayer.error());
        }
        if (ticks == FORMATS_AT + 44) wavOk = playNamed("Формат WAV");
        if (ticks == FORMATS_AT + 56) {
            wavOk &= playingAt(0.2);
            formatNotes += String.format(java.util.Locale.ROOT, " wav %.2f/%.2f %s;", tech.gulp.lavavisual.audio.MusicPlayer.position(), tech.gulp.lavavisual.audio.MusicPlayer.duration(), tech.gulp.lavavisual.audio.MusicPlayer.error());
        }
        if (ticks == FORMATS_AT + 58) {
            renamedOk = playNamed("MP3 под видом ogg");
            try {
                var hits = tech.gulp.lavavisual.effects.CustomSounds.dir().resolve("hits");
                String test = System.getProperty("lavavisual.testAudio");
                if (test != null) {
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.wav"), hits.resolve("Удар WAV.wav"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    java.nio.file.Files.copy(java.nio.file.Path.of(test, "test_track.opus"), hits.resolve("Удар Opus.opus"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    clipsOk = tech.gulp.lavavisual.audio.LavaAudio.play(hits.resolve("Удар WAV.wav"), 0.3f) && tech.gulp.lavavisual.audio.LavaAudio.play(hits.resolve("Удар Opus.opus"), 0.3f);
                }
            } catch (Exception error) {
                LavaVisual.LOGGER.warn("LavaVisual smoke clip formats failed", error);
            }
        }
        if (ticks == FORMATS_AT + 72) {
            renamedOk &= playingAt(0.2);
            formatNotes += String.format(java.util.Locale.ROOT, " renamed %.2f %s;", tech.gulp.lavavisual.audio.MusicPlayer.position(), tech.gulp.lavavisual.audio.MusicPlayer.error());
            mc.gui.setScreen(new tech.gulp.lavavisual.ui.MusicScreen(null));
        }
        if (ticks == FORMATS_AT + 78 && mc.gui.screen() instanceof tech.gulp.lavavisual.ui.MusicScreen screen) {
            boolean hit = screen.click(screen.playX(), screen.playY());
            clickOk = hit && tech.gulp.lavavisual.audio.MusicPlayer.paused();
        }
        if (ticks == FORMATS_AT + 82 && mc.gui.screen() instanceof tech.gulp.lavavisual.ui.MusicScreen screen) {
            clickOk &= screen.click(screen.playX(), screen.playY()) && tech.gulp.lavavisual.audio.MusicPlayer.playing();
            formatNotes += " button " + screen.playX() + "," + screen.playY() + ";";
        }
        if (ticks == FORMATS_AT + 86) {
            tech.gulp.lavavisual.input.Binds.press(tech.gulp.lavavisual.input.Binds.Action.MUSIC_PLAY, mc);
            bindOk = tech.gulp.lavavisual.audio.MusicPlayer.paused();
        }
        if (ticks == FORMATS_AT + 90) {
            tech.gulp.lavavisual.input.Binds.press(tech.gulp.lavavisual.input.Binds.Action.MUSIC_PLAY, mc);
            bindOk &= tech.gulp.lavavisual.audio.MusicPlayer.playing();
        }
        if (ticks == FORMATS_AT + 96) {
            boolean ok = mp3Ok && mp3Seek && opusOk && wavOk && renamedOk && clipsOk && clickOk && bindOk;
            LavaVisual.LOGGER.info((ok ? "LavaVisual smoke formats ok" : "LavaVisual smoke formats failed") + ": mp3=" + mp3Ok + " seek=" + mp3Seek + " opus=" + opusOk
                    + " wav=" + wavOk + " renamed=" + renamedOk + " clips=" + clipsOk + " click=" + clickOk + " bind=" + bindOk + " |" + formatNotes);
            mc.gui.setScreen(null);
            tech.gulp.lavavisual.audio.MusicPlayer.stop();
        }
        // Client-side time: midnight on this client only.
        // Frame time with the custom time on (midnight) and off, then the check and a shot at midnight.
        if (ticks == TIME_AT) { c.timeEnabled = true; c.timeTicks = 18000; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (ticks == TIME_AT + 4) tech.gulp.lavavisual.map.Minimap.clock(true);
        if (ticks == TIME_AT + 24) { timeOnMs = tech.gulp.lavavisual.map.Minimap.clockMillis(); c.timeEnabled = false; tech.gulp.lavavisual.map.Minimap.clock(false); }
        if (ticks == TIME_AT + 28) tech.gulp.lavavisual.map.Minimap.clock(true);
        if (ticks == TIME_AT + 48) {
            double off = tech.gulp.lavavisual.map.Minimap.clockMillis();
            tech.gulp.lavavisual.map.Minimap.clock(false);
            LavaVisual.LOGGER.info(String.format(java.util.Locale.ROOT, "LavaVisual smoke time fps: midnight %.2f ms, off %.2f ms per frame", timeOnMs, off));
            c.timeEnabled = true;
        }
        if (ticks == TIME_AT + 52) {
            var clock = mc.level.dimensionType().defaultClock();
            long shown = clock.map(h -> mc.level.clockManager().getTotalTicks(h)).orElse(-1L);
            boolean ok = clock.isPresent() && Math.floorMod(shown, 24000L) == 18000;
            LavaVisual.LOGGER.info((ok ? "LavaVisual smoke time ok" : "LavaVisual smoke time failed") + ": clock " + shown);
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_time_night");
        }
        if (ticks == TIME_AT + 76) c.timeEnabled = false;
        // Item physics: a sword (flat), a block and a stack of apples fall in front of the camera and settle.
        if (ticks == ITEMS_AT) {
            c.itemPhysics = true; c.itemPhysicsFlat = true; c.itemPhysicsSpin = 1; c.itemPhysicsSize = 1.3;
            tech.gulp.lavavisual.effects.ItemPhysics.rendered = 0;
            player.connection.sendCommand("summon item ~1.4 ~1.6 ~-3.4 {Item:{id:\"minecraft:diamond_sword\",count:1}}");
            player.connection.sendCommand("summon item ~-1.4 ~1.8 ~-3.6 {Item:{id:\"minecraft:grass_block\",count:1}}");
            player.connection.sendCommand("summon item ~0.7 ~2.4 ~-4.6 {Item:{id:\"minecraft:golden_apple\",count:3}}");
        }
        if (ticks == ITEMS_AT + 40) {
            long drawn = tech.gulp.lavavisual.effects.ItemPhysics.rendered;
            LavaVisual.LOGGER.info((drawn > 0 ? "LavaVisual smoke item physics ok" : "LavaVisual smoke item physics failed") + ": " + drawn + " item draws");
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_item_physics");
        }
        if (ticks == ITEMS_AT + 62) { player.connection.sendCommand("kill @e[type=item]"); c.itemPhysics = false; c.itemPhysicsSize = 1; }
        // Projectile trails: two volleys of a snowball, an arrow and an ender pearl.
        if (ticks == PROJ_AT) {
            c.projTrails = true; c.projOnlyMine = false; c.projStyle = 0; c.projLength = 2; c.projByItem = true; c.projGlow = true;
            volley(player);
        }
        if (ticks == PROJ_AT + 12) volley(player);
        if (ticks == PROJ_AT + 16) {
            int active = tech.gulp.lavavisual.effects.ProjectileTrails.active();
            LavaVisual.LOGGER.info((active > 0 ? "LavaVisual smoke projectile trails ok" : "LavaVisual smoke projectile trails failed") + ": " + active + " trails");
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_projectile_trails");
        }
        if (ticks == PROJ_AT + 44) {
            c.projTrails = false; c.projOnlyMine = true; c.projLength = 1;
            player.connection.sendCommand("kill @e[type=arrow]");
        }
        // Outfit: royal cape, glasses, headphones and scarf while walking, from behind and from the front.
        if (ticks == OUTFIT_AT) {
            c.hatEnabled = false; c.wingsEnabled = false; c.trailEnabled = false;
            c.capeEnabled = true; c.capeType = 6; c.capeSway = 1; c.capeStyle = 0; c.capePhysics = true;
            c.extras = new java.util.ArrayList<>(java.util.List.of(1, 2, 3));
            tech.gulp.lavavisual.effects.WorldCosmetics.capesDrawn = 0; tech.gulp.lavavisual.effects.WorldCosmetics.extrasDrawn = 0;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks > OUTFIT_AT && ticks < OUTFIT_AT + 40) player.setPos(player.getX(), player.getY(), player.getZ() - 0.22);
        if (ticks == OUTFIT_AT + 22) LavaVisual.LOGGER.info("LavaVisual smoke shot world_outfit_back");
        if (ticks == OUTFIT_AT + 30) walkLift = tech.gulp.lavavisual.effects.CapeCloth.lastLift;
        if (ticks == OUTFIT_AT + 46) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (ticks == OUTFIT_AT + 68) {
            long capes = tech.gulp.lavavisual.effects.WorldCosmetics.capesDrawn, extras = tech.gulp.lavavisual.effects.WorldCosmetics.extrasDrawn;
            boolean ok = capes > 0 && extras >= 3;
            LavaVisual.LOGGER.info((ok ? "LavaVisual smoke outfit ok" : "LavaVisual smoke outfit failed") + ": capes " + capes + ", accessories " + extras);
            float restLift = tech.gulp.lavavisual.effects.CapeCloth.lastLift;
            long steps = tech.gulp.lavavisual.effects.CapeCloth.steps;
            boolean cloth = steps > 0 && walkLift < -0.15f && restLift > walkLift + 0.1f && Float.isFinite(restLift);
            LavaVisual.LOGGER.info((cloth ? "LavaVisual smoke cape cloth ok" : "LavaVisual smoke cape cloth failed") + String.format(java.util.Locale.ROOT,
                    ": walking %.3f, standing %.3f, steps %d", walkLift, restLift, steps));
            LavaVisual.LOGGER.info("LavaVisual smoke shot world_outfit_front");
        }
        if (ticks == OUTFIT_AT + 90) { c.capeEnabled = false; c.extras.clear(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
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
    private static void volley(net.minecraft.client.player.LocalPlayer player) {
        player.connection.sendCommand("summon snowball ~0.6 ~1.7 ~-0.8 {Motion:[0.08,0.32,-0.85]}");
        player.connection.sendCommand("summon arrow ~-0.6 ~1.7 ~-0.8 {Motion:[-0.1,0.3,-1.1]}");
        player.connection.sendCommand("summon ender_pearl ~0 ~2.2 ~-0.8 {Motion:[0.0,0.42,-0.7]}");
    }
    private static boolean playNamed(String name) {
        var list = tech.gulp.lavavisual.audio.MusicPlayer.tracks();
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).name().equals(name)) continue;
            tech.gulp.lavavisual.audio.MusicPlayer.play(i);
            return tech.gulp.lavavisual.audio.MusicPlayer.active();
        }
        formatNotes += " missing " + name + ";";
        return false;
    }
    private static boolean playingAt(double seconds) {
        return tech.gulp.lavavisual.audio.MusicPlayer.playing() && tech.gulp.lavavisual.audio.MusicPlayer.position() > seconds
                && tech.gulp.lavavisual.audio.MusicPlayer.alState() == org.lwjgl.openal.AL10.AL_PLAYING;
    }
    private static int index(int group, String name) {
        var list = tech.gulp.lavavisual.effects.CustomSounds.list(group);
        for (int i = 0; i < list.size(); i++) if (list.get(i).name().equals(name)) return i;
        return -1;
    }
}
