package cn.tohsaka.factory.zstdnet.mixin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import cn.tohsaka.factory.zstdnet.server.DedicatedServerAutoPort;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
    @Redirect(method = "startServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkSystem;addLanEndpoint(Ljava/net/InetAddress;I)V"))
    private void zstdnet$bindBackend(NetworkSystem network, InetAddress host, int port) throws IOException {
        InetSocketAddress target = DedicatedServerAutoPort.prepare((MinecraftServer) (Object) this, host, port);
        network.addLanEndpoint(target.getAddress(), target.getPort());
    }
}
