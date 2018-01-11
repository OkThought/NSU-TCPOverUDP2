package ru.nsu.ccfit.bogush.net.tou.segment;

import ru.nsu.ccfit.bogush.factory.Factory;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentFactory;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType;

import java.net.InetSocketAddress;

public class TOUSegmentFactory implements Factory<TOUSegment, TCPSegmentType> {
    private final InetSocketAddress local;
    private final InetSocketAddress remote;
    private final TCPSegmentFactory tcpSegmentFactory = new TCPSegmentFactory();

    public TOUSegmentFactory(InetSocketAddress local, InetSocketAddress remote) {
        this.local = local;
        this.remote = remote;
    }

    public TOUSegment create(TCPSegmentType type, Object... args) {
        return new TOUSegment(tcpSegmentFactory.create(type, args), local, remote);
    }

    public static TOUSegment create(TCPSegmentType type, InetSocketAddress local, InetSocketAddress remote,
                                    Object... args) {
        return new TOUSegment(TCPSegmentFactory.staticCreate(type, args), local, remote);
    }

    public static TOUSegment create(TCPSegmentType type, TOUSegment segment) {
        return new TOUSegment(TCPSegmentFactory.staticCreate(type, segment), segment.getDst(), segment.getSrc());
    }
}
