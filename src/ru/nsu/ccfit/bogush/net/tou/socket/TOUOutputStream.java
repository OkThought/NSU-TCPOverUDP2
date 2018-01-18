package ru.nsu.ccfit.bogush.net.tou.socket;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;

import java.io.IOException;
import java.io.OutputStream;

public class TOUOutputStream extends OutputStream {
    private static final int BUFFER_SIZE = 1<<10;

    private TOUSocketImpl impl;
    private int seq;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private int pos = 0;

    TOUOutputStream(TOUSocketImpl impl) {
        this.impl = impl;
        seq = impl.getInitialWriteSEQ();
    }

    @Override
    public synchronized void write(int b)
            throws IOException {
        if (impl.isClosedOrPending()) throw new IOException("Socket closed");
        if (impl.isOutShut()) throw new IOException("Output shutdown");

        while (pos == buffer.length) {
            try {
                wait();
            } catch (InterruptedException ignored) {}
        }
        buffer[pos] = (byte) b;
        ++pos;
        if (pos == 1) {
            try {
                impl.communicator.implsWithData.put(impl);
            } catch (InterruptedException ignored) {}
        }
    }

    int available() {
        return pos;
    }

    synchronized int getCurrentSEQ() {
        return seq;
    }



    synchronized TCPSegment flushIntoSegment() {
        TCPSegment segment = new TCPSegment(pos);
        byte[] dst = segment.getBytes();
        System.arraycopy(buffer, 0, dst, segment.getDataOffset(), pos);
        pos = 0;
        ++seq;
        notifyAll();
        return segment;
    }
}
