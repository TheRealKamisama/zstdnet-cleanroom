package cn.tohsaka.factory.zstdnet.client;

import java.io.IOException;
import cn.tohsaka.factory.zstdnet.ClientConfig;
import cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet;
import org.slf4j.LoggerFactory;

public final class ClientConnection {
    private static LocalZstdNet.ProxyHandle active;
    private ClientConnection() {}
    public static synchronized int connect(String host, int port) {
        close();
        try {
            active = LocalZstdNet.start(host, port, host, port, ClientConfig.getLevel(), LocalZstdNet.Mode.AUTO);
            return active.localPort();
        } catch (IOException failure) {
            LoggerFactory.getLogger(ClientConnection.class).warn("Could not create local proxy for {}:{}", host, port, failure);
            return -1;
        }
    }
    public static synchronized LocalZstdNet.StatsSnapshot snapshot() { return active == null ? null : active.statsSnapshot(); }
    public static synchronized void connected() { if (active != null) active.closeListener(); }
    public static synchronized void close() {
        if (active != null) active.close();
        active = null;
    }
}
