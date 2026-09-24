package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.ui.UiDraw;

/** LavaVisual badge next to the name tag of other LavaVisual users. Works on ordinary servers via
    the relayed skin-parts marker (see Badge); the optional mod channel covers hosts running LavaVisual. */
public final class PlayerTags {
    public record TagPayload(UUID id) implements CustomPacketPayload {
        public static final Type<TagPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lavavisual", "tag"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TagPayload> CODEC = new StreamCodec<>() {
            @Override public TagPayload decode(RegistryFriendlyByteBuf buf) { return new TagPayload(buf.readUUID()); }
            @Override public void encode(RegistryFriendlyByteBuf buf, TagPayload value) { buf.writeUUID(value.id()); }
        };
        @Override public Type<TagPayload> type() { return TYPE; }
    }
    private record Tag(Vec3 anchor, float half) { }
    private static final RenderStateDataKey<List<Tag>> DATA = RenderStateDataKey.create(() -> "lavavisual:tags");
    private static final Set<UUID> KNOWN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> TAGGED = ConcurrentHashMap.newKeySet();
    private PlayerTags() { }
    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(TagPayload.TYPE, TagPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TagPayload.TYPE, TagPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TagPayload.TYPE, (payload, context) -> {
            var sender = context.player();
            UUID id = sender.getUUID();
            if (!KNOWN.add(id)) return;
            var server = context.server();
            server.execute(() -> {
                for (var player : server.getPlayerList().getPlayers()) {
                    if (player.getUUID().equals(id) || !ServerPlayNetworking.canSend(player, TagPayload.TYPE)) continue;
                    ServerPlayNetworking.send(player, new TagPayload(id));
                }
                if (ServerPlayNetworking.canSend(sender, TagPayload.TYPE))
                    for (UUID known : KNOWN) if (!known.equals(id)) ServerPlayNetworking.send(sender, new TagPayload(known));
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> KNOWN.remove(handler.getPlayer().getUUID()));
    }
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(TagPayload.TYPE, (payload, context) ->
                context.client().execute(() -> TAGGED.add(payload.id())));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TAGGED.clear();
            if (client.player != null && ClientPlayNetworking.canSend(TagPayload.TYPE))
                sender.sendPacket(new TagPayload(client.player.getUUID()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TAGGED.clear());
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var mc = Minecraft.getInstance();
            if (!LavaVisualClient.config().badgeEnabled || mc.level == null || mc.player == null) {
                context.levelState().setData(DATA, null);
                return;
            }
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var tags = new ArrayList<Tag>();
            for (var player : mc.level.players()) {
                if (player == mc.player || player.isInvisible()) continue;
                if (!TAGGED.contains(player.getUUID()) && !Badge.marked(player)) continue;
                if (player.distanceToSqr(mc.player) > 48 * 48) continue;
                Vec3 anchor = player.getPosition(partial).add(0, player.getBbHeight() + 0.39, 0);
                tags.add(new Tag(anchor, mc.font.width(player.getDisplayName()) * 0.0125f));
                if (tags.size() >= 24) break;
            }
            context.levelState().setData(DATA, tags.isEmpty() ? null : List.copyOf(tags));
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(PlayerTags::render);
    }
    private static void render(LevelRenderContext context) {
        List<Tag> tags = context.levelState().getData(DATA);
        if (tags == null || tags.isEmpty()) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Quaternionf orientation = new Quaternionf(context.levelState().cameraRenderState.orientation);
        Vector3f right = new Vector3f(1, 0, 0).rotate(orientation), up = new Vector3f(0, 1, 0).rotate(orientation);
        int accent = LavaVisualClient.config().accent();
        float spin = (System.currentTimeMillis() % 4000L) / 4000f * (float) (Math.PI * 2);
        context.poseStack().pushPose();
        try {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), WorldCosmetics.GLOW, (pose, out) -> {
                for (Tag tag : tags) {
                    float shift = -(tag.half() + 0.17f);
                    Vec3 p = tag.anchor().subtract(camera).add(right.x() * shift, right.y() * shift, right.z() * shift);
                    ring(pose, out, p, right, up, 0.10f, 0.19f, UiDraw.alpha(accent, 0.38), UiDraw.alpha(accent, 0));
                    ring(pose, out, p, right, up, 0f, 0.10f, 0xFF000000 | brighten(accent), 0xFF000000 | accent);
                    ring(pose, out, p, right, up, 0.078f, 0.10f, UiDraw.alpha(0xFFFFFF, 0.55), UiDraw.alpha(0xFFFFFF, 0.55));
                    star(pose, out, p, right, up, spin, 0.068f, 0.017f, 0xFFFFFFFF);
                }
            });
        } finally { context.poseStack().popPose(); }
    }
    private static int brighten(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return (r + (255 - r) / 3) << 16 | (g + (255 - g) / 3) << 8 | (b + (255 - b) / 3);
    }
    /** Counter-clockwise ring/disc in camera space; inner==0 draws a filled disc. */
    private static void ring(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f r, Vector3f u, float inner, float outer, int innerColor, int outerColor) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            float a = (float) (Math.PI * 2 * i / segments), b = (float) (Math.PI * 2 * (i + 1) / segments);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            vertex(pose, out, p, r, u, ca * inner, sa * inner, innerColor);
            vertex(pose, out, p, r, u, ca * outer, sa * outer, outerColor);
            vertex(pose, out, p, r, u, cb * outer, sb * outer, outerColor);
            vertex(pose, out, p, r, u, cb * inner, sb * inner, innerColor);
        }
    }
    /** Four-point sparkle, slowly rotating. */
    private static void star(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f r, Vector3f u, float spin, float length, float width, int color) {
        for (int k = 0; k < 2; k++) {
            float a = spin + k * (float) (Math.PI / 2), c = (float) Math.cos(a), s = (float) Math.sin(a);
            float lx = c * length, ly = s * length, wx = -s * width, wy = c * width;
            vertex(pose, out, p, r, u, -lx, -ly, color);
            vertex(pose, out, p, r, u, wx, wy, color);
            vertex(pose, out, p, r, u, lx, ly, color);
            vertex(pose, out, p, r, u, -wx, -wy, color);
        }
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f r, Vector3f u, float x, float y, int color) {
        out.addVertex(pose, (float) (p.x + r.x * x + u.x * y), (float) (p.y + r.y * x + u.y * y), (float) (p.z + r.z * x + u.z * y)).setColor(color);
    }
}
