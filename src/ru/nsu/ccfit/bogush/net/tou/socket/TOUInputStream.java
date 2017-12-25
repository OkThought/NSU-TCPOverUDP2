package ru.nsu.ccfit.bogush.net.tou.socket;

import java.io.IOException;
import java.io.InputStream;

public class TOUInputStream extends InputStream {
    int seq;
    private TOUSocketImpl impl;

    public TOUInputStream(TOUSocketImpl impl) {
        this.impl = impl;
    }

    @Override
    public int read()
            throws IOException {
        return 0;
    }
}
