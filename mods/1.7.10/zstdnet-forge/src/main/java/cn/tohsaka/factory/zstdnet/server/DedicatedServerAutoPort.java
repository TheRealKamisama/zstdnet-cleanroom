package cn.tohsaka.factory.zstdnet.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;

/** Alters the in-memory bind only; server.properties keeps the public address. */
public final class DedicatedServerAutoPort {
    private static volatile AutoPortPlan plan;

    private DedicatedServerAutoPort() {}

    public static InetSocketAddress prepare(MinecraftServer server, InetAddress host, int port) throws IOException {
        Properties config = ServerProxyConfigFile.loadProperties();
        if (!Boolean.parseBoolean(config.getProperty("enabled", "true"))
            || !Boolean.parseBoolean(config.getProperty("auto_takeover", "true"))) {
            return new InetSocketAddress(host, port);
        }
        String listenHost = host == null ? "0.0.0.0" : host.getHostAddress();
        // Fail early on public-port contention rather than exposing an unusable backend.
        try (ServerSocket publicProbe = new ServerSocket()) {
            publicProbe.bind(new InetSocketAddress(host, port));
        }
        int backend = freeBackend(port);
        plan = new AutoPortPlan(listenHost, port, "127.0.0.1", backend);
        ServerProxyConfigFile.writeResolvedAutoTakeoverConfig(listenHost, port, backend);
        server.setServerPort(backend);
        return new InetSocketAddress("127.0.0.1", backend);
    }

    private static int freeBackend(int publicPort) throws IOException {
        int preferred = publicPort < 65535 ? publicPort + 1 : 25566;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress("127.0.0.1", preferred));
            return probe.getLocalPort();
        } catch (IOException busy) {
            try (ServerSocket probe = new ServerSocket()) {
                probe.bind(new InetSocketAddress("127.0.0.1", 0));
                return probe.getLocalPort();
            }
        }
    }

    static AutoPortPlan activePlan() { return plan; }
    static void clear() { plan = null; }

    public record AutoPortPlan(String listenHost, int listenPort, String targetHost, int targetPort) {}
}
