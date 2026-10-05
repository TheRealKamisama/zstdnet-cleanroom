package cn.tohsaka.factory.zstdnet;

import java.nio.file.Path;
import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClientConfigTest {
    @TempDir Path directory;
    @Test void reloadsCompressionLevelForNextConnectionAndPreservesItWhenSavingHud() throws Exception {
        var home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        Object previousHome = home.get(null);
        home.set(null, directory.toFile());
        try {
        Platform.configure(directory, true);
        ClientConfig.load();
        assertEquals(3, ClientConfig.getLevel());
        var file = directory.resolve("zstdnet-client.cfg").toFile();
        long originalTimestamp = file.lastModified();
        Configuration externalEdit = new Configuration(file);
        externalEdit.get("general", "level", 3).set(7);
        externalEdit.save();
        assertTrue(file.setLastModified(originalTimestamp + 2000));
        assertEquals(7, ClientConfig.getLevel());
        ClientConfig.setHudVisible(false);
        Configuration saved = new Configuration(file);
        assertEquals(7, saved.get("general", "level", 3).getInt());
        assertFalse(saved.get("general", "hud", true).getBoolean());
        } finally {
            home.set(null, previousHome);
        }
    }
}
