package ru.nsu.ccfit.bogush.net.tou.socket;

import java.io.IOException;
import java.io.InputStream;

public class TOUInputStream extends InputStream {
    private TOUSocketImpl impl;
    private int seq;
    private byte[] data;
    private int pos = 0;

    TOUInputStream(TOUSocketImpl impl) {
        this.impl = impl;
        seq = impl.getInitialReadSEQ();
    }

    @Override
    public synchronized int read()
            throws IOException {
        if (data == null || pos == data.length) {
            try {
                data = impl.fetchData(seq);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return -1;
            }
            pos = 0;
        }
        return data[pos++];
    }
}
