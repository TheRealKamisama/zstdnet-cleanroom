package cn.tohsaka.factory.zstdnet.mixin.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import net.minecraft.client.multiplayer.ThreadLanServerPing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThreadLanServerPing.class)
public abstract class LanPingMixin {
    @Shadow @Final private String motd;
    @Shadow @Final private DatagramSocket socket;

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Ljava/net/DatagramSocket;send(Ljava/net/DatagramPacket;)V"))
    private void zstdnet$advertiseCurrentPort(DatagramSocket sender, DatagramPacket packet) throws IOException {
        int port = ServerProxyBootstrap.advertisedPort();
        if (port > 0) {
            packet.setData(ThreadLanServerPing.getPingResponse(motd, Integer.toString(port)).getBytes(StandardCharsets.UTF_8));
        }
        sender.send(packet);
    }
    @Inject(method = "run", at = @At("RETURN"))
    private void zstdnet$closeSocket(CallbackInfo ci) { socket.close(); }
}
