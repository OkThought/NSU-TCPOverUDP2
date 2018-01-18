package ru.nsu.ccfit.bogush.net.tou.socket;

import java.io.IOException;
import java.io.InputStream;

public class TOUInputStream extends InputStream {
    private TOUSocketImpl impl;
    private int seq;
    private byte[] data;
    private int pos = 0;
    private boolean eof = false;

    TOUInputStream(TOUSocketImpl impl) {
        this.impl = impl;
        seq = impl.getInitialReadSEQ();
    }

    private boolean closing = false;
    public void close() throws IOException {
        if (closing)
            return;
        closing = true;
        if (!impl.isClosedOrPending()) {
            impl.close();
        }
        closing = false;
    }

    void setEof(boolean eof) {
        this.eof = eof;
    }

    @Override
    public synchronized int read()
            throws IOException {
        if (data == null || pos == data.length) {
            if (eof) return -1;
            if (impl.isInShut()) throw new IOException("Input shutdown");
            try {
                data = impl.fetchData(seq);
                if (data == null) {
                    eof = true;
                    return -1;
                }
            } catch (InterruptedException e) { return -1; }
            pos = 0;
        }

        return data[pos++];
    }
}
