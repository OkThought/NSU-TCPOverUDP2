package ru.nsu.ccfit.bogush.net.tou.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.util.WeakHashMap;

public class TOUSocketImpl extends SocketImpl {
    private DatagramSocket datagramSocket;
    private WeakHashMap<TOUSocketImpl, DatagramSocket> weakHashMap;
    InetSocketAddress local;
    InetSocketAddress remote;

    @Override
    protected void create(boolean stream)
            throws IOException {
    }

    @Override
    protected void connect(String host, int port)
            throws IOException {
        connect(new InetSocketAddress(host, port), 0);
    }

    @Override
    protected void connect(SocketAddress address, int timeout)
            throws IOException {
        InetSocketAddress iAddr = (InetSocketAddress) address;
        connect(iAddr.getAddress(), iAddr.getPort());
    }

    @Override
    protected void connect(InetAddress address, int port)
            throws IOException {
        bind(new InetSocketAddress(0));
        datagramSocket.connect(address, port);
        remote = (InetSocketAddress) datagramSocket.getRemoteSocketAddress();
    }

    @Override
    protected void bind(InetAddress host, int port)
            throws IOException {
        bind(new InetSocketAddress(host, port));
    }

    private void bind(InetSocketAddress address)
            throws SocketException {
        weakHashMap = new WeakHashMap<>();
        datagramSocket = new DatagramSocket(address);
        local = (InetSocketAddress) datagramSocket.getLocalSocketAddress();
        weakHashMap.put(this, datagramSocket);
    }

    @Override
    protected void listen(int backlog)
            throws IOException {

    }

    @Override
    protected void accept(SocketImpl s)
            throws IOException {

    }

    TOUInputStream in;

    @Override
    protected InputStream getInputStream()
            throws IOException {
        if (in == null) {
            in = new TOUInputStream(this);
        }
        return in;
    }

    TOUOutputStream out;

    @Override
    protected OutputStream getOutputStream()
            throws IOException {
        if (out == null) {
            out = new TOUOutputStream(this);
        }
        return out;
    }

    @Override
    protected int available()
            throws IOException {
        return 0;
    }

    @Override
    protected void close()
            throws IOException {
        if (weakHashMap.size() == 1 && datagramSocket != null) {
            datagramSocket.close();
        }
    }

    @Override
    protected void sendUrgentData(int data)
            throws IOException {

    }

    @Override
    public void setOption(int optID, Object value)
            throws SocketException {

    }

    @Override
    public Object getOption(int optID)
            throws SocketException {
        return null;
    }
}
