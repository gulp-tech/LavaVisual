package tech.gulp.lavavisual.effects;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import tech.gulp.lavavisual.LavaVisualClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Local practice dummy: a player model that exists only in this client's world. The server never learns about it -
 * hits and right clicks on it are cancelled before any packet is built (the swing itself is the same as swinging at
 * air), it pushes nobody and cannot be pushed. It wears your skin, armour, held items, hat and wings, flashes red,
 * loses health and plays the hit / crit / kill sounds and effects, so visuals can be tried on and tested anywhere.
 */
public final class Dummy {
    /** Negative: server entity ids are positive, so this id can never collide with a real entity. */
    public static final int ID = -1_000_417;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static DummyPlayer dummy;
    private static int ticks, lastHit = -1000, deadTicks;
    private Dummy() { }

    public static boolean active() { return dummy != null; }
    public static boolean is(int id) { return dummy != null && id == ID; }
    public static float health() { return dummy == null ? 0 : dummy.getHealth(); }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (dummy == null || entity != dummy) return InteractionResult.PASS;
            hurt(player);
            return InteractionResult.FAIL; // FAIL = cancelled on the client and no attack packet is sent
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                dummy != null && entity == dummy ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    public static void toggle(Minecraft mc) { if (dummy != null) remove(); else spawn(mc); }

    /** Places the dummy 2.6 blocks in front of you, facing you. */
    public static void spawn(Minecraft mc) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;
        remove();
        var profile = new GameProfile(UUID.nameUUIDFromBytes(("lavavisual-dummy:" + player.getUUID()).getBytes(StandardCharsets.UTF_8)), "Манекен");
        var entity = new DummyPlayer(level, profile);
        entity.setId(ID);
        double yaw = Math.toRadians(player.getYRot());
        double x = player.getX() - Math.sin(yaw) * 2.6, y = player.getY(), z = player.getZ() + Math.cos(yaw) * 2.6;
        entity.setPos(x, y, z);
        entity.xo = x; entity.yo = y; entity.zo = z;
        face(entity, player.getYRot() + 180f, true);
        copyEquipment(player, entity);
        level.addEntity(entity);
        dummy = entity;
        ticks = 0; deadTicks = 0; lastHit = -1000;
    }

    public static void remove() {
        var entity = dummy;
        dummy = null;
        if (entity == null) return;
        var level = Minecraft.getInstance().level;
        if (level != null && entity.level() == level) level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
    }

    public static void tick(Minecraft mc) {
        var entity = dummy;
        if (entity == null) return;
        if (mc.level == null || mc.player == null || entity.level() != mc.level || entity.isRemoved()) { dummy = null; return; }
        ticks++;
        if (entity.getHealth() <= 0) {
            // Lies down like a killed player, then stands up with full health.
            if (++deadTicks > 30) {
                entity.setHealth(entity.getMaxHealth());
                entity.deathTime = 0; entity.hurtTime = 0; deadTicks = 0;
                WorldCosmetics.forgetKill(ID);
            }
            return;
        }
        if (ticks - lastHit > 80 && entity.getHealth() < entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        if (LavaVisualClient.config().dummySpin) face(entity, entity.getYRot() + 3f, false);
        if (ticks % 10 == 0) copyEquipment(mc.player, entity);
    }

    /** CI smoke: the same local hit a click on the dummy performs. */
    public static void testHit(Minecraft mc) { if (mc.player != null) hurt(mc.player); }

    private static void hurt(Player player) {
        var entity = dummy;
        if (entity == null || entity.getHealth() <= 0) return;
        float strength = player.getAttackStrengthScale(0.5f);
        boolean crit = strength > 0.9f && player.fallDistance > 0 && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.isPassenger() && !player.isSprinting();
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2 + strength * strength * 0.8) * (crit ? 1.5 : 1));
        entity.setHealth(Math.max(0, entity.getHealth() - Math.max(0.5f, damage)));
        entity.hurtTime = 10;
        entity.hurtDuration = 10;
        lastHit = ticks;
        play(crit ? "entity.player.attack.crit" : strength > 0.9f ? "entity.player.attack.strong" : "entity.player.attack.weak", entity, 1f);
        play(entity.getHealth() <= 0 ? "entity.player.death" : "entity.player.hurt", entity, 0.8f);
        // Like a real hit (and like vanilla's swing at air): the attack indicator starts recharging.
        player.resetAttackStrengthTicker();
    }

    /** Local sound at the dummy; hit sounds go through the LavaVisual sound replacement like real ones. */
    private static void play(String id, Entity at, float volume) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(Identifier.fromNamespaceAndPath("minecraft", id), SoundSource.PLAYERS,
                volume, 1f, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR,
                at.getX(), at.getY() + 1, at.getZ(), false));
    }

    private static void face(DummyPlayer entity, float yaw, boolean snap) {
        entity.yRotO = snap ? yaw : entity.getYRot();
        entity.yBodyRotO = snap ? yaw : entity.yBodyRot;
        entity.yHeadRotO = snap ? yaw : entity.yHeadRot;
        entity.setYRot(yaw);
        entity.yBodyRot = yaw;
        entity.yHeadRot = yaw;
        entity.setXRot(0);
        entity.xRotO = 0;
    }

    private static void copyEquipment(Player from, Player to) {
        for (var slot : SLOTS) {
            var wanted = from.getItemBySlot(slot);
            if (!ItemStack.matches(wanted, to.getItemBySlot(slot))) to.setItemSlot(slot, wanted.copy());
        }
    }

    /** Client-only player: your skin with every outer layer shown; never pushes or gets pushed. */
    static final class DummyPlayer extends RemotePlayer {
        DummyPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
            getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        }
        @Override public PlayerSkin getSkin() {
            var self = Minecraft.getInstance().player;
            return self != null ? self.getSkin() : super.getSkin();
        }
        @Override public boolean isPushable() { return false; }
        @Override protected void doPush(Entity entity) { }
    }
}
