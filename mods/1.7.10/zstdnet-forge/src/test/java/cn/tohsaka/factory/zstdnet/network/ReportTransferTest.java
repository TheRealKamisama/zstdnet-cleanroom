package cn.tohsaka.factory.zstdnet.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportTransferTest {
    @Test
    void reassemblesLargeUnicodeReportWithinLegacyPacketLimit() throws Exception {
        String json = "{\"aggregate\":\"" + "流量统计".repeat(70000) + "\"}";
        List<byte[]> packets = ReportTransfer.split(42, true, json);
        ReportTransfer.Receiver receiver = new ReportTransfer.Receiver();
        receiver.begin(42, 0);
        ReportTransfer.Result completed = null;
        for (byte[] packet : packets) {
            assertTrue(packet.length < 32767);
            var result = receiver.accept(packet, 1);
            if (result.isPresent()) completed = result.get();
        }
        assertNotNull(completed);
        assertTrue(completed.success());
        assertEquals(json, completed.payload());
    }

    @Test
    void rejectsOversizedReportsBeforeAllocatingReceiverBuffer() throws Exception {
        byte[] wire = ReportTransfer.split(1, true, "{}").getFirst();
        ByteBuffer.wrap(wire).putInt(4, Integer.MAX_VALUE);
        ReportTransfer.Receiver receiver = new ReportTransfer.Receiver();
        receiver.begin(1, 0);
        assertThrows(IOException.class, () -> receiver.accept(wire, 1));
        assertTrue(receiver.accept(ReportTransfer.split(1, true, "{}").getFirst(), 2).isEmpty());
        assertThrows(IOException.class, () -> ReportTransfer.split(1, true, "a".repeat(ReportTransfer.MAX_REPORT_BYTES + 1)));
    }

    @Test
    void rejectsDuplicatesReorderingAndHeaderChanges() throws Exception {
        List<byte[]> packets = ReportTransfer.split(7, true, "a".repeat(30000));
        ReportTransfer.Receiver receiver = new ReportTransfer.Receiver();
        receiver.begin(7, 0);
        assertThrows(IOException.class, () -> receiver.accept(packets.get(1), 1));
        receiver.begin(7, 0);
        receiver.accept(packets.getFirst(), 1);
        assertThrows(IOException.class, () -> receiver.accept(packets.getFirst(), 2));
        receiver.begin(7, 0);
        receiver.accept(packets.getFirst(), 1);
        packets.get(1)[16] = 0;
        assertThrows(IOException.class, () -> receiver.accept(packets.get(1), 2));
    }

    @Test
    void dropsUnsolicitedLateTimedOutAndDisconnectedResponses() throws Exception {
        byte[] packet = ReportTransfer.split(3, false, "Permission denied").getFirst();
        ReportTransfer.Receiver receiver = new ReportTransfer.Receiver();
        assertTrue(receiver.accept(packet, 0).isEmpty());
        receiver.begin(4, 0);
        assertTrue(receiver.accept(packet, 1).isEmpty());
        receiver.begin(3, 0);
        assertTrue(receiver.accept(packet, ReportTransfer.TIMEOUT_NANOS).isEmpty());
        receiver.begin(3, 0);
        receiver.clear();
        assertTrue(receiver.accept(packet, 1).isEmpty());
        receiver.begin(3, 0);
        assertFalse(receiver.accept(packet, 1).orElseThrow().success());
    }

    @Test
    void rejectsInvalidUtf8() throws Exception {
        byte[] packet = ReportTransfer.split(3, true, "{}").getFirst();
        packet[17] = (byte) 0xff;
        ReportTransfer.Receiver receiver = new ReportTransfer.Receiver();
        receiver.begin(3, 0);
        assertThrows(IOException.class, () -> receiver.accept(packet, 1));
    }
}
