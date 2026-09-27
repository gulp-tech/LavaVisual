package tech.gulp.lavavisual.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionSkinApplyEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.AvatarEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.DownstreamSession;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

/**
 * LavaVisual for Bedrock players: shows the accessories (glasses, headphones, scarf) of Java players who use the
 * LavaVisual mod to the Bedrock players of this Geyser.
 *
 * It needs nothing on the Java server and nothing on the Bedrock clients. The mod already tells other players what
 * its user wears through bit 0x80 of the skin-part settings (see Receiver); Geyser receives that bit for every player
 * its Bedrock players can see. The extension decodes it and sends those players' skins to Bedrock with an extra
 * geometry for the accessories (see Accessories).
 */
public final class LavaVisualBedrock implements Extension {
    /**
     * DATA_PLAYER_MODE_CUSTOMISATION of Avatar: index 16 in Java 26.x, 17 on older servers. Both are read; frames
     * carry a preamble and a check, so a byte from another field is dropped instead of being decoded as an outfit.
     */
    static final int SKIN_PARTS = 16, SKIN_PARTS_OLD = 17;
    private static final long REPORT_EVERY = 30_000;
    private final java.util.concurrent.atomic.AtomicLong bits = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.Set<UUID> reported = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long nextReport;
    private final Receiver receiver = new Receiver();
    private final Map<GeyserSession, ClientSession> attached = new ConcurrentHashMap<>();
    /** Per Bedrock player: what each Java player's skin was last sent with (entity id and accessories). */
    private final Map<GeyserSession, Map<UUID, Long>> shown = new ConcurrentHashMap<>();
    private Accessories accessories;
    private ScheduledExecutorService ticker;
    private volatile boolean failed;

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        accessories = Accessories.load();
        String problem = checkGeyser();
        if (problem != null) {
            logger().error("LavaVisual: " + problem + ". Update Geyser or LavaVisual Bedrock; accessories stay off.");
            return;
        }
        ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "LavaVisual Bedrock");
            thread.setDaemon(true);
            return thread;
        });
        ticker.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);
        logger().info("LavaVisual Bedrock ready: " + accessories.count() + " accessory geometries; Bedrock players see the"
            + " glasses, headphones and scarves of LavaVisual players");
    }

    @Subscribe
    public void onShutdown(GeyserShutdownEvent event) {
        if (ticker != null) ticker.shutdownNow();
    }

    @Subscribe
    public void onDisconnect(SessionDisconnectEvent event) {
        attached.remove(event.connection());
        shown.remove(event.connection());
    }

    /** Geyser is sending a Java player's skin to a Bedrock player: add the accessories that player wears. */
    @Subscribe
    public void onSkinApply(SessionSkinApplyEvent event) {
        if (event.bedrock() || accessories == null || failed) return;
        long now = now();
        Receiver.Outfit outfit = receiver.active(event.uuid(), now);
        remember(event.connection(), event.uuid(), outfit);
        if (outfit == null) return;
        Skin skin = Accessories.paint(event.skinData().skin(), outfit.extras(), outfit.extrasColor());
        SkinGeometry geometry = accessories.geometry(outfit.extras(), outfit.extrasColor() == 0, event.slim());
        if (skin == null || geometry == null) return;
        event.skin(skin);
        event.geometry(geometry);
    }

    private void remember(GeyserConnection connection, UUID id, Receiver.Outfit outfit) {
        if (!(connection instanceof GeyserSession session)) return;
        PlayerEntity entity = session.getEntityCache().getPlayerEntity(id);
        if (entity != null) shown.computeIfAbsent(session, key -> new ConcurrentHashMap<>()).put(id, stamp(entity, outfit));
    }

    private static long stamp(Entity entity, Receiver.Outfit outfit) {
        int look = outfit == null ? 0 : (outfit.extras() & 7) << 5 | outfit.extrasColor();
        return entity.geyserId() << 9 | look;
    }

    private void tick() {
        if (failed) return;
        try {
            long now = now();
            List<GeyserSession> sessions = new ArrayList<>();
            for (GeyserConnection connection : GeyserApi.api().onlineConnections()) {
                if (connection instanceof GeyserSession session) {
                    sessions.add(session);
                    attach(session);
                }
            }
            report(now, sessions);
            receiver.tick(now, id -> {
                for (GeyserSession session : sessions) if (session.getEntityCache().getPlayerEntity(id) != null) return true;
                return false;
            });
            // Resend a skin when what a Java player wears changed, or when their entity was spawned again (Geyser then
            // sends a plain skin itself when the server gave no textures, which fires no skin event).
            for (UUID id : receiver.dressed()) {
                Receiver.Outfit outfit = receiver.active(id, now);
                for (GeyserSession session : sessions) {
                    PlayerEntity entity = session.getEntityCache().getPlayerEntity(id);
                    if (entity == null || entity == session.getPlayerEntity()) continue;
                    Map<UUID, Long> seen = shown.computeIfAbsent(session, key -> new ConcurrentHashMap<>());
                    long stamp = stamp(entity, outfit);
                    Long last = seen.get(id);
                    if (last == null ? outfit == null : last == stamp) continue;
                    seen.put(id, stamp);
                    if (outfit != null && reported.add(id))
                        logger().info("LavaVisual: " + name(session, id) + " wears accessories " + outfit.extras()
                            + " colour " + outfit.extrasColor() + ", sending the skin to " + session.bedrockUsername());
                    session.executeInEventLoop(() -> resend(session, id));
                }
            }
        } catch (Throwable error) {
            failed = true;
            logger().error("LavaVisual Bedrock stopped: this Geyser build is not supported", error);
        }
    }

    /** A line every 30 seconds while Bedrock players are online, so the console shows whether anything arrives. */
    private void report(long now, List<GeyserSession> sessions) {
        if (sessions.isEmpty() || now < nextReport) return;
        nextReport = now + REPORT_EVERY;
        logger().info("LavaVisual: " + sessions.size() + " Bedrock player(s), " + bits.get()
            + " skin-setting update(s) from Java players, " + receiver.dressed().size() + " of them wearing accessories."
            + (bits.get() == 0 ? " Nothing received yet: a Java player with LavaVisual and accessories switched on has"
            + " to be visible to a Bedrock player." : ""));
    }

    private static String name(GeyserSession session, UUID id) {
        PlayerEntity entity = session.getEntityCache().getPlayerEntity(id);
        return entity == null ? id.toString() : entity.getUsername();
    }

    /** Listens to the Java server's entity data on the Bedrock player's connection (again after a server switch). */
    private void attach(GeyserSession session) {
        DownstreamSession downstream = session.getDownstream();
        if (downstream == null) return;
        ClientSession client = downstream.getSession();
        if (client == null || attached.get(session) == client) return;
        attached.put(session, client);
        client.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session connection, Packet packet) {
                if (!(packet instanceof ClientboundSetEntityDataPacket data)) return;
                ByteEntityMetadata parts = null;
                for (EntityMetadata<?, ?> entry : data.getMetadata())
                    if ((entry.getId() == SKIN_PARTS || entry.getId() == SKIN_PARTS_OLD) && entry instanceof ByteEntityMetadata value) parts = value;
                if (parts == null) return;
                long time = now();
                boolean bit = (parts.getPrimitiveValue() & 0x80) != 0;
                int entityId = data.getEntityId();
                // After Geyser has translated this packet (it runs on the same loop), so a new player is known.
                session.executeInEventLoop(() -> {
                    Entity entity = session.getEntityCache().getEntityByJavaId(entityId);
                    if (entity instanceof PlayerEntity player && player != session.getPlayerEntity()) {
                        bits.incrementAndGet();
                        receiver.observe(player.uuid(), bit, time);
                    }
                });
            }
        });
    }

    /** Sends a Java player's skin to one Bedrock player again, with or without accessories. */
    private void resend(GeyserSession session, UUID id) {
        PlayerEntity entity = session.getEntityCache().getPlayerEntity(id);
        if (entity == null) return;
        if (SkinManager.GameProfileData.from(entity) != null) {
            // Fetches the skin (cached) and fires SessionSkinApplyEvent, where onSkinApply adds the accessories.
            SkinManager.requestAndHandleSkinAndCape(entity, session, null);
            return;
        }
        // No textures from the server (offline mode without a skin plugin): Geyser shows a default skin.
        SkinData base = SkinProvider.determineFallbackSkinData(id);
        Receiver.Outfit outfit = receiver.active(id, now());
        SkinData data = base;
        if (outfit != null && accessories != null) {
            boolean slim = base.geometry() != null && base.geometry().geometryName().contains("Slim");
            Skin skin = Accessories.paint(base.skin(), outfit.extras(), outfit.extrasColor());
            SkinGeometry geometry = accessories.geometry(outfit.extras(), outfit.extrasColor() == 0, slim);
            if (skin != null && geometry != null) data = new SkinData(skin, base.cape(), geometry);
        }
        SkinManager.sendSkinPacket(session, entity, data);
    }

    /** Null when every piece of Geyser this extension relies on is there. */
    static String checkGeyser() {
        try {
            GeyserSession.class.getMethod("getDownstream");
            GeyserSession.class.getMethod("getEntityCache");
            GeyserSession.class.getMethod("executeInEventLoop", Runnable.class);
            DownstreamSession.class.getMethod("getSession");
            EntityCache.class.getMethod("getPlayerEntity", UUID.class);
            EntityCache.class.getMethod("getEntityByJavaId", int.class);
            Entity.class.getMethod("geyserId");
            Entity.class.getMethod("uuid");
            SkinManager.class.getMethod("requestAndHandleSkinAndCape", AvatarEntity.class, GeyserSession.class, java.util.function.Consumer.class);
            SkinManager.class.getMethod("sendSkinPacket", GeyserSession.class, AvatarEntity.class, SkinData.class);
            SkinManager.GameProfileData.class.getMethod("from", AvatarEntity.class);
            SkinProvider.class.getMethod("determineFallbackSkinData", UUID.class);
            ClientboundSetEntityDataPacket.class.getMethod("getMetadata");
            ByteEntityMetadata.class.getMethod("getPrimitiveValue");
            return null;
        } catch (Throwable error) {
            return "this Geyser build is not supported (" + error + ")";
        }
    }

    static long now() { return System.nanoTime() / 1_000_000L; }
}
