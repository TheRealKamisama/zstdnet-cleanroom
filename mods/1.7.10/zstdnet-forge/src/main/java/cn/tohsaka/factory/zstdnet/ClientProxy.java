package cn.tohsaka.factory.zstdnet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import cn.tohsaka.factory.zstdnet.client.ClientConnection;
import cn.tohsaka.factory.zstdnet.client.TrafficReportGenerator;
import cn.tohsaka.factory.zstdnet.network.ReportNetwork;
import cn.tohsaka.factory.zstdnet.network.ReportTransfer;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile;
import com.google.gson.Gson;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {
    private final ArrayBlockingQueue<byte[]> packets = new ArrayBlockingQueue<>(64);
    private final ReportTransfer.Receiver reports = new ReportTransfer.Receiver();
    private final AtomicInteger ids = new AtomicInteger();
    private final Gson gson = new Gson();
    private volatile boolean serverSupports;
    private volatile boolean joined;
    private volatile ServerProxyBootstrap.ServerHudSnapshot remoteHud;
    private long hudReceived;

    @Override public void preInit() { ClientConfig.load(); }
    @Override public void init() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        for (String name : List.of("zstdhud", "zstdport", "zstdreport")) {
            ClientCommandHandler.instance.registerCommand(new LocalCommand(name));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(ClientConnection::close, "zstdnet-client-shutdown"));
    }
    @Override public void acceptPacket(byte[] data) { packets.offer(data); }

    @SubscribeEvent public void registration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if (event.side == Side.CLIENT && event.registrations.contains("zstdnet")) {
            serverSupports = "REGISTER".equals(event.operation);
        }
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientConnection.close();
        packets.clear();
        reports.clear();
        serverSupports = false;
        joined = false;
        remoteHud = null;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && !joined) {
            ClientConnection.connected();
            joined = true;
        }
        if (mc.thePlayer != null && serverSupports) {
            // Sending hello repeatedly is unnecessary; reuse the connection generation.
            ReportNetwork.hello();
            serverSupports = false;
        }
        if (mc.theWorld == null && !(mc.currentScreen instanceof GuiConnecting)) ClientConnection.close();
        if (reports.expire(System.nanoTime())) message("zstdnet.report.timeout");
        for (int n = 0; n < 64; n++) {
            byte[] packet = packets.poll();
            if (packet == null) break;
            if (mc.thePlayer == null) continue;
            try {
                if (packet[0] == 2) {
                    var result = reports.accept(Arrays.copyOfRange(packet, 1, packet.length), System.nanoTime());
                    if (result.isPresent()) {
                        if (!result.get().success()) message(result.get().payload());
                        else {
                            var connection = ClientConnection.snapshot();
                            String identity = mc.func_147104_D() != null ? mc.func_147104_D().serverIP
                                : connection != null ? connection.remoteHost() + ":" + connection.remotePort() : "singleplayer";
                            var file = TrafficReportGenerator.generate(Platform.configDirectory().resolve("zstdnet/reports"), identity, result.get().payload());
                            ChatComponentTranslation link = new ChatComponentTranslation("zstdnet.report.open");
                            link.getChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true)
                                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.latest().toString()));
                            mc.thePlayer.addChatMessage(link);
                            mc.thePlayer.addChatMessage(new ChatComponentText(file.latest().toString()));
                        }
                    }
                } else if (packet[0] == 3 && packet.length <= 4097) {
                    remoteHud = gson.fromJson(new String(packet, 1, packet.length - 1, StandardCharsets.UTF_8), ServerProxyBootstrap.ServerHudSnapshot.class);
                    hudReceived = System.nanoTime();
                }
            } catch (Exception failure) { reports.clear(); message("zstdnet.report.failed"); }
        }
    }

    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.gameSettings.hideGUI || !ClientConfig.hudVisible()) return;
        List<String> lines = new ArrayList<>();
        var host = mc.getIntegratedServer() != null ? ServerProxyBootstrap.currentHudSnapshot()
            : System.nanoTime() - hudReceived < 5_000_000_000L ? remoteHud : null;
        if (host != null) {
            lines.add(tr("zstdnet.hud.host.title", tr("zstdnet.hud.mode." + host.mode().toLowerCase(Locale.ROOT)), host.listenHost(), host.listenPort()));
            lines.add(tr("zstdnet.hud.server.zstd_rate", rate(host.zstdDownRate()), rate(host.zstdUpRate())));
            lines.add(tr("zstdnet.hud.server.raw_rate", rate(host.rawDownRate()), rate(host.rawUpRate())));
            lines.add(tr("zstdnet.hud.server.zstd_total", size(host.zstdBytes())));
            lines.add(tr("zstdnet.hud.server.raw_total", size(host.rawBytes())));
            lines.add(tr("zstdnet.hud.server.ratio", percent(host.ratioPercent())));
            lines.add(tr("zstdnet.hud.connections", host.connections()));
        }
        var client = ClientConnection.snapshot();
        if (client != null) {
            lines.add(tr("zstdnet.hud.client.title", tr("zstdnet.hud.mode." + client.mode().name().toLowerCase(Locale.ROOT)), client.remoteHost(), client.remotePort()));
            lines.add(tr("zstdnet.hud.client.wire", rate(client.wireUpRate()), rate(client.wireDownRate())));
            lines.add(tr("zstdnet.hud.client.raw", rate(client.rawUpRate()), rate(client.rawDownRate())));
            lines.add(tr("zstdnet.hud.total_ratio", size(client.wireUpBytes() + client.wireDownBytes()), size(client.rawUpBytes() + client.rawDownBytes()), percent(client.ratioPercent())));
        }
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.fontRenderer.getStringWidth(line));
        if (lines.isEmpty()) return;
        Gui.drawRect(4, 4, width + 12, 10 + lines.size() * 10, 0xa0101820);
        int y = 8;
        for (String line : lines) { mc.fontRenderer.drawStringWithShadow(line, 8, y, 0xffffff); y += 10; }
    }
    private static String size(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }
    private static String rate(long bytes) { return size(bytes) + "/s"; }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.2f%%", value); }
    private static String tr(String key, Object... args) { return I18n.format(key, args); }
    public static void message(String key, Object... args) {
        if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentTranslation(key, args));
    }

    private final class LocalCommand extends CommandBase {
        private final String name;
        LocalCommand(String name) { this.name = name; }
        @Override public String getCommandName() { return name; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/" + name; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
            if (args.length != 1) return null;
            return getListOfStringsMatchingLastWord(args, name.equals("zstdhud") ? new String[] {"status", "on", "off", "toggle"}
                : name.equals("zstdreport") ? new String[] {"today", "session", "24h", "7d", "30d"}
                : new String[] {"show", "game", "zstd", "voice", "zstdvoice"});
        }
        @Override public void processCommand(ICommandSender sender, String[] args) {
            try {
                if (name.equals("zstdhud")) hud(args);
                else if (name.equals("zstdreport")) report(args);
                else port(args);
            } catch (Exception failure) { message("zstdnet.command.port.write_failed"); }
        }
        private void hud(String[] args) {
            String action = args.length == 0 ? "status" : args[0];
            switch (action) {
                case "on" -> ClientConfig.setHudVisible(true);
                case "off" -> ClientConfig.setHudVisible(false);
                case "toggle" -> ClientConfig.setHudVisible(!ClientConfig.hudVisible());
                case "status" -> { message("zstdnet.command.hud.status", tr(ClientConfig.hudVisible() ? "options.on" : "options.off")); return; }
                default -> { message("zstdnet.command.usage.hud"); return; }
            }
            message(ClientConfig.hudVisible() ? "zstdnet.command.hud.enabled" : "zstdnet.command.hud.disabled");
        }
        private void report(String[] args) {
            String range = args.length == 0 ? "today" : args[0];
            if (args.length > 1 || !List.of("today", "session", "24h", "7d", "30d").contains(range)) { message("zstdnet.report.invalid_range"); return; }
            int id = ids.incrementAndGet();
            reports.begin(id, System.nanoTime());
            ReportNetwork.request(id, range);
            message("zstdnet.report.requested", range);
        }
        private void port(String[] args) throws Exception {
            String action = args.length == 0 ? "show" : args[0];
            if (action.equals("show")) {
                int active = ServerProxyBootstrap.advertisedPort();
                message("zstdnet.command.port.status", active > 0 ? active : ServerProxyConfigFile.readListenPort(),
                    ServerProxyBootstrap.gamePort() > 0 ? ServerProxyBootstrap.gamePort() : ServerProxyConfigFile.readTargetPort());
                message("zstdnet.command.port.voice_status", ServerProxyConfigFile.readVoiceListenPort(), ServerProxyConfigFile.readVoiceTargetPort());
                if (Minecraft.getMinecraft().getIntegratedServer() == null) message("zstdnet.command.port.dedicated");
                return;
            }
            if (args.length != 2 || !args[1].matches("[0-9]{1,5}")) { message("zstdnet.command.usage.port"); return; }
            int port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) { message("zstdnet.share_to_lan.port_invalid"); return; }
            if (action.equals("copy")) { GuiScreen.setClipboardString(Integer.toString(port)); return; }
            Minecraft mc = Minecraft.getMinecraft();
            IntegratedServer server = mc.getIntegratedServer();
            var host = server == null ? null : server.getConfigurationManager().func_152612_a(mc.getSession().getUsername());
            if (server == null || host == null || !server.getServerOwner().equalsIgnoreCase(mc.getSession().getUsername())
                || !host.canCommandSenderUseCommand(2, "zstdport")) { message("zstdnet.command.port.no_permission"); return; }
            switch (action) {
                case "game" -> { ServerProxyConfigFile.writeTargetPort(port); message(ServerProxyBootstrap.gamePort() > 0 ? "zstdnet.command.port.game_set_reopen" : "zstdnet.command.port.game_set", port); }
                case "zstd" -> { ServerProxyConfigFile.writeListenPort(port); message("zstdnet.command.port.zstd_set", port); }
                case "voice" -> { ServerProxyConfigFile.writeVoiceTargetPort(port); message("zstdnet.command.port.voice_target_set", port); }
                case "zstdvoice" -> { ServerProxyConfigFile.writeVoiceListenPort(port); message("zstdnet.command.port.voice_listen_set", port); }
                default -> message("zstdnet.command.usage.port");
            }
        }
    }
}
