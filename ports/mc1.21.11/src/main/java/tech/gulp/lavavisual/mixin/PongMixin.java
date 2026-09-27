package tech.gulp.lavavisual.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.hud.PingMeter;

/** Feeds answers to the watermark's ping requests to PingMeter; vanilla handling continues unchanged. */
@Mixin(ClientPacketListener.class)
public abstract class PongMixin {
    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void lavavisual$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingMeter.pong(packet.time());
    }
}
