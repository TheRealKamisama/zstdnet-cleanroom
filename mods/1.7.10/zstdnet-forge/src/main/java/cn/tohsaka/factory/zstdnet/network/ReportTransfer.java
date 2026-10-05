package cn.tohsaka.factory.zstdnet.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Aggregate JSON framing below 1.7.10's 32767-byte custom-payload limit. */
public final class ReportTransfer {
    public static final int MAX_REPORT_BYTES = 1024 * 1024;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int HEADER_BYTES = 17;
    public static final int MAX_PACKET_BYTES = HEADER_BYTES + CHUNK_BYTES;
    public static final long TIMEOUT_NANOS = 30_000_000_000L;

    private ReportTransfer() {}

    public record Result(boolean success, String payload) {}

    public static List<byte[]> split(int requestId, boolean success, String payload) throws IOException {
        byte[] utf8 = payload.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0 || utf8.length > MAX_REPORT_BYTES) {
            throw new IOException("Report must contain 1.." + MAX_REPORT_BYTES + " UTF-8 bytes");
        }
        int count = (utf8.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        List<byte[]> chunks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int start = index * CHUNK_BYTES;
            int length = Math.min(CHUNK_BYTES, utf8.length - start);
            ByteBuffer packet = ByteBuffer.allocate(HEADER_BYTES + length);
            packet.putInt(requestId).putInt(utf8.length).putInt(index).putInt(count).put((byte) (success ? 1 : 0));
            packet.put(utf8, start, length);
            chunks.add(packet.array());
        }
        return chunks;
    }

    /** One outstanding user request per connection. Begin on request, clear on disconnect. */
    public static final class Receiver {
        private int requestId;
        private long startedAt;
        private boolean awaiting;
        private int expectedTotal;
        private int expectedCount;
        private boolean success;
        private int nextIndex;
        private ByteArrayOutputStream buffer;

        public synchronized void begin(int id, long nowNanos) {
            clear();
            requestId = id;
            startedAt = nowNanos;
            awaiting = true;
        }

        public synchronized void clear() {
            awaiting = false;
            buffer = null;
            nextIndex = 0;
            expectedTotal = 0;
            expectedCount = 0;
        }

        public synchronized boolean expire(long nowNanos) {
            if (awaiting && nowNanos - startedAt >= TIMEOUT_NANOS) {
                clear();
                return true;
            }
            return false;
        }

        public synchronized Optional<Result> accept(byte[] wire, long nowNanos) throws IOException {
            if (expire(nowNanos) || !awaiting) {
                return Optional.empty();
            }
            try {
                if (wire.length < HEADER_BYTES || wire.length > MAX_PACKET_BYTES) {
                    throw new IOException("Invalid report packet length");
                }
                ByteBuffer packet = ByteBuffer.wrap(wire);
                int id = packet.getInt();
                if (id != requestId) {
                    return Optional.empty(); // Late response from a superseded request.
                }
                int total = packet.getInt();
                int index = packet.getInt();
                int count = packet.getInt();
                byte status = packet.get();
                if (total < 1 || total > MAX_REPORT_BYTES || count != (total + CHUNK_BYTES - 1) / CHUNK_BYTES
                    || index != nextIndex || index >= count || status < 0 || status > 1) {
                    throw new IOException("Invalid report chunk header or sequence");
                }
                int expectedBytes = Math.min(CHUNK_BYTES, total - index * CHUNK_BYTES);
                if (packet.remaining() != expectedBytes) {
                    throw new IOException("Invalid report chunk size");
                }
                if (index == 0) {
                    expectedTotal = total;
                    expectedCount = count;
                    success = status == 1;
                    buffer = new ByteArrayOutputStream(total);
                } else if (total != expectedTotal || count != expectedCount || success != (status == 1)) {
                    throw new IOException("Report header changed during transfer");
                }
                buffer.write(wire, HEADER_BYTES, packet.remaining());
                nextIndex++;
                if (nextIndex != expectedCount) {
                    return Optional.empty();
                }
                byte[] bytes = buffer.toByteArray();
                if (bytes.length != expectedTotal) {
                    throw new IOException("Incomplete report");
                }
                String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
                Result result = new Result(success, text);
                clear();
                return Optional.of(result);
            } catch (IOException | RuntimeException failure) {
                clear();
                throw failure;
            }
        }
    }
}
