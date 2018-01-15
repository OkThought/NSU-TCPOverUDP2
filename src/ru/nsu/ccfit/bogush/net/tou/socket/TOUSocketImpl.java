package ru.nsu.ccfit.bogush.net.tou.socket;

import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegment;
import ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType;
import ru.nsu.ccfit.bogush.net.tou.segment.TOUSegment;
import ru.nsu.ccfit.bogush.net.tou.segment.TOUSegmentFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Predicate;

import static ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType.*;

public class TOUSocketImpl extends SocketImpl {
    private static final int SEGMENT_QUEUE_CAPACITY = 32;
    private static final int ACK_QUEUE_CAPACITY = 64;
    private static final long DEFAULT_SEGMENT_TIMEOUT = 1000; // milliseconds
    private static final HashMap<TCPSegmentType, Long> SEGMENT_TIMEOUT_MAP = new HashMap<>();
    static {
        SEGMENT_TIMEOUT_MAP.put(SYN, Long.MAX_VALUE);
    }

    private TOUSegmentFactory segmentFactory;
    private TOUSharedCommunicator communicator;
    private InetSocketAddress local;
    private InetSocketAddress remote;
    private final HashMap<TCPSegmentType, ArrayBlockingQueue<TOUSegment>> segmentQueueMap = new HashMap<>();
    private final ArrayBlockingQueue<Integer> ackQueue = new ArrayBlockingQueue<Integer>(SEGMENT_QUEUE_CAPACITY);
    private int initialReadSEQ = 0;
    private int initialWriteSEQ = 0;

    private boolean isServerSocket = false;
    private boolean bound = false;
    private boolean connected = false;

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
        communicator.datagramSocket.connect(address, port);
        remote = (InetSocketAddress) communicator.datagramSocket.getRemoteSocketAddress();
        segmentFactory = new TOUSegmentFactory(local, remote);
        segmentQueueMap.put(SYNACK, new ArrayBlockingQueue<>(1));
        // three-way handshake: SYN(x,?) -> SYNACK(y,x+1) -> ACK(x+1,y+1)
        TOUSegment syn = segmentFactory.create(SYN);
        try {
            communicator.putInSendingQueue(syn);
            communicator.start();
            int x = syn.getSEQ();
            TOUSegment synack = fetch(SYNACK, s -> s.getACK() == x+1);
            int y = synack.getSEQ();
            communicator.removeReference(syn);
            segmentQueueMap.remove(SYNACK);
            ackQueue.put(y+1);
            initialReadSEQ = y+1;
            initialWriteSEQ = x+1;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        connected = true;
    }

    @Override
    protected void bind(InetAddress host, int port)
            throws IOException {
        bind(new InetSocketAddress(host, port));
    }

    private void bind(InetSocketAddress address)
            throws SocketException {
        communicator = new TOUSharedCommunicator(address, SEGMENT_QUEUE_CAPACITY);
        local = (InetSocketAddress) communicator.datagramSocket.getLocalSocketAddress();
        bound = true;
    }

    @Override
    protected void listen(int backlog)
            throws IOException {
        isServerSocket = true;
        segmentQueueMap.put(SYN, new ArrayBlockingQueue<>(backlog));
        communicator.start();
    }

    @Override
    protected void accept(SocketImpl si)
            throws IOException {
        if (!isServerSocket) throw new IllegalArgumentException("Not server socket");
        TOUSocketImpl impl = (TOUSocketImpl) si;
        // three-way handshake: SYN(x,?) -> SYNACK(y,x+1) -> ACK(x+1,y+1)
        try {
            TOUSegment syn = segmentQueueMap.get(SYN).take();
            int x = syn.getSEQ();
            segmentQueueMap.put(ORDINARY, new ArrayBlockingQueue<>(SEGMENT_QUEUE_CAPACITY));
            segmentQueueMap.put(ACK, new ArrayBlockingQueue<>(SEGMENT_QUEUE_CAPACITY));
            TOUSegment synack = segmentFactory.create(SYNACK, syn);
            int y = synack.getSEQ();
            communicator.putInSendingQueue(synack);
            impl.local = local;
            impl.bound = true;
            impl.remote = syn.getSrc();
            impl.communicator = communicator;
            communicator.registerImpl(impl.remote, impl);
            impl.fetch(ACK, s -> s.getSEQ() == x+1 && s.getACK() == y+1);
            initialReadSEQ = x+1;
            initialWriteSEQ = y+1;
            communicator.removeReference(synack);
            impl.connected = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private TOUInputStream in;

    @Override
    protected InputStream getInputStream()
            throws IOException {
        if (in == null) {
            in = new TOUInputStream(this);
        }
        return in;
    }

    private TOUOutputStream out;

    @Override
    protected OutputStream getOutputStream()
            throws IOException {
        if (out == null) {
            out = new TOUOutputStream(this);
        }
        return out;
    }

    private boolean closePending = false;

    boolean isClosedOrPending() {
        return closePending || communicator == null || communicator.isClosed();
    }

    @Override
    protected void close()
            throws IOException {
        if (isClosedOrPending()) {
            return;
        }

        closePending = true;

        // close

        communicator.socketClosed(this);
    }

    private void activeClose() {
        try {
            TOUSegment fin = segmentFactory.create(FIN);
            communicator.putInSendingQueue(fin);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void passiveClose(TOUSegment fin) {
        // three-way handshake: FIN(x,?) -> FINACK(y,x+1) -> ACK(x+1,y+1)
        try {
            TOUSegment finack = segmentFactory.create(FINACK, fin);
            communicator.putInSendingQueue(finack);
            synchronized (segmentQueueMap) {
                ArrayBlockingQueue<TOUSegment> ackQueue = segmentQueueMap.get(ACK);
                while (!ackQueue.removeIf(s -> s.getACK() == finack.getSEQ() + 1 && s.getSEQ() == finack.getACK())) {
                    segmentQueueMap.wait();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    protected int available()
            throws IOException {
        return 0;
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

    void handle(TOUSegment segment)
            throws InterruptedException {
        if (segment.dataSize() > 0) {
            putInQueue(ORDINARY, segment);
            ackQueue.put(segment.getSEQ());
        }

        if (segment.isACK()) {
            if (segment.isSYN()) {
                putInQueue(SYNACK, segment);
            } else if (segment.isFIN()) {
                putInQueue(FINACK, segment);
            } else {
                communicator.removeByAck(segment);
            }
        } else {
            if (segment.isSYN()) {
                putInQueue(SYN, segment);
            } else if (segment.isFIN()) {
                passiveClose(segment);
            }
        }
    }

    void sweepTimedOutSegmentsFromQueues() {
        for (ArrayBlockingQueue<TOUSegment> queue : segmentQueueMap.values()) {
            queue.removeIf(TOUSegment::timedOut);
        }
    }

    private void putInQueue(TCPSegmentType type, TOUSegment segment)
            throws InterruptedException {
        ArrayBlockingQueue<TOUSegment> queue = segmentQueueMap.get(type);
        if (queue == null) {
            return;
        }
        synchronized (segmentQueueMap) {
            if (queue.contains(segment)) {
                return;
            }
            segment.setTimeout(SEGMENT_TIMEOUT_MAP.getOrDefault(type, (long) 0));
            queue.put(segment);
            segmentQueueMap.notifyAll();
        }
    }

    private TOUSegment fetch(ArrayBlockingQueue<TOUSegment> queue, Predicate<TOUSegment> predicate)
            throws InterruptedException {
        queue.iterator();
        TOUSegment fetched = null;
        while (fetched == null) {
            Iterator<TOUSegment> iterator = queue.iterator();
            while (iterator.hasNext()) {
                TOUSegment next = iterator.next();
                if (predicate.test(next)) {
                    iterator.remove();
                    fetched = next;
                    break;
                }
            }
            synchronized (segmentQueueMap) {
                if (fetched == null) {
                    segmentQueueMap.wait();
                } else {
                    segmentQueueMap.notifyAll();
                }
            }
        }
        return fetched;
    }

    private TOUSegment fetch(TCPSegmentType type, Predicate<TOUSegment> predicate)
            throws InterruptedException {
        ArrayBlockingQueue<TOUSegment> queue = segmentQueueMap.get(type);
        return fetch(queue, predicate);
    }

    byte[] fetchData(int seq)
            throws InterruptedException {
        return fetch(ORDINARY, s -> s.getSEQ() == seq).getData();
    }

    TOUSegment flushIfAvailable() {
        if (out == null) return null;
        int available = out.available();
        if (available <= 0) return null;
        int seq = out.getCurrentSEQ();
        byte[] data = out.flushBytes();
        return new TOUSegment(new TCPSegment(data.length).setData(data), local, remote);
    }

    int getInitialReadSEQ() {
        return initialReadSEQ;
    }

    int getInitialWriteSEQ() {
        return initialWriteSEQ;
    }

    InetSocketAddress getLocalSocketAddress() {
        return local;
    }

    InetSocketAddress getRemoteSocketAddress() {
        return remote;
    }

    boolean isServerSocket() {
        return isServerSocket;
    }
}
