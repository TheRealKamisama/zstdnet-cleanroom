package cn.tohsaka.factory.zstdnet;

import net.minecraftforge.common.config.Configuration;

public final class ClientConfig {
    private static Configuration config;
    private static int level = 3;
    private static boolean hud = true;
    private static long lastModified;
    private ClientConfig() {}
    public static synchronized void load() {
        config = new Configuration(Platform.configDirectory().resolve("zstdnet-client.cfg").toFile());
        level = config.getInt("level", "general", 3, 1, 22, "Zstd compression level for client-to-server traffic");
        hud = config.getBoolean("hud", "general", true, "Display ZstdNet traffic HUD");
        if (config.hasChanged()) config.save();
        lastModified = config.getConfigFile().lastModified();
    }
    public static synchronized int getLevel() {
        reloadIfChanged();
        return level;
    }
    public static boolean hudVisible() { return hud; }
    public static synchronized void setHudVisible(boolean value) {
        reloadIfChanged();
        hud = value;
        config.get("general", "hud", true).set(value);
        config.save();
        lastModified = config.getConfigFile().lastModified();
    }
    private static void reloadIfChanged() {
        if (config == null || config.getConfigFile().lastModified() != lastModified) load();
    }
}
