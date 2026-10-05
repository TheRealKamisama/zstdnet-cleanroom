package cn.tohsaka.factory.zstdnet.coremod;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;

public final class ServerRealIpHooks {
    private static final String PREFIX = "zstdnet-real-ip=";
    private static final Map<NetworkManager, Boolean> FORWARDED = Collections.synchronizedMap(new WeakHashMap<>());
    private ServerRealIpHooks() {}

    public static boolean isForwardedConnection(NetworkManager manager) { return FORWARDED.containsKey(manager); }

    public static void apply(NetworkManager manager, C00Handshake packet) {
        if (packet.func_149594_c() != EnumConnectionState.LOGIN) return;
        SocketAddress peer = manager.getSocketAddress();
        if (!(peer instanceof InetSocketAddress address) || address.getAddress() == null
            || !address.getAddress().isLoopbackAddress()) return;
        String host = ((HandshakeAccess) packet).zstdnet$getHost();
        String source = extractLiteral(host);
        if (source == null) return;
        try {
            InetSocketAddress forwarded = new InetSocketAddress(InetAddress.getByName(source), address.getPort());
            ((ForwardedAddressAccess) manager).zstdnet$setAddress(forwarded);
            FORWARDED.put(manager, Boolean.TRUE);
        } catch (Exception ignored) {
            // Invalid metadata never broadens the trust boundary.
        }
    }

    static String extractLiteral(String host) {
        if (host == null || host.length() > 255) return null;
        String found = null;
        for (String part : host.split("\0", -1)) {
            if (!part.startsWith(PREFIX)) continue;
            if (found != null || part.length() > PREFIX.length() + 96) return null;
            try {
                String value = new String(Base64.getUrlDecoder().decode(part.substring(PREFIX.length())), StandardCharsets.US_ASCII);
                found = validateLiteral(value);
                if (found == null) return null;
            } catch (Exception invalid) { return null; }
        }
        if (found != null) return found;
        String legacyMarker = "\0zstdnet-real-ip\0";
        int legacy = host.lastIndexOf(legacyMarker);
        return legacy < 0 ? null : validateLiteral(host.substring(legacy + legacyMarker.length()).trim());
    }

    private static String validateLiteral(String value) {
        if (value.length() > 45 || value.isEmpty()) return null;
        // Never resolve a hostname supplied by a handshake, including legacy metadata.
        try {
            if (value.indexOf(':') >= 0) {
                if (!value.matches("[0-9A-Fa-f:.]+")) return null;
                if (InetAddress.getByName(value).getAddress().length != 16) return null;
            } else {
                String[] octets = value.split("\\.", -1);
                if (octets.length != 4) return null;
                for (String octet : octets) {
                    if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255) return null;
                }
            }
            return value;
        } catch (Exception invalid) { return null; }
    }
}
