package cn.tohsaka.factory.zstdnet.mixin;

import cn.tohsaka.factory.zstdnet.coremod.ServerRealIpHooks;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerHandshakeTCP.class)
public abstract class ServerHandshakeMixin {
    @Shadow @Final private NetworkManager field_147386_b;
    @Inject(method = "processHandshake", at = @At("HEAD"))
    private void zstdnet$realAddress(C00Handshake packet, CallbackInfo ci) {
        ServerRealIpHooks.apply(field_147386_b, packet);
    }
}
