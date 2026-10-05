package cn.tohsaka.factory.zstdnet.acceptance;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Optional client-only acceptance fixture. Never included in the product JAR.
 * Exercises commands and report networking through the real game APIs. */
@Mod(modid = "zstdnetacceptance", name = "ZstdNet runtime acceptance", version = "1", acceptableRemoteVersions = "*")
public final class ClientAcceptance {
    private final Path root = Path.of(System.getProperty("zstdnet.acceptance.root"));
    private final Path reports = root.resolve("../.minecraft/config/zstdnet/reports").normalize();
    private int stage;
    private int range;
    private long deadline;
    private long requestedAt;
    private Object player;
    private Object proxy;
    private static final List<String> RANGES = List.of("today", "session", "24h", "7d", "30d");

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!"reports".equals(System.getProperty("zstdnet.acceptance.mode", "reports"))) return;
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0 || stage == 99) return;
        try {
            long now = System.currentTimeMillis();
            Object minecraft = method(Class.forName("net.minecraft.client.Minecraft"), new String[] {"getMinecraft", "func_71410_x"}, 0).invoke(null);
            player = field(minecraft.getClass(), "thePlayer", "field_71439_g").get(minecraft);
            if (player == null) return;
            if (proxy == null) proxy = Class.forName("cn.tohsaka.factory.zstdnet.Zstdnet").getField("proxy").get(null);
            if (stage == 0) {
                Files.createDirectories(root);
                checkHud("off", false);
                checkHud("on", true);
                checkHud("toggle", false);
                checkHud("toggle", true);
                command("/zstdhud status");
                command("/zstdport show");
                Path localConfig = root.resolve("../.minecraft/config/zstdnet-server.properties").normalize();
                byte[] original = Files.exists(localConfig) ? Files.readAllBytes(localConfig) : null;
                command("/zstdport zstd 39999");
                byte[] updated = Files.exists(localConfig) ? Files.readAllBytes(localConfig) : null;
                if (!java.util.Arrays.equals(original, updated)) throw new AssertionError("Remote client changed local host config");
                command("/zstdreport today");
                deadline = now + 10000;
                stage = 1;
                record("commands", "HUD off/on/toggle/status and remote port mutation denial passed");
            } else if (stage == 1) {
                if (!awaiting()) {
                    if (latestReport() != null) throw new AssertionError("Unprivileged report generated HTML");
                    record("permission-denied", "Server answered the unprivileged request without a report; inspect client chat log for denial message");
                    record("ready-for-op", "Grant this isolated test player permission level 2, then create continue.txt");
                    stage = 2;
                } else if (now > deadline) throw new AssertionError("Permission response did not complete within 10 seconds");
            } else if (stage == 2 && Files.exists(root.resolve("continue.txt"))) {
                stage = 3;
                deadline = now + 6000;
            } else if (stage == 3 && now >= deadline) {
                requestedAt = now;
                command("/zstdreport " + RANGES.get(range));
                deadline = now + 15000;
                stage = 4;
            } else if (stage == 4) {
                Path latest = latestReport();
                if (!awaiting() && latest != null && Files.getLastModifiedTime(latest).toMillis() >= requestedAt) {
                    String html = Files.readString(latest);
                    if (!html.contains("\"range\":\"" + RANGES.get(range) + "\"")) throw new AssertionError("Wrong report range");
                    if (html.contains("/*__ZSTDNET_REPORT_JSON__*/")) throw new AssertionError("Unexpanded report template");
                    record("report-" + RANGES.get(range), latest + " (" + Files.size(latest) + " bytes)");
                    range++;
                    if (range == RANGES.size()) { record("passed", "All command and report API acceptance steps completed"); stage = 99; }
                    else { deadline = now + 6000; stage = 3; }
                } else if (now > deadline) throw new AssertionError("No completed report for " + RANGES.get(range));
            }
        } catch (Throwable failure) {
            try { record("failed", failure.toString()); } catch (Exception ignored) { }
            failure.printStackTrace();
            stage = -1;
        }
    }

    private void checkHud(String action, boolean expected) throws Exception {
        command("/zstdhud " + action);
        boolean actual = (boolean) Class.forName("cn.tohsaka.factory.zstdnet.ClientConfig").getMethod("hudVisible").invoke(null);
        if (actual != expected) throw new AssertionError("HUD " + action + " did not persist expected state");
    }
    private void command(String text) throws Exception {
        Class<?> type = Class.forName("net.minecraftforge.client.ClientCommandHandler");
        Object handler = type.getField("instance").get(null);
        method(type, new String[] {"executeCommand", "func_71556_a"}, 2).invoke(handler, player, text);
    }
    private boolean awaiting() throws Exception {
        Field receiver = proxy.getClass().getDeclaredField("reports");
        receiver.setAccessible(true);
        Object value = receiver.get(proxy);
        Field pending = value.getClass().getDeclaredField("awaiting");
        pending.setAccessible(true);
        return pending.getBoolean(value);
    }
    private Path latestReport() throws Exception {
        if (!Files.isDirectory(reports)) return null;
        try (var files = Files.walk(reports)) {
            return files.filter(p -> p.getFileName().toString().equals("latest.html")).findFirst().orElse(null);
        }
    }
    private void record(String name, String result) throws Exception {
        Files.writeString(root.resolve(name + ".txt"), result + "\n");
        System.out.println("ZSTDNET_ACCEPTANCE " + name + ": " + result);
    }
    private static Field field(Class<?> type, String... names) throws Exception {
        for (String name : names) { try { return type.getField(name); } catch (NoSuchFieldException ignored) { } }
        throw new NoSuchFieldException(String.join("/", names));
    }
    private static Method method(Class<?> type, String[] names, int arity) throws Exception {
        for (Method method : type.getMethods()) {
            if (List.of(names).contains(method.getName()) && method.getParameterCount() == arity) return method;
        }
        throw new NoSuchMethodException(String.join("/", names));
    }
}
