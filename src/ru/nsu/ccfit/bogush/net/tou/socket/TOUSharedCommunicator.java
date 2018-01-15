package ru.nsu.ccfit.bogush.net.tou.socket;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType;
import ru.nsu.ccfit.bogush.net.tou.segment.TOUSegment;
import ru.nsu.ccfit.bogush.util.concurrent.BlockingCircularDoublyLinkedList;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Predicate;

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
    private final InetSocketAddress localSocketAddress;
    private final HashMap<InetSocketAddress, TOUSocketImpl> implMap = new HashMap<>();
    private final Thread segmentSender = new Thread(this::segmentSenderRun,"SegmentSender");
    private final Thread segmentReceiver = new Thread(this::segmentReceiverRun, "SegmentReceiver");
    private final BlockingCircularDoublyLinkedList<TOUSegment> segmentQueue;
    private volatile boolean shouldStop = false;

    TOUSharedCommunicator(InetSocketAddress address, int queueCapacity)
            throws SocketException {
        datagramSocket = new DatagramSocket(address);
        localSocketAddress = address;
        segmentQueue = new BlockingCircularDoublyLinkedList<>(queueCapacity);
    }

    void start() {
        segmentSender.start();
        segmentReceiver.start();
    }

    private void segmentSenderRun() {
        try {
            DatagramPacket packet = new DatagramPacket(new byte[UDP_PACKET_DATA_SIZE], UDP_PACKET_DATA_SIZE);
            long sweepingTime = 0;
            while (!shouldStop) {
                TOUSegment segment;
                segment = segmentQueue.next(s -> !s.timedOut());
                packet.setData(segment.getBytes());
                datagramSocket.send(packet);

                long t;
                if (sweepingTime <= (t = System.currentTimeMillis())) {
                    sweepingTime = t + SWEEPING_PERIOD;
                    for (TOUSocketImpl impl : implMap.values()) {
                        impl.sweepTimedOutSegmentsFromQueues();
                    }
                }
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private void segmentReceiverRun() {
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

    void putInSendingQueue(TOUSegment segment)
            throws InterruptedException {
        segmentQueue.putPrev(segment);
    }

    private boolean implMapIsEmpty() {
        boolean implMapEmpty;
        synchronized (implMap) {
            implMapEmpty = implMap.isEmpty();
        }
        return implMapEmpty;
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
        closeDatagramSocket();
    }

    private void closeDatagramSocket() {
        if (implMapIsEmpty()) {
            datagramSocket.close();
            shouldStop = true;
        }
    }

    boolean removeReference(TOUSegment segment) {
        return removeIf(s -> s == segment);
    }

    boolean removeIf(Predicate<TOUSegment> p) {
        return segmentQueue.removeIf(p);
    }

    boolean removeByAck(TOUSegment ack) {
        return removeIf(s ->
                Objects.equals(s.getSrc(), ack.getDst()) &&
                Objects.equals(s.getDst(), ack.getSrc()) &&
                s.getSEQ() == ack.getACK());
    }

    public void registerImpl(InetSocketAddress associatedAddress, TOUSocketImpl impl) {
        implMap.put(associatedAddress, impl);
    }
}
