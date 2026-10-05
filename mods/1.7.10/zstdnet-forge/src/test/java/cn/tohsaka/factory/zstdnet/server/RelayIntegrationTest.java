package cn.tohsaka.factory.zstdnet.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import cn.tohsaka.factory.zstdnet.Platform;
import cn.tohsaka.factory.zstdnet.core.protocol.ByteArrayOps;
import cn.tohsaka.factory.zstdnet.core.protocol.PacketIo;
import cn.tohsaka.factory.zstdnet.core.protocol.RelayStatus;
import cn.tohsaka.factory.zstdnet.core.protocol.VarIntCodec;
import cn.tohsaka.factory.zstdnet.core.stats.TrafficStats;
import cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RelayIntegrationTest {
    @TempDir Path directory;

    @Test void compressedRoundTripPreservesForgeSuffixAndCountersAndReconnects() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            TrafficStats stats = new TrafficStats();
            ServerProxyRuntime runtime = start(backend.port(), relay, stats, "");
            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try (var local = LocalZstdNet.start("127.0.0.1", relay, "original.example", 25565, 3, LocalZstdNet.Mode.AUTO);
                         Socket game = socket(local.localPort())) {
                        assertEquals(LocalZstdNet.Mode.ZSTD, local.mode());
                        PacketIo.writePacket(game.getOutputStream(), handshake("127.0.0.1\0FML\0mod-marker\0zstdnet-real-ip=MS4yLjMuNA", 2));
                        byte[] data = new byte[256 * 1024];
                        Arrays.fill(data, (byte) 'Z');
                        game.getOutputStream().write(data);
                        game.getOutputStream().flush();
                        String host = host(PacketIo.readPacket(game.getInputStream()));
                        assertTrue(host.startsWith("original.example\0FML\0mod-marker"), host);
                        assertFalse(host.contains("MS4yLjMuNA"), "Client cannot inject its own real-IP token");
                        assertTrue(host.contains("zstdnet-real-ip=MTI3LjAuMC4x"));
                        assertArrayEquals(data, PacketIo.readFully(game.getInputStream(), data.length));
                    }
                }
                var state = stats.snapshot();
                assertTrue(state.rawIngressBytes() >= 512 * 1024);
                assertTrue(state.rawEgressBytes() >= 512 * 1024);
                assertTrue(state.tcpIngressBytes() < state.rawIngressBytes() / 2);
            } finally { runtime.stop(); }
            try (ServerSocket reusable = new ServerSocket(relay)) { assertEquals(relay, reusable.getLocalPort()); }
        }
    }

    @Test void ordinaryServerAutoModeRemainsRaw() throws Exception {
        try (Backend backend = new Backend(); var local = LocalZstdNet.start("127.0.0.1", backend.port(), 3, LocalZstdNet.Mode.AUTO);
             Socket game = socket(local.localPort())) {
            assertEquals(LocalZstdNet.Mode.RAW, local.mode());
            PacketIo.writePacket(game.getOutputStream(), handshake("ordinary\0FML\0", 2));
            game.getOutputStream().flush();
            assertTrue(host(PacketIo.readPacket(game.getInputStream())).contains("\0FML\0"));
        }
    }

    @Test void statusReleasesPerIpSlotsAndRawLoginIsRejected() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "max_conn_per_ip=1\nstatus_cache_ttl=0s\n");
            try {
                for (int i = 0; i < 4; i++) {
                    try (Socket status = socket(relay)) {
                        PacketIo.writePacket(status.getOutputStream(), handshake("status", 1));
                        PacketIo.writePacket(status.getOutputStream(), new byte[] {0});
                        status.getOutputStream().flush();
                        assertTrue(RelayStatus.isRelay(PacketIo.readPacket(status.getInputStream())));
                    }
                    Thread.sleep(50);
                }
                try (Socket raw = socket(relay)) {
                    PacketIo.writePacket(raw.getOutputStream(), handshake("login", 2));
                    PacketIo.writePacket(raw.getOutputStream(), new byte[] {0, 4, 'T', 'e', 's', 't'});
                    raw.getOutputStream().flush();
                    byte[] reply = PacketIo.readPacket(raw.getInputStream());
                    assertTrue(new String(reply, StandardCharsets.UTF_8).contains("ZSTD"));
                }
            } finally { runtime.stop(); }
        }
    }

    @Test void occupiedUdpPortDoesNotDisableCompressedTcp() throws Exception {
        try (Backend backend = new Backend(); DatagramSocket occupied = new DatagramSocket(0)) {
            int relay = occupied.getLocalPort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "");
            try (var local = LocalZstdNet.start("127.0.0.1", relay, 3, LocalZstdNet.Mode.ZSTD); Socket game = socket(local.localPort())) {
                assertTrue(runtime.isRunning());
                PacketIo.writePacket(game.getOutputStream(), handshake("udp-busy", 2));
                game.getOutputStream().flush();
                assertTrue(host(PacketIo.readPacket(game.getInputStream())).contains("zstdnet-real-ip="));
            } finally { runtime.stop(); }
        }
    }

    @Test void sameAndSeparateUdpPortsForwardAndCleanUp() throws Exception {
        try (Backend backend = new Backend(); DatagramSocket gameBackend = new DatagramSocket(backend.port());
             DatagramSocket voiceBackend = new DatagramSocket(0); DatagramSocket caller = new DatagramSocket(0)) {
            int relay = freePort();
            int voiceListen = freePort();
            caller.setSoTimeout(3000);
            gameBackend.setSoTimeout(3000);
            voiceBackend.setSoTimeout(3000);
            TrafficStats stats = new TrafficStats();
            ServerProxyRuntime runtime = start(backend.port(), relay, stats,
                "voice_chat_passthrough=true\nvoice_chat_listen=127.0.0.1:" + voiceListen + "\nvoice_chat_target=127.0.0.1:" + voiceBackend.getLocalPort() + "\n");
            try {
                for (int index = 0; index < 2; index++) {
                    int destination = index == 0 ? relay : voiceListen;
                    DatagramSocket destinationBackend = index == 0 ? gameBackend : voiceBackend;
                    byte[] payload = ("udp-" + index).getBytes(StandardCharsets.UTF_8);
                    caller.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), destination));
                    DatagramPacket received = new DatagramPacket(new byte[100], 100);
                    destinationBackend.receive(received);
                    assertArrayEquals(payload, Arrays.copyOf(received.getData(), received.getLength()));
                    destinationBackend.send(new DatagramPacket(payload, payload.length, received.getSocketAddress()));
                    caller.receive(received);
                    assertArrayEquals(payload, Arrays.copyOf(received.getData(), received.getLength()));
                }
                assertTrue(stats.snapshot().udpIngressBytes() > 0);
                assertTrue(stats.snapshot().udpEgressBytes() > 0);
            } finally { runtime.stop(); }
            try (DatagramSocket released = new DatagramSocket(relay); DatagramSocket voiceReleased = new DatagramSocket(voiceListen)) {
                assertEquals(relay, released.getLocalPort());
                assertEquals(voiceListen, voiceReleased.getLocalPort());
            }
        }
    }

    @Test void trustedProxyHeaderForwardsIpAndMissingHeaderIsRejected() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "trust_proxy_protocol=true\n");
            try {
                try (Socket direct = socket(relay)) {
                    direct.getOutputStream().write(new byte[16]);
                    direct.getOutputStream().flush();
                    assertEquals(-1, direct.getInputStream().read());
                }
                try (Socket proxy = socket(relay)) {
                    proxy.getOutputStream().write(proxyHeader());
                    try (ZstdInputStream in = new ZstdInputStream(proxy.getInputStream());
                         ZstdOutputStream out = new ZstdOutputStream(proxy.getOutputStream(), 3)) {
                        PacketIo.writePacket(out, handshake("trusted\0FML\0", 2));
                        out.flush();
                        assertTrue(host(PacketIo.readPacket(in)).contains("zstdnet-real-ip=MjAzLjAuMTEzLjc"));
                    }
                }
            } finally { runtime.stop(); }
        }
    }

    @Test void proxyHeaderFromUntrustedPeerIsRejected() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(),
                "trust_proxy_protocol=true\ntrusted_proxy_ips=192.0.2.1\n");
            try (Socket proxy = socket(relay)) {
                proxy.getOutputStream().write(proxyHeader());
                proxy.getOutputStream().flush();
                assertEquals(-1, proxy.getInputStream().read());
            } finally { runtime.stop(); }
        }
    }

    @Test void stopClosesIdleConnectionsWithinBound() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "handshake_timeout=30s\n");
            try (Socket idle = socket(relay)) {
                Thread.sleep(100);
                assertTimeoutPreemptively(Duration.ofSeconds(8), runtime::stop);
                assertEquals(-1, idle.getInputStream().read());
            } finally { runtime.stop(); }
        }
    }

    @Test void requestWindowBanExpiresWithoutLeakingConnectionSlots() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(),
                "max_req_per_window=1\nrequest_window=200ms\nban_duration=250ms\nmax_conn_per_ip=1\n");
            try {
                assertTrue(statusAllowed(relay));
                assertFalse(statusAllowed(relay));
                Thread.sleep(350);
                assertTrue(statusAllowed(relay));
            } finally { runtime.stop(); }
        }
    }

    @Test void handshakeDeadlineClosesSilentPeer() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "handshake_timeout=100ms\n");
            try (Socket idle = socket(relay)) {
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(-1, idle.getInputStream().read()));
            } finally { runtime.stop(); }
        }
    }

    @Test void configuredBackendIdleTimeoutEndsCompressedSession() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "idle_timeout=150ms\n");
            try (var local = LocalZstdNet.start("127.0.0.1", relay, 3, LocalZstdNet.Mode.ZSTD); Socket game = socket(local.localPort())) {
                PacketIo.writePacket(game.getOutputStream(), handshake("idle-backend", 2));
                game.getOutputStream().flush();
                assertTrue(PacketIo.readPacket(game.getInputStream()).length > 0);
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(-1, game.getInputStream().read()));
            } finally { runtime.stop(); }
        }
    }

    @Test void globalConnectionCapRejectsASecondLivePeer() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(), "max_conn_total=1\nhandshake_timeout=30s\n");
            try (Socket first = socket(relay)) {
                long deadline = System.nanoTime() + 1_000_000_000L;
                while (runtime.hudSnapshot().connections() < 1 && System.nanoTime() < deadline) Thread.sleep(10);
                assertEquals(1, runtime.hudSnapshot().connections());
                try (Socket second = socket(relay)) {
                    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(-1, second.getInputStream().read()));
                }
            } finally { runtime.stop(); }
        }
    }

    @Test void globalRequestWindowRejectsThenRecovers() throws Exception {
        try (Backend backend = new Backend()) {
            int relay = freePort();
            ServerProxyRuntime runtime = start(backend.port(), relay, new TrafficStats(),
                "max_req_total_per_window=1\nrequest_window=200ms\nban_duration=200ms\n");
            try {
                assertTrue(statusAllowed(relay));
                assertFalse(statusAllowed(relay));
                Thread.sleep(300);
                assertTrue(statusAllowed(relay));
            } finally { runtime.stop(); }
        }
    }

    private static boolean statusAllowed(int relay) throws Exception {
        try (Socket status = socket(relay)) {
            PacketIo.writePacket(status.getOutputStream(), handshake("limits", 1));
            PacketIo.writePacket(status.getOutputStream(), new byte[] {0});
            status.getOutputStream().flush();
            byte[] packet = PacketIo.readPacket(status.getInputStream());
            return packet.length > 0 && RelayStatus.isRelay(packet);
        } catch (java.net.SocketException closed) { return false; }
    }

    private ServerProxyRuntime start(int backend, int relay, TrafficStats stats, String extras) throws Exception {
        Platform.configure(directory, false);
        Files.writeString(ServerProxyConfigFile.path(), "enabled=true\nauto_takeover=false\nlisten=127.0.0.1:" + relay
            + "\ntarget=127.0.0.1:" + backend + "\nvoice_chat_passthrough=false\nmax_req_per_window=0\n" + extras);
        ServerProxyRuntime runtime = new ServerProxyRuntime(stats);
        runtime.start(backend);
        assertTrue(runtime.isRunning());
        return runtime;
    }
    private static int freePort() throws Exception { try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
    private static Socket socket(int port) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        return socket;
    }
    private static byte[] handshake(String host, int state) {
        byte[] text = host.getBytes(StandardCharsets.UTF_8);
        return ByteArrayOps.concat(new byte[] {0, 5}, VarIntCodec.encode(text.length), text, new byte[] {0x63, (byte) 0xdd, (byte) state});
    }
    private static String host(byte[] handshake) {
        var length = VarIntCodec.read(handshake, 2);
        return new String(handshake, length.next(), length.value(), StandardCharsets.UTF_8);
    }
    private static byte[] proxyHeader() {
        return new byte[] {13,10,13,10,0,13,10,81,85,73,84,10,0x21,0x11,0,12,
            (byte)203,0,113,7,127,0,0,1,0x30,0x39,0x63,(byte)0xdd};
    }

    private static final class Backend implements AutoCloseable {
        private final ServerSocket listener = new ServerSocket(0);
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        Backend() throws Exception {
            workers.submit(() -> {
                while (!listener.isClosed()) {
                    try { Socket peer = listener.accept(); workers.submit(() -> serve(peer)); }
                    catch (Exception done) { break; }
                }
            });
        }
        int port() { return listener.getLocalPort(); }
        private void serve(Socket peer) {
            try (peer) {
                peer.setSoTimeout(7000);
                byte[] handshake = PacketIo.readPacket(peer.getInputStream());
                if (handshake[handshake.length - 1] == 1) {
                    PacketIo.readPacket(peer.getInputStream());
                    byte[] json = "{\"version\":{\"name\":\"1.7.10\",\"protocol\":5},\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"Test\"}}".getBytes(StandardCharsets.UTF_8);
                    PacketIo.writePacket(peer.getOutputStream(), ByteArrayOps.concat(new byte[] {0}, VarIntCodec.encode(json.length), json));
                } else {
                    PacketIo.writePacket(peer.getOutputStream(), handshake);
                    peer.getOutputStream().flush();
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = peer.getInputStream().read(buffer)) >= 0) {
                        peer.getOutputStream().write(buffer, 0, count);
                        peer.getOutputStream().flush();
                    }
                }
            } catch (Exception closed) { /* Client disconnect/negative case. */ }
        }
        @Override public void close() throws Exception { listener.close(); workers.shutdownNow(); }
    }
}
