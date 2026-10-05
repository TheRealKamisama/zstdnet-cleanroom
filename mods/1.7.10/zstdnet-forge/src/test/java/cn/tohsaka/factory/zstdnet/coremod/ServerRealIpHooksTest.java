package cn.tohsaka.factory.zstdnet.coremod;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerRealIpHooksTest {
    @Test void acceptsEncodedAndLegacyIpLiteralsWithoutRemovingForgeMarkers() {
        assertEquals("203.0.113.7", ServerRealIpHooks.extractLiteral("host\0FML\0" + token("203.0.113.7")));
        assertEquals("2001:db8::7", ServerRealIpHooks.extractLiteral("host\0FML\0" + token("2001:db8::7")));
        assertEquals("203.0.113.7", ServerRealIpHooks.extractLiteral("host\0zstdnet-real-ip\0" + "203.0.113.7"));
    }

    @Test void rejectsHostnamesInvalidAddressesDuplicateTokensAndOversizedHosts() {
        for (String value : new String[] {"localhost", "example.com", "256.1.2.3", "1.2.3", "::zzz", "", "1.2.3.4\0extra"}) {
            assertNull(ServerRealIpHooks.extractLiteral("host\0" + token(value)), value);
            assertNull(ServerRealIpHooks.extractLiteral("host\0zstdnet-real-ip\0" + value), value);
        }
        assertNull(ServerRealIpHooks.extractLiteral("host\0" + token("1.2.3.4") + "\0" + token("5.6.7.8")));
        assertNull(ServerRealIpHooks.extractLiteral("x".repeat(256) + "\0" + token("1.2.3.4")));
    }
    private static String token(String ip) {
        return "zstdnet-real-ip=" + Base64.getUrlEncoder().withoutPadding().encodeToString(ip.getBytes(StandardCharsets.US_ASCII));
    }
}
