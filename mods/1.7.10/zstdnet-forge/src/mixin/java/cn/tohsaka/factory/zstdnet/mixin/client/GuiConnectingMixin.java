package cn.tohsaka.factory.zstdnet.mixin.client;

import cn.tohsaka.factory.zstdnet.client.ClientConnection;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GuiConnecting.class)
public abstract class GuiConnectingMixin {
    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/GuiConnecting;func_146367_a(Ljava/lang/String;I)V"))
    private void zstdnet$connect(Args args) {
        int localPort = ClientConnection.connect(args.get(0), args.get(1));
        if (localPort > 0) {
            args.set(0, "127.0.0.1");
            args.set(1, localPort);
        }
    }
}
