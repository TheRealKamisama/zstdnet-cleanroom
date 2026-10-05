package cn.tohsaka.factory.zstdnet.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.ZoneId;
import java.util.List;
import cn.tohsaka.factory.zstdnet.core.stats.TrafficStatisticsService;
import cn.tohsaka.factory.zstdnet.core.stats.TrafficStats;
import cn.tohsaka.factory.zstdnet.coremod.ServerRealIpHooks;
import cn.tohsaka.factory.zstdnet.network.ReportNetwork;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

public final class ServerProxyBootstrap {
    private static final TrafficStats STATS = new TrafficStats();
    private static final ServerProxyRuntime RUNTIME = new ServerProxyRuntime(STATS);
    private static TrafficStatisticsService history;
    private static volatile int lanPort = -1;
    private static long lastSync;
    private static boolean announce;

    private ServerProxyBootstrap() {}
    public static void init() { FMLCommonHandler.instance().bus().register(new ServerProxyBootstrap()); }

    public static void started() {
        history = new TrafficStatisticsService(ServerProxyConfigFile.path().getParent().resolve("zstdnet/stats"), ZoneId.systemDefault(), STATS);
        history.startSession();
        MinecraftServer server = MinecraftServer.getServer();
        if (server.isDedicatedServer()) RUNTIME.start(server.getServerPort());
    }

    public static void stop() {
        RUNTIME.stop();
        if (history != null) history.stopSession();
        history = null;
        lanPort = -1;
        announce = false;
        lastSync = 0;
        ReportNetwork.clearServer();
        DedicatedServerAutoPort.clear();
    }

    public static int chooseLanGamePort() throws IOException {
        int preferred = ServerProxyConfigFile.readTargetPort();
        if (preferred != ServerProxyConfigFile.readListenPort()) {
            try (ServerSocket probe = new ServerSocket()) {
                probe.bind(new InetSocketAddress(preferred));
                return preferred;
            } catch (IOException occupied) { /* Allocate a free port below. */ }
        }
        try (ServerSocket probe = new ServerSocket(0)) { return probe.getLocalPort(); }
    }

    public static synchronized void publishLan(MinecraftServer server, int gamePort) {
        lanPort = gamePort;
        // Existing relay semantics require an offline backend; only LAN is changed automatically.
        server.setOnlineMode(false);
        RUNTIME.stop();
        RUNTIME.startLan(gamePort);
        announce = true;
    }

    public static int advertisedPort() {
        ServerProxyRuntime.HudSnapshot state = RUNTIME.hudSnapshot();
        return state == null ? -1 : state.listenPort();
    }
    public static int gamePort() { return lanPort; }

    public static String buildTrafficReport(String range) {
        if (history == null) throw new IllegalStateException("Statistics session is not active");
        ServerHudSnapshot state = currentHudSnapshot();
        return history.buildReportJson(range, state == null ? "inactive" : state.mode(),
            state == null ? "" : state.listenHost() + ":" + state.listenPort());
    }

    public static ServerHudSnapshot currentHudSnapshot() {
        ServerProxyRuntime.HudSnapshot s = RUNTIME.hudSnapshot();
        return s == null ? null : new ServerHudSnapshot(s.modeName(), s.listenHost(), s.listenPort(),
            s.rawBytes(), s.zstdBytes(), s.rawUpBytes(), s.rawDownBytes(), s.zstdUpBytes(), s.zstdDownBytes(),
            s.rawUpRate(), s.rawDownRate(), s.zstdUpRate(), s.zstdDownRate(), s.rawRate(), s.zstdRate(), s.ratioPercent(), s.connections());
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || history == null) return;
        ReportNetwork.tickServer();
        if (RUNTIME.configChangedOnDisk()) {
            RUNTIME.stop();
            if (server.isDedicatedServer()) RUNTIME.start(server.getServerPort());
            else if (lanPort > 0) { RUNTIME.startLan(lanPort); announce = true; }
        }
        long now = System.currentTimeMillis();
        if (now - lastSync < 1000) return;
        lastSync = now;
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) entry;
            ReportNetwork.sendHud(player, currentHudSnapshot());
            if (announce && advertisedPort() > 0) {
                String port = Integer.toString(advertisedPort());
                ChatComponentText clickable = new ChatComponentText(port);
                clickable.getChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true)
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/zstdport copy " + port))
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentTranslation("zstdnet.singleplayer.lan_ready.copy_zstd")));
                player.addChatMessage(new ChatComponentTranslation("zstdnet.singleplayer.lan_ready", clickable, lanPort));
            }
        }
        announce = false;
    }

    @SubscribeEvent public void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP player) || !RUNTIME.protectsBackendLogin()) return;
        var manager = player.playerNetServerHandler.netManager;
        var remote = manager.getSocketAddress();
        if (manager.isLocalChannel() || ServerRealIpHooks.isForwardedConnection(manager)) return;
        if (remote instanceof InetSocketAddress address && address.getAddress() != null && address.getAddress().isLoopbackAddress()) return;
        player.playerNetServerHandler.kickPlayerFromServer(ServerProxyRuntime.ZSTD_ADDRESS_HINT);
    }

    @SubscribeEvent public void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP player) ReportNetwork.remove(player);
    }

    public record ServerHudSnapshot(String mode, String listenHost, int listenPort,
        long rawBytes, long zstdBytes, long rawUpBytes, long rawDownBytes, long zstdUpBytes, long zstdDownBytes,
        long rawUpRate, long rawDownRate, long zstdUpRate, long zstdDownRate, long rawRate, long zstdRate,
        double ratioPercent, int connections) {}
}
