package cn.tohsaka.factory.zstdnet.core.protocol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketLimitsTest {
    @Test void rejectsOversizedNegativeTruncatedAndOverflowingLengths() {
        for (byte[] input : new byte[][] {VarIntCodec.encode(Integer.MAX_VALUE), VarIntCodec.encode(-2),
            new byte[] {5, 1}, new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80}}) {
            assertThrows(IOException.class, () -> PacketIo.readPacketWire(new ByteArrayInputStream(input), 2048));
        }
    }
}
