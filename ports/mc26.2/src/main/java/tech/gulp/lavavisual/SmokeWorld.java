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
    private static final int WING_STEP = 90, HAT_STEP = 40, HATS_AT = 60 + 5 * WING_STEP + 10,
            DUMMY_AT = HATS_AT + HATS.length * HAT_STEP + 10, HANDS_AT = DUMMY_AT + 70, END_AT = HANDS_AT + 50;
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
            if (at == 45) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (at == 80) LavaVisual.LOGGER.info("LavaVisual smoke shot world_wings" + w + "_front");
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
        if (ticks == END_AT) finish(null);
    }

    private static void finish(String problem) {
        if (problem != null) LavaVisual.LOGGER.warn(problem);
        stage = 3;
        LavaVisual.LOGGER.info("LavaVisual UI smoke complete");
    }
}
