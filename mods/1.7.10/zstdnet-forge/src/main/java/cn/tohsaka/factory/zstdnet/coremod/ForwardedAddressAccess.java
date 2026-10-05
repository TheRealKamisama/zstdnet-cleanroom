package cn.tohsaka.factory.zstdnet.coremod;

import java.net.SocketAddress;

public interface ForwardedAddressAccess {
    void zstdnet$setAddress(SocketAddress address);
}
