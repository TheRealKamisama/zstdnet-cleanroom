package cn.tohsaka.factory.zstdnet.mixin.client;

import java.io.IOException;
import java.net.InetAddress;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect(method = "shareToLAN", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/HttpUtil;func_76181_a()I"))
    private int zstdnet$gamePort() throws IOException { return ServerProxyBootstrap.chooseLanGamePort(); }

    @Redirect(method = "shareToLAN", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkSystem;addLanEndpoint(Ljava/net/InetAddress;I)V"))
    private void zstdnet$trackGamePort(NetworkSystem system, InetAddress host, int port) throws IOException {
        system.addLanEndpoint(host, port);
        IntegratedServer server = (IntegratedServer) (Object) this;
        // MinecraftServer's port accessors are SERVER-only in production 1.7.10.
        // Track the actual client-hosted port in our lifecycle service instead.
        ServerProxyBootstrap.publishLan(server, port);
    }

    @ModifyArg(method = "shareToLAN", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ThreadLanServerPing;<init>(Ljava/lang/String;Ljava/lang/String;)V"), index = 1)
    private String zstdnet$advertise(String gamePort) {
        int advertised = ServerProxyBootstrap.advertisedPort();
        return advertised > 0 ? Integer.toString(advertised) : gamePort;
    }
}
