package cn.tohsaka.factory.zstdnet.server;

import java.nio.file.Files;
import java.nio.file.Path;
import cn.tohsaka.factory.zstdnet.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @TempDir Path directory;

    @Test void modeSpecificVoiceDefaultsAndAdvancedKeysSurviveWrites() throws Exception {
        Platform.configure(directory, false);
        assertEquals(24454, ServerProxyConfigFile.readVoiceTargetPort());
        Platform.configure(directory, true);
        assertEquals(25565, ServerProxyConfigFile.readVoiceTargetPort());
        Files.writeString(ServerProxyConfigFile.path(), "max_worker_threads=42\ncustom_key=keep\n");
        ServerProxyConfigFile.writePorts(35565, 25566);
        var config = ServerProxyConfigFile.loadProperties();
        assertEquals("42", config.getProperty("max_worker_threads"));
        assertEquals("keep", config.getProperty("custom_key"));
        assertEquals("128", config.getProperty("max_conn_total"));
        assertEquals("3s", config.getProperty("handshake_timeout"));
    }

    @Test void autoTakeoverPersistsRestrictedIpv4AndIpv6BindHosts() throws Exception {
        Platform.configure(directory, false);
        ServerProxyConfigFile.writeResolvedAutoTakeoverConfig("127.0.0.1", 25565, 25566);
        assertEquals("127.0.0.1:25565", ServerProxyConfigFile.loadProperties().getProperty("listen"));
        ServerProxyConfigFile.writeResolvedAutoTakeoverConfig("::1", 25565, 25566);
        assertEquals("[::1]:25565", ServerProxyConfigFile.loadProperties().getProperty("listen"));
    }

    @Test void generatedDedicatedDefaultsContainNoUnexpandedTokens() throws Exception {
        var method = ServerProxyRuntime.class.getDeclaredMethod("buildDedicatedDefaultConfig");
        method.setAccessible(true);
        String config = (String) method.invoke(new ServerProxyRuntime());
        assertFalse(config.contains("${"));
        assertTrue(config.contains("voice_chat_target=127.0.0.1:24454"));
        assertTrue(config.contains("voice_chat_listen=0.0.0.0:24455"));
    }
}
