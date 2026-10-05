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
import net.minecraftforge.common.config.Configuration;

/** Production saved-server/reconnect/config reload fixture; never shipped. */
@Mod(modid = "zstdnetconnectionacceptance", name = "ZstdNet connection acceptance", version = "1", acceptableRemoteVersions = "*")
public final class ConnectionAcceptance {
    private final Path root = Path.of(System.getProperty("zstdnet.acceptance.root"));
    private final String address = "127.0.0.1:" + System.getProperty("zstdnet.acceptance.port");
    private int stage;
    private int connects;
    private long deadline;
    private Object entry;
    private Object list;
    private int entryIndex;
    private int originalLevel;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if ("connection".equals(System.getProperty("zstdnet.acceptance.mode"))) FMLCommonHandler.instance().bus().register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0 || stage == 99) return;
        try {
            Class<?> minecraftType = Class.forName("net.minecraft.client.Minecraft");
            Object mc = method(minecraftType, 0, "getMinecraft", "func_71410_x").invoke(null);
            Object player = field(minecraftType, "thePlayer", "field_71439_g").get(mc);
            long now = System.currentTimeMillis();
            if (stage == 0 && player != null) {
                var file = root.resolve("../.minecraft/config/zstdnet-client.cfg").normalize().toFile();
                Configuration config = new Configuration(file);
                originalLevel = config.get("general", "level", 3).getInt();
                config.get("general", "level", 3).set(7);
                config.save();
                file.setLastModified(System.currentTimeMillis() + 2000);
                if ((int) Class.forName("cn.tohsaka.factory.zstdnet.ClientConfig").getMethod("getLevel").invoke(null) != 7) throw new AssertionError("Client config did not reload without restarting");
                record("config-reload", "Live client compression level reloaded to 7; reconnect must use the new level");
                Class<?> dataType = Class.forName("net.minecraft.client.multiplayer.ServerData");
                entry = dataType.getConstructor(String.class, String.class).newInstance("ZstdNet acceptance", address);
                Class<?> listType = Class.forName("net.minecraft.client.multiplayer.ServerList");
                list = listType.getConstructor(minecraftType).newInstance(mc);
                entryIndex = (int) method(listType, 0, "countServers", "func_78856_c").invoke(list);
                method(listType, 1, "addServerData", "func_78849_a").invoke(list, entry);
                method(listType, 0, "saveServerList", "func_78855_b").invoke(list);
                reconnect(mc);
                stage = 1;
                deadline = now + 30000;
            } else if (stage == 1 && player != null) {
                Object current = method(minecraftType, 0, "func_147104_D").invoke(mc);
                if (!address.equals(field(current.getClass(), "serverIP", "field_78845_b").get(current))) throw new AssertionError("Current server address replaced with local proxy");
                method(list.getClass(), 0, "loadServerList", "func_78853_a").invoke(list);
                Object saved = method(list.getClass(), 1, "getServerData", "func_78850_a").invoke(list, entryIndex);
                if (!address.equals(field(saved.getClass(), "serverIP", "field_78845_b").get(saved))) throw new AssertionError("Saved server address changed");
                connects++;
                record("reconnect-" + connects, "Real Forge reconnect completed through the saved-server constructor; original address persisted: " + address);
                if (connects < 2) { reconnect(mc); deadline = now + 30000; }
                else { disconnect(mc); stage = 2; deadline = now + 5000; }
            } else if (stage == 2) {
                Object snapshot = Class.forName("cn.tohsaka.factory.zstdnet.client.ClientConnection").getMethod("snapshot").invoke(null);
                if (snapshot == null) {
                    Configuration config = new Configuration(root.resolve("../.minecraft/config/zstdnet-client.cfg").normalize().toFile());
                    config.get("general", "level", 3).set(originalLevel);
                    config.save();
                    record("connection-passed", "Two same-process production reconnects, persisted saved address, live level reload and final session cleanup passed");
                    stage = 99;
                }
            }
            if (stage > 0 && stage != 99 && now > deadline) throw new AssertionError("Connection acceptance timed out at stage " + stage);
        } catch (Throwable failure) {
            try { record("connection-failed", failure.toString()); } catch (Exception ignored) {}
            failure.printStackTrace();
            stage = -1;
        }
    }
    private void reconnect(Object mc) throws Exception {
        Object menu = disconnect(mc);
        Class<?> guiType = Class.forName("net.minecraft.client.gui.GuiScreen");
        Object connecting = Class.forName("net.minecraft.client.multiplayer.GuiConnecting")
            .getConstructor(guiType, mc.getClass(), entry.getClass()).newInstance(menu, mc, entry);
        method(mc.getClass(), 1, "displayGuiScreen", "func_147108_a").invoke(mc, connecting);
    }
    private Object disconnect(Object mc) throws Exception {
        Object world = field(mc.getClass(), "theWorld", "field_71441_e").get(mc);
        if (world != null) method(world.getClass(), 0, "sendQuittingDisconnectingPacket", "func_72882_A").invoke(world);
        method(mc.getClass(), 1, "loadWorld", "func_71403_a").invoke(mc, new Object[] {null});
        Object menu = Class.forName("net.minecraft.client.gui.GuiMainMenu").getConstructor().newInstance();
        method(mc.getClass(), 1, "displayGuiScreen", "func_147108_a").invoke(mc, menu);
        return menu;
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
