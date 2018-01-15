package ru.nsu.ccfit.bogush.net.tou.socket;

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

        while (pos == buffer.length) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        buffer[pos] = (byte) b;
        ++pos;
    }

    synchronized int available() {
        return pos;
    }

    synchronized int getCurrentSEQ() {
        return seq;
    }

    synchronized byte[] flushBytes() {
        byte[] result = new byte[pos];
        System.arraycopy(buffer, 0, result, 0, pos);
        pos = 0;
        ++seq;
        notifyAll();
        return result;
    }
}
