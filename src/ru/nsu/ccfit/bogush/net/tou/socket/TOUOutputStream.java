package ru.nsu.ccfit.bogush.net.tou.socket;

import java.io.IOException;
import java.io.OutputStream;

public class TOUOutputStream extends OutputStream {
    private TOUSocketImpl impl;

    public TOUOutputStream(TOUSocketImpl impl) {
        this.impl = impl;
    }

    @Override
    public void write(int b)
            throws IOException {

    }
}
