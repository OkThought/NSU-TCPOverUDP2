package ru.nsu.ccfit.bogush.net.tou.segment;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;

import java.net.InetSocketAddress;

public class TOUSegment extends TCPSegment {
    private final InetSocketAddress src;
    private final InetSocketAddress dst;
    private long timeout = -1;

    public TOUSegment(TOUSegment segment) {
        super(segment.getBytes().clone());
        src = segment.getSrc();
        dst = segment.getDst();
    }

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

    public TOUSegment setTimeout(long timeout) {
        this.timeout = System.currentTimeMillis() + timeout;
        return this;
    }

    public boolean timedOut() {
        long t = System.currentTimeMillis();
        return t >= timeout;
    }

    @Override
    public String toString() {
        return String.format("%16s[%s seq: %5d ack: %5d data offset: %3d capacity: %3d src: %16s:%-5d dst: %16s:%-5d]",
                TOUSegment.class.getSimpleName(), flagsToString(), getSEQ(), getACK(), HEADER_SIZE, capacity(),
                src.getAddress().getHostAddress(), src.getPort(), dst.getAddress().getHostAddress(), dst.getPort());
    }
}
