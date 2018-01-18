package ru.nsu.ccfit.bogush.net.tou.socket;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType;
import ru.nsu.ccfit.bogush.net.tou.segment.TOUSegment;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.*;

import static ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType.*;

class TOUSharedCommunicator {
    /*
     * The UDP length field size sets a theoretical limit of 65,535 bytes (8 byte header + 65,527 bytes of data)
     * for a UDP datagram. However the actual limit for the data length, which is imposed by the underlying IPv4
     * protocol, is 65,507 bytes (65,535 − 8 byte UDP header − 20 byte IP header). (Wikipedia)
     */
    private static final int UDP_PACKET_DATA_SIZE = 65507;
    private static final int SWEEPING_PERIOD = 100; // milliseconds

    final DatagramSocket datagramSocket;
    InetSocketAddress localSocketAddress;
    private final HashMap<InetSocketAddress, TOUSocketImpl> implMap = new HashMap<>();
    private final SegmentReceiver segmentReceiver = new SegmentReceiver();
    final LinkedBlockingQueue<TOUSocketImpl> implsWithData;
    private final ScheduledThreadPoolExecutor threadPoolExecutor;
    private boolean shouldStop = false;

    TOUSharedCommunicator(InetSocketAddress address, int corePoolSize)
            throws SocketException {
        datagramSocket = new DatagramSocket(address);
        localSocketAddress = address;
        threadPoolExecutor = new ScheduledThreadPoolExecutor(corePoolSize);
        implsWithData = new LinkedBlockingQueue<>();
    }

    private class SegmentReceiver extends Thread {
        private SegmentReceiver() {
            super("TOUSegmentReceiver");
        }

        @Override
        public void run() {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[UDP_PACKET_DATA_SIZE], UDP_PACKET_DATA_SIZE);
                while (!shouldStop) {
                    try {
                        datagramSocket.receive(packet);
                    } catch (SocketTimeoutException e) {
                        System.out.println("receiver timed out");
                        continue;
                    }
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    InetSocketAddress socketAddress = new InetSocketAddress(address, port);
                    TOUSegment segment = new TOUSegment(new TCPSegment(packet.getData()), socketAddress, localSocketAddress);
                    TCPSegmentType type = TCPSegmentType.typeOf(segment);
                    TOUSocketImpl impl;

                    if (type == SYN) {
                        // SYN segment addressed to server socket associated with local address
                        impl = implMap.get(localSocketAddress);
                    } else {
                        // other segments addressed to socket associated with remote address
                        impl = implMap.get(socketAddress);
                    }

                    if (impl == null) {
                        continue;
                    }

                    impl.handle(segment);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    void start() {
        segmentReceiver.start();
        startSweeper();
        startFlusher();
    }

    private void send(TOUSegment segment)
            throws IOException {
        datagramSocket.send(new DatagramPacket(segment.getBytes(), segment.size()));
    }

    private static final RuntimeException SEGMENT_TIMED_OUT = new RuntimeException("segment timed out") {
        @Override
        public synchronized Throwable fillInStackTrace() {
            super.fillInStackTrace();
            return this;
        }
    };

    ScheduledFuture<?> sendRepeatedly(TOUSegment segment, long sendingPeriod, TimeUnit timeUnit)
            throws InterruptedException {
        return threadPoolExecutor.scheduleAtFixedRate(() -> {
            try {
                send(segment);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
            if (segment.timedOut()) {
                throw SEGMENT_TIMED_OUT;
            }
        }, 0, sendingPeriod, timeUnit);
    }

    void sendOnce(TOUSegment segment) {
        threadPoolExecutor.execute(() -> {
            try {
                send(segment);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * sweeper is a task that, when periodically executed, removes timed out
     * segments from queues of each impl in {@code implMap}.
     */
    private void startSweeper() {
        threadPoolExecutor.scheduleAtFixedRate(() -> {
            for (TOUSocketImpl impl : implMap.values()) {
                impl.sweepTimedOutSegmentsFromQueues();
            }
        }, SWEEPING_PERIOD, SWEEPING_PERIOD, TimeUnit.MILLISECONDS);
    }

    private void startFlusher() {
        threadPoolExecutor.execute(() -> {
            try {
                while (!Thread.interrupted()) {
                    implsWithData.take().flushAndSendIfAvailable();
                }
            } catch (InterruptedException ignored) {}
        });
    }

    boolean isClosed() {
        return datagramSocket.isClosed();
    }

    void socketClosed(TOUSocketImpl impl) {
        InetSocketAddress associatedAddress = impl.isServerSocket() ?
                impl.getLocalSocketAddress() :
                impl.getRemoteSocketAddress();
        synchronized (implMap) {
            implMap.remove(associatedAddress);
        }
        if (implMapIsEmpty()) {
            stop();
        }
    }

    private boolean implMapIsEmpty() {
        boolean implMapEmpty;
        synchronized (implMap) {
            implMapEmpty = implMap.isEmpty();
        }
        return implMapEmpty;
    }

    private void stop() {
        threadPoolExecutor.shutdown();
        implsWithData.clear();
        datagramSocket.close();
        shouldStop = true;
    }

    public void registerImpl(InetSocketAddress associatedAddress, TOUSocketImpl impl) {
        implMap.put(associatedAddress, impl);
    }
}
