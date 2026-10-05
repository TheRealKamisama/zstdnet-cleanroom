package cn.tohsaka.factory.zstdnet;

import java.nio.file.Path;

/** Set during pre-init; no client-only linkage from transport or server classes. */
public final class Platform {
    private static volatile Path configDirectory = Path.of("config").toAbsolutePath();
    private static volatile boolean client;

    private Platform() {}

    public static void configure(Path directory, boolean clientSide) {
        configDirectory = directory.toAbsolutePath();
        client = clientSide;
    }

    public static Path configDirectory() { return configDirectory; }
    public static boolean isClient() { return client; }
}
