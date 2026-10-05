package cn.tohsaka.factory.zstdnet.mixin;

import net.minecraft.network.handshake.client.C00Handshake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C00Handshake.class)
public interface HandshakeAccessor extends cn.tohsaka.factory.zstdnet.coremod.HandshakeAccess {
    @Accessor("field_149598_b") String zstdnet$getHost();
    @Accessor("field_149598_b") void zstdnet$setHost(String host);
}
