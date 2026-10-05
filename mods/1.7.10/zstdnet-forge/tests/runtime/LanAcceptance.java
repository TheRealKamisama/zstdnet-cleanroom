package cn.tohsaka.factory.zstdnet.acceptance;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Isolated integrated-server acceptance fixture; not part of the product. */
@Mod(modid = "zstdnetlanacceptance", name = "ZstdNet LAN acceptance", version = "1", acceptableRemoteVersions = "*")
public final class LanAcceptance {
    private final Path root = Path.of(System.getProperty("zstdnet.acceptance.root"));
    private int stage;
    private long deadline;
    private long reportRequestedAt;
    private int actualGame;
    private int changedRelay;
    private int nextGame;
    private int voice;
    private int publicVoice;
    private Object player;
    private Object creative;
    private ServerSocket occupied;
    private CompletableFuture<String> advertised;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if ("lan".equals(System.getProperty("zstdnet.acceptance.mode"))) FMLCommonHandler.instance().bus().register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0 || stage == 99) return;
        try {
            Object mc = method(Class.forName("net.minecraft.client.Minecraft"), 0, "getMinecraft", "func_71410_x").invoke(null);
            player = field(mc.getClass(), "thePlayer", "field_71439_g").get(mc);
            if (player == null) return;
            long now = System.currentTimeMillis();
            if (stage == 0) {
                // The launcher first joins the dedicated test server, validating reconnect.
                disconnect(mc);
                Class<?> gameType = Class.forName("net.minecraft.world.WorldSettings$GameType");
                for (Object candidate : gameType.getEnumConstants()) {
                    if (((Enum<?>) candidate).name().equals("CREATIVE")) creative = candidate;
                }
                if (creative == null) throw new AssertionError("Creative game type missing");
                Class<?> worldType = Class.forName("net.minecraft.world.WorldType");
                Object flat = field(worldType, "FLAT", "field_77138_c").get(null);
                Class<?> settingsType = Class.forName("net.minecraft.world.WorldSettings");
                Object settings = settingsType.getConstructor(long.class, gameType, boolean.class, boolean.class, worldType)
                    .newInstance(71025L, creative, true, false, flat);
                method(settingsType, 0, "enableCommands", "func_77166_b").invoke(settings);
                stage = 1;
                method(mc.getClass(), 3, "launchIntegratedServer", "func_71371_a")
                    .invoke(mc, "zstdnet-acceptance-lan", "ZstdNet LAN acceptance", settings);
                deadline = now + 60000;
            } else if (stage == 1) {
                Object server = method(mc.getClass(), 0, "getIntegratedServer", "func_71401_C").invoke(mc);
                if (server == null) { if (now > deadline) throw new AssertionError("Integrated server did not start"); return; }
                // Construct the actual transformed legacy screen and verify injected controls.
                Class<?> guiType = Class.forName("net.minecraft.client.gui.GuiShareToLan");
                Object gui = guiType.getConstructor(Class.forName("net.minecraft.client.gui.GuiScreen")).newInstance(new Object[] {null});
                method(guiType, 3, "setWorldAndResolution", "func_146280_a").invoke(gui, mc, 854, 480);
                for (String name : new String[] {"zstdnet$game", "zstdnet$zstd"}) {
                    Field control = guiType.getDeclaredField(name);
                    control.setAccessible(true);
                    if (control.get(gui) == null) throw new AssertionError("Missing LAN control " + name);
                }
                record("lan-widgets", "Both game and Zstd fields exist in the transformed production GuiShareToLan");
                occupied = new ServerSocket(0);
                int preferredGame = freePort();
                Class<?> config = Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile");
                for (String name : new String[] {"zstdnet$game", "zstdnet$zstd"}) {
                    Field control = guiType.getDeclaredField(name);
                    control.setAccessible(true);
                    Object value = control.get(gui);
                    method(value.getClass(), 1, "setText", "func_146180_a").invoke(value,
                        Integer.toString(name.endsWith("game") ? preferredGame : occupied.getLocalPort()));
                }
                Field cheats = guiType.getDeclaredField("field_146600_i");
                cheats.setAccessible(true);
                cheats.setBoolean(gui, true);
                Class<?> buttonType = Class.forName("net.minecraft.client.gui.GuiButton");
                Object publish = buttonType.getConstructor(int.class, int.class, int.class, String.class)
                    .newInstance(101, 0, 0, "Start LAN World");
                Method action = null;
                for (Method candidate : guiType.getDeclaredMethods()) {
                    if (List.of("actionPerformed", "func_146284_a").contains(candidate.getName()) && candidate.getParameterCount() == 1) action = candidate;
                }
                if (action == null) throw new AssertionError("LAN publish button handler missing");
                action.setAccessible(true);
                action.invoke(gui, publish);
                actualGame = (int) bootstrap("gamePort");
                if (actualGame <= 0) throw new AssertionError("LAN button did not publish the world");
                if (actualGame != preferredGame) throw new AssertionError("Configured game port not used");
                int advertised = (int) bootstrap("advertisedPort");
                if (advertised <= 0 || advertised == occupied.getLocalPort() || advertised == actualGame) throw new AssertionError("No free relay port fallback");
                if ((int) bootstrap("gamePort") != actualGame) throw new AssertionError("Actual LAN port not tracked");
                if ((boolean) method(server.getClass(), 0, "isServerInOnlineMode", "func_71266_T").invoke(server)) throw new AssertionError("LAN backend authentication mismatch");
                occupied.close();
                occupied = null;
                probeStatus(advertised);
                record("lan-publish", "Game=" + actualGame + ", relay=" + advertised + "; production screen fields/button, occupied-port fallback and real status passed");
                nextGame = freePort();
                changedRelay = freePort();
                voice = freePort();
                publicVoice = freePort();
                this.advertised = CompletableFuture.supplyAsync(() -> {
                    try (MulticastSocket listener = new MulticastSocket(null)) {
                        listener.setReuseAddress(true);
                        listener.bind(new InetSocketAddress(4445));
                        listener.joinGroup(InetAddress.getByName("224.0.2.60"));
                        listener.setSoTimeout(8000);
                        long until = System.currentTimeMillis() + 8000;
                        while (System.currentTimeMillis() < until) {
                            DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
                            listener.receive(packet);
                            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                            if (message.contains("[AD]" + changedRelay + "[/AD]")) return message;
                        }
                        throw new AssertionError("Updated LAN advertisement not received");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                });
                command("/zstdport game " + nextGame);
                command("/zstdport voice " + voice);
                command("/zstdport zstdvoice " + publicVoice);
                command("/zstdport zstd " + changedRelay);
                deadline = now + 15000;
                stage = 2;
            } else if (stage == 2) {
                int advertised = (int) bootstrap("advertisedPort");
                if (advertised == changedRelay && this.advertised.isDone()) {
                    record("lan-advertisement", this.advertised.get());
                    Class<?> config = Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile");
                    if ((int) config.getMethod("readTargetPort").invoke(null) != nextGame) throw new AssertionError("Game config not persisted");
                    if ((int) config.getMethod("readVoiceTargetPort").invoke(null) != voice) throw new AssertionError("Voice target not persisted");
                    if ((int) config.getMethod("readVoiceListenPort").invoke(null) != publicVoice) throw new AssertionError("Public voice port not persisted");
                    if ((int) bootstrap("gamePort") != actualGame) throw new AssertionError("Live backend changed before republish");
                    probeStatus(advertised);
                    command("/zstdport show");
                    record("lan-commands", "All four host port mutations persisted; public relay reloaded; current backend stayed on actual game port");
                    reportRequestedAt = now;
                    command("/zstdreport session");
                    deadline = now + 15000;
                    stage = 3;
                } else if (now > deadline) throw new AssertionError("LAN relay did not hot reload");
            } else if (stage == 3) {
                Path reports = root.resolve("../.minecraft/config/zstdnet/reports").normalize();
                try (var files = Files.walk(reports)) {
                    Path local = files.filter(p -> p.getFileName().toString().equals("latest.html") && p.getParent().getFileName().toString().startsWith("singleplayer-")).findFirst().orElse(null);
                    if (local != null && Files.getLastModifiedTime(local).toMillis() >= reportRequestedAt
                        && Files.readString(local).contains("\"range\":\"session\"")) {
                        record("lan-report", "Integrated-server admin session report generated through the game channel: " + local);
                        disconnect(mc);
                        record("lan-passed", "Integrated publication, widget injection, free-port fallback, host commands, hot reload, real status, report and shutdown API checks passed");
                        Files.deleteIfExists(root.resolve("lan-failed.txt"));
                        stage = 99;
                    } else if (now > deadline) throw new AssertionError("LAN session report not received");
                }
            }
        } catch (Throwable error) {
            try { if (occupied != null) occupied.close(); record("lan-failed", error.toString()); } catch (Exception ignored) {}
            error.printStackTrace();
            stage = -1;
        }
    }
    private Object bootstrap(String name) throws Exception {
        return Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap").getMethod(name).invoke(null);
    }
    private void disconnect(Object mc) throws Exception {
        Object world = field(mc.getClass(), "theWorld", "field_71441_e").get(mc);
        if (world != null) method(world.getClass(), 0, "sendQuittingDisconnectingPacket", "func_72882_A").invoke(world);
        method(mc.getClass(), 1, "loadWorld", "func_71403_a").invoke(mc, new Object[] {null});
        // Match vanilla Quit-to-title: clear in-game focus before the next render.
        Object menu = Class.forName("net.minecraft.client.gui.GuiMainMenu").getConstructor().newInstance();
        method(mc.getClass(), 1, "displayGuiScreen", "func_147108_a").invoke(mc, menu);
    }
    private void command(String text) throws Exception {
        Class<?> type = Class.forName("net.minecraftforge.client.ClientCommandHandler");
        method(type, 2, "executeCommand", "func_71556_a").invoke(type.getField("instance").get(null), player, text);
    }
    private static int freePort() throws Exception { try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
    private static void probeStatus(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(4000);
            byte[] host = "127.0.0.1".getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(new byte[] {(byte) (host.length + 6), 0, 5, (byte) host.length});
            socket.getOutputStream().write(host);
            socket.getOutputStream().write(new byte[] {(byte) (port >> 8), (byte) port, 1, 1, 0});
            socket.getOutputStream().flush();
            int length = varInt(socket);
            byte[] response = socket.getInputStream().readNBytes(length);
            if (!new String(response, StandardCharsets.UTF_8).contains("\"zstdnet\":1")) throw new AssertionError("Public LAN status lacks relay capability");
        }
    }
    private static int varInt(Socket socket) throws Exception {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = socket.getInputStream().read();
            if (next < 0) throw new AssertionError("Status EOF");
            value |= (next & 127) << shift;
            if ((next & 128) == 0) return value;
        }
        throw new AssertionError("Invalid status length");
    }
    private void record(String name, String value) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve(name + ".txt"), value + "\n");
        System.out.println("ZSTDNET_ACCEPTANCE " + name + ": " + value);
    }
    private static Field field(Class<?> type, String... names) throws Exception {
        for (String name : names) { try { return type.getField(name); } catch (NoSuchFieldException ignored) {} }
        throw new NoSuchFieldException(String.join("/", names));
    }
    private static Method method(Class<?> type, int arity, String... names) throws Exception {
        for (Method candidate : type.getMethods()) {
            if (List.of(names).contains(candidate.getName()) && candidate.getParameterCount() == arity) return candidate;
        }
        throw new NoSuchMethodException(String.join("/", names));
    }
}
