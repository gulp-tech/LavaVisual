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

/** Shows a small badge above players who also run LavaVisual, when the server routes the channel. */
public final class PlayerTags {
    public record TagPayload(UUID id) implements CustomPacketPayload {
        public static final Type<TagPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lavavisual", "tag"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TagPayload> CODEC = new StreamCodec<>() {
            @Override public TagPayload decode(RegistryFriendlyByteBuf buf) { return new TagPayload(buf.readUUID()); }
            @Override public void encode(RegistryFriendlyByteBuf buf, TagPayload value) { buf.writeUUID(value.id()); }
        };
        @Override public Type<TagPayload> type() { return TYPE; }
    }
    private static final RenderStateDataKey<List<Vec3>> DATA = RenderStateDataKey.create(() -> "lavavisual:tags");
    private static final Set<UUID> KNOWN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> TAGGED = ConcurrentHashMap.newKeySet();
    private PlayerTags() { }
    public static void registerCommon() {
        PayloadTypeRegistry.playC2S().register(TagPayload.TYPE, TagPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TagPayload.TYPE, TagPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TagPayload.TYPE, (payload, context) -> {
            var sender = context.player().getUUID();
            KNOWN.add(sender);
            context.server().execute(() -> {
                for (var player : context.server().getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(sender)) ServerPlayNetworking.send(player, payload);
                    for (UUID known : KNOWN) if (!known.equals(player.getUUID()))
                        ServerPlayNetworking.send(player, new TagPayload(known));
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> KNOWN.remove(handler.getPlayer().getUUID()));
    }
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(TagPayload.TYPE, (payload, context) ->
                context.client().execute(() -> TAGGED.add(payload.id())));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TAGGED.clear();
            if (client.player != null) sender.sendPacket(new TagPayload(client.player.getUUID()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { TAGGED.clear(); KNOWN.clear(); });
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var level = Minecraft.getInstance().level;
            if (TAGGED.isEmpty() || level == null) { context.levelState().setData(DATA, null); return; }
            var positions = new ArrayList<Vec3>();
            for (var player : level.players()) {
                if (TAGGED.contains(player.getUUID()) && !player.isInvisible()) {
                    positions.add(player.getEyePosition().add(0, 0.55, 0));
                    if (positions.size() >= 24) break;
                }
            }
            context.levelState().setData(DATA, positions.isEmpty() ? null : List.copyOf(positions));
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(PlayerTags::render);
    }
    private static void render(LevelRenderContext context) {
        List<Vec3> positions = context.levelState().getData(DATA);
        if (positions == null || positions.isEmpty()) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Quaternionf orientation = new Quaternionf(context.levelState().cameraRenderState.orientation);
        Vector3f right = new Vector3f(1, 0, 0).rotate(orientation), up = new Vector3f(0, 1, 0).rotate(orientation);
        int accent = LavaVisualClient.config().accent();
        context.poseStack().pushPose();
        try {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), WorldCosmetics.GLOW, (pose, out) -> {
                for (Vec3 origin : positions) {
                    Vec3 p = origin.subtract(camera);
                    diamond(pose, out, p, right, up, 0.24f, UiDraw.alpha(accent, 0.28));
                    diamond(pose, out, p, right, up, 0.15f, UiDraw.alpha(accent, 0.9));
                    diamond(pose, out, p, right, up, 0.06f, 0xFFFFFFFF);
                }
            });
        } finally { context.poseStack().popPose(); }
    }
    private static void diamond(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f right, Vector3f up, float s, int color) {
        vertex(pose, out, p, right, up, 0, s, color);
        vertex(pose, out, p, right, up, s, 0, color);
        vertex(pose, out, p, right, up, 0, -s, color);
        vertex(pose, out, p, right, up, -s, 0, color);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f r, Vector3f u, float x, float y, int color) {
        out.addVertex(pose, (float) (p.x + r.x * x + u.x * y), (float) (p.y + r.y * x + u.y * y), (float) (p.z + r.z * x + u.z * y)).setColor(color);
    }
}
