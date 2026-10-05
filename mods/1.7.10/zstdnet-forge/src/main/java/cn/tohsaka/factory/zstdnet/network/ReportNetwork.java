package cn.tohsaka.factory.zstdnet.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import cn.tohsaka.factory.zstdnet.Zstdnet;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import com.google.gson.Gson;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Small bounded packets; handlers defer all game state work to the server/client tick. */
public final class ReportNetwork {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("zstdnet");
    private static final ArrayBlockingQueue<Request> REQUESTS = new ArrayBlockingQueue<>(128);
    private static final Set<UUID> CLIENTS = new HashSet<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static final Set<String> RANGES = Set.of("today", "session", "24h", "7d", "30d");
    private ReportNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(ServerHandler.class, Payload.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientHandler.class, Payload.class, 1, Side.CLIENT);
    }
    public static void hello() { CHANNEL.sendToServer(new Payload(new byte[] {0})); }
    public static void request(int id, String range) {
        byte[] name = range.getBytes(StandardCharsets.US_ASCII);
        CHANNEL.sendToServer(new Payload(ByteBuffer.allocate(5 + name.length).put((byte) 1).putInt(id).put(name).array()));
    }

    public static void tickServer() {
        for (int n = 0; n < 16; n++) {
            Request request = REQUESTS.poll();
            if (request == null) return;
            EntityPlayerMP player = request.player();
            if (!player.playerNetServerHandler.netManager.isChannelOpen()) continue;
            byte[] data = request.data();
            UUID playerId = player.getUniqueID();
            if (data.length == 1 && data[0] == 0) { CLIENTS.add(playerId); continue; }
            if (data.length < 6 || data.length > 21 || data[0] != 1) continue;
            int id = ByteBuffer.wrap(data, 1, 4).getInt();
            String range = new String(data, 5, data.length - 5, StandardCharsets.US_ASCII);
            long now = System.nanoTime();
            Long previous = LAST_REQUEST.put(playerId, now);
            if (previous != null && now - previous < 5_000_000_000L) continue;
            if (!player.canCommandSenderUseCommand(2, "zstdreport")) {
                respond(player, id, false, "zstdnet.report.no_permission");
            } else if (!RANGES.contains(range)) {
                respond(player, id, false, "zstdnet.report.invalid_range");
            } else {
                try { respond(player, id, true, ServerProxyBootstrap.buildTrafficReport(range)); }
                catch (RuntimeException failure) { respond(player, id, false, "zstdnet.report.failed"); }
            }
        }
    }

    private static void respond(EntityPlayerMP player, int id, boolean success, String payload) {
        try {
            for (byte[] chunk : ReportTransfer.split(id, success, payload)) {
                byte[] framed = new byte[chunk.length + 1];
                framed[0] = 2;
                System.arraycopy(chunk, 0, framed, 1, chunk.length);
                CHANNEL.sendTo(new Payload(framed), player);
            }
        } catch (IOException tooLarge) {
            if (success) respond(player, id, false, "zstdnet.report.too_large");
        }
    }

    public static void sendHud(EntityPlayerMP player, ServerProxyBootstrap.ServerHudSnapshot hud) {
        if (!CLIENTS.contains(player.getUniqueID())) return;
        byte[] json = GSON.toJson(hud).getBytes(StandardCharsets.UTF_8);
        if (json.length > 4096) return;
        byte[] framed = new byte[json.length + 1];
        framed[0] = 3;
        System.arraycopy(json, 0, framed, 1, json.length);
        CHANNEL.sendTo(new Payload(framed), player);
    }
    public static void remove(EntityPlayerMP player) {
        CLIENTS.remove(player.getUniqueID());
        LAST_REQUEST.remove(player.getUniqueID());
        REQUESTS.removeIf(r -> r.player() == player);
    }
    public static void clearServer() { REQUESTS.clear(); CLIENTS.clear(); LAST_REQUEST.clear(); }
    private record Request(EntityPlayerMP player, byte[] data) {}

    public static final class Payload implements IMessage {
        private byte[] data;
        public Payload() {}
        public Payload(byte[] data) { this.data = data; }
        @Override public void fromBytes(ByteBuf buffer) {
            int length = buffer.readableBytes();
            if (length < 1 || length > ReportTransfer.MAX_PACKET_BYTES + 1) throw new IllegalArgumentException("Invalid ZstdNet payload length");
            data = new byte[length];
            buffer.readBytes(data);
        }
        @Override public void toBytes(ByteBuf buffer) { buffer.writeBytes(data); }
    }
    public static final class ServerHandler implements IMessageHandler<Payload, IMessage> {
        @Override public IMessage onMessage(Payload message, MessageContext context) {
            // Bound request storage independently of the much larger client response limit.
            if (message.data.length <= 21) REQUESTS.offer(new Request(context.getServerHandler().playerEntity, message.data));
            return null;
        }
    }
    public static final class ClientHandler implements IMessageHandler<Payload, IMessage> {
        @Override public IMessage onMessage(Payload message, MessageContext context) {
            Zstdnet.proxy.acceptPacket(message.data);
            return null;
        }
    }
}
