package ru.nsu.ccfit.bogush.net.tou.segment;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;

import java.net.InetSocketAddress;

public class TOUSegment extends TCPSegment {
    private final InetSocketAddress src;
    private final InetSocketAddress dst;

    public TOUSegment(TCPSegment segment, InetSocketAddress src, InetSocketAddress dst) {
        super(segment.getBytes());
        this.src = src;
        this.dst = dst;
    }

    public InetSocketAddress getSrc() {
        return src;
    }

    public InetSocketAddress getDst() {
        return dst;
    }

    @Override
    public String toString() {
        return String.format("%16s[%s seq: %5d ack: %5d data offset: %3d capacity: %3d src: %16s:%-5d dst: %16s:%-5d]",
                TOUSegment.class.getSimpleName(), flagsToString(), getSEQ(), getACK(), HEADER_SIZE, capacity(),
                src.getAddress().getHostAddress(), src.getPort(), dst.getAddress().getHostAddress(), dst.getPort());
    }
}
