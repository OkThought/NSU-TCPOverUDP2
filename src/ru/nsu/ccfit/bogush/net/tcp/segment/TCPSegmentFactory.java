package ru.nsu.ccfit.bogush.net.tcp.segment;


import java.util.Arrays;
import java.util.Random;

import static ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType.*;

public final class TCPSegmentFactory {
    private static final Random RANDOM = new Random();

    public static TCPSegment generateSYN() {
        return create(SYN, RANDOM.nextInt(), 0);
    }

    public static TCPSegment generateSYNACK(int seq) {
        return create(SYNACK, RANDOM.nextInt(), seq + 1);
    }

    public static TCPSegment generateFIN() {
        return create(FIN, RANDOM.nextInt(), 0);
    }

    public static TCPSegment generateFINACK(int seq) {
        return create(FINACK, RANDOM.nextInt(), seq + 1);
    }

    public static TCPSegment createEmptyACK(int seq) {
        return create(ACK, 0, seq);
    }

    public static TCPSegment create(TCPSegmentType type, int seq, int ack) {
        return create(type, seq, ack, 0);
    }

    public static TCPSegment create(TCPSegmentType type, int seq, int ack, int size) {
        return new TCPSegment(size).setFlags(type.toByte()).setSEQ(seq).setACK(ack);
    }

    private TCPSegmentFactory() {}
}
