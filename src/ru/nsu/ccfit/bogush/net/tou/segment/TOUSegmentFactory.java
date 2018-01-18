package ru.nsu.ccfit.bogush.net.tou.segment;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentFactory;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType;

import java.net.InetSocketAddress;

public final class TOUSegmentFactory {
    private TOUSegmentFactory() {}

    public static TOUSegment create(TCPSegment segment, InetSocketAddress address) {
        return create(segment, null, address);
    }

    public static TOUSegment create(TCPSegment segment, InetSocketAddress src, InetSocketAddress dst) {
        return new TOUSegment(segment, src, dst);
    }

    public static TOUSegment generateSYN(InetSocketAddress address) {
        return create(TCPSegmentFactory.generateSYN(), address);
    }

    public static TOUSegment generateSYNACK(TOUSegment synack) {
        return create(TCPSegmentFactory.generateSYNACK(synack.getSEQ()), synack.getDst(), synack.getSrc());
    }

    public static TOUSegment generateFIN(InetSocketAddress address) {
        return create(TCPSegmentFactory.generateFIN(), address);
    }

    public static TOUSegment generateFINACK(TOUSegment fin) {
        return create(TCPSegmentFactory.generateFINACK(fin.getSEQ()), fin.getDst(), fin.getSrc());
    }

    public static TOUSegment createEmptyACK(InetSocketAddress address, int seq) {
        return create(TCPSegmentFactory.createEmptyACK(seq), address);
    }

    public static TOUSegment createEmptyACK(TOUSegment dataSegment) {
        return create(TCPSegmentFactory.createEmptyACK(dataSegment.getSEQ()), dataSegment.getDst(), dataSegment.getSrc());
    }

    public static TOUSegment create(InetSocketAddress address, TCPSegmentType type, int seq, int ack) {
        return create(TCPSegmentFactory.create(type, seq, ack), address);
    }
}
