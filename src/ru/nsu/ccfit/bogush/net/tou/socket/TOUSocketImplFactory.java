package ru.nsu.ccfit.bogush.net.tou.socket;

import java.net.SocketImpl;
import java.net.SocketImplFactory;

public class TOUSocketImplFactory implements SocketImplFactory {
    @Override
    public SocketImpl createSocketImpl() {
        return new TOUSocketImpl();
    }
}
