package cn.tohsaka.factory.zstdnet.mixin;

import java.net.SocketAddress;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NetworkManager.class)
public interface NetworkManagerAccessor extends cn.tohsaka.factory.zstdnet.coremod.ForwardedAddressAccess {
    @Accessor("socketAddress") void zstdnet$setAddress(SocketAddress address);
}
