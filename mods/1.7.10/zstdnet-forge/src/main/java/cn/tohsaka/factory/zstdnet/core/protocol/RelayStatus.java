package cn.tohsaka.factory.zstdnet.core.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Advertises relay capability only on the public relay's status response. */
public final class RelayStatus {
    private RelayStatus() {}
    public static byte[] advertise(byte[] wire) throws IOException {
        byte[] payload = PacketIo.extractPacketPayload(wire);
        JsonObject status = parse(payload);
        status.addProperty("zstdnet", 1);
        byte[] json = status.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = ByteArrayOps.concat(new byte[] {0}, VarIntCodec.encode(json.length), json);
        return ByteArrayOps.concat(VarIntCodec.encode(result.length), result);
    }
    public static boolean isRelay(byte[] payload) throws IOException {
        JsonObject status = parse(payload);
        return status.has("zstdnet") && status.get("zstdnet").isJsonPrimitive()
            && status.getAsJsonPrimitive("zstdnet").isNumber() && status.get("zstdnet").getAsInt() == 1;
    }
    private static JsonObject parse(byte[] payload) throws IOException {
        try {
            VarIntRead id = VarIntCodec.read(payload, 0);
            if (id == null || id.value() != 0) throw new IOException("Not a status response");
            VarIntRead length = VarIntCodec.read(payload, id.next());
            if (length == null || length.value() < 0 || length.value() != payload.length - length.next()) {
                throw new IOException("Invalid status response length");
            }
            return JsonParser.parseString(new String(payload, length.next(), length.value(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException badStatus) { throw new IOException("Invalid status JSON", badStatus); }
    }
}
