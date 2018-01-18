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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType.*;

/**
 * <p>
 *     A TCP over UDP socket implementation using only one {@link DatagramSocket} (aka UDP socket)
 *     and fixed amount of internal threads. The instances of this class have a shared communicator
 *     that encapsulates sending and receiving of UDP packets. It with underlying UDP socket as
 *     its field is shared between implementations of one group.
 * </p>
 *
 * <p>
 *     Two implementations are considered to be in one group if
 *     <ul>
 *      <li>one of them is a server socket implementation and the other one is accepted by the first</li>
 *      <li>or they are both accepted by the same server socket implementation</li>
 *     </ul>
 * </p>
 *
 * <p>
 *     A shared communicator keeps a reference to every impl in group. When socket is closed it
 *     notifies the communicator using {@link TOUSharedCommunicator#socketClosed}. When notified
 *     communicator removes the reference of the closed impl from its container and closes the UDP socket
 *     if the group has become empty.
 * </p>
 */
public class TOUSocketImpl extends SocketImpl {
    private static final int SEGMENT_QUEUE_CAPACITY = 32;
    private static final long DEFAULT_SEGMENT_TIMEOUT = 1000; // milliseconds
    private static final HashMap<TCPSegmentType, Long> SEGMENT_TIMEOUT_MAP = new HashMap<>();
    private static final int NUM_CORE_THREADS = 4;
    private static final int RESENDING_PERIOD = 10; // milliseconds

    static {
        SEGMENT_TIMEOUT_MAP.put(SYN, Long.MAX_VALUE);
    }

    private TOUSegmentFactory segmentFactory;
    TOUSharedCommunicator communicator;
    private InetSocketAddress local;
    private InetSocketAddress remote;
    private final HashMap<TCPSegmentType, BlockingQueue<TOUSegment>> receivedSegmentsQueueMap = new HashMap<>();
    private final ArrayBlockingQueue<Integer> ackQueue = new ArrayBlockingQueue<Integer>(SEGMENT_QUEUE_CAPACITY);
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> sendDataFutureTasks = new ConcurrentHashMap<>();
    private int initialReadSEQ = 0;
    private int initialWriteSEQ = 0;

    private boolean isServerSocket = false;
    private boolean bound = false;
    private boolean connected = false;
    private volatile boolean shutIn = false;
    private boolean shutOut = false;

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
        receivedSegmentsQueueMap.put(SYNACK, new ArrayBlockingQueue<>(1));
        // three-way handshake: SYN(x,?) -> SYNACK(y,x+1) -> ACK(x+1,y+1)
        TOUSegment syn = segmentFactory.create(SYN);
        try {
            ScheduledFuture<?> future = sendRepeatedly(syn, DEFAULT_SEGMENT_TIMEOUT);
            communicator.start();
            int x = syn.getSEQ();
            TOUSegment synack = fetch(SYNACK, s -> s.getACK() == x+1);
            int y = synack.getSEQ();
            future.cancel(true);
            receivedSegmentsQueueMap.remove(SYNACK);
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
        communicator = new TOUSharedCommunicator(address, NUM_CORE_THREADS);
//        if (communicator.localSocketAddress.getAddress().isAnyLocalAddress()) {
//            try {
//                communicator.localSocketAddress = new InetSocketAddress(InetAddress.getLocalHost(),
//                        communicator.datagramSocket.getLocalPort());
//            } catch (UnknownHostException e) {
//                e.printStackTrace();
//                System.exit(-1);
//            }
//        }
        local = communicator.localSocketAddress;
        bound = true;
    }

    @Override
    protected void listen(int backlog)
            throws IOException {
        isServerSocket = true;
        receivedSegmentsQueueMap.put(SYN, new ArrayBlockingQueue<>(backlog));
        communicator.registerImpl(local, this);
        communicator.start();
    }

    @Override
    protected void accept(SocketImpl si)
            throws IOException {
        if (!isServerSocket) throw new IllegalArgumentException("Not server socket");
        TOUSocketImpl impl = (TOUSocketImpl) si;
        // three-way handshake: SYN(x,?) -> SYNACK(y,x+1) -> ACK(x+1,y+1)
        try {
            TOUSegment syn = receivedSegmentsQueueMap.get(SYN).take();
            int x = syn.getSEQ();
            receivedSegmentsQueueMap.put(ORDINARY, new ArrayBlockingQueue<>(SEGMENT_QUEUE_CAPACITY));
            receivedSegmentsQueueMap.put(ACK, new ArrayBlockingQueue<>(SEGMENT_QUEUE_CAPACITY));
            TOUSegment synack = segmentFactory.create(SYNACK, syn);
            int y = synack.getSEQ();
            ScheduledFuture<?> future = sendRepeatedly(synack, DEFAULT_SEGMENT_TIMEOUT);
            impl.local = local;
            impl.bound = true;
            impl.remote = syn.getSrc();
            impl.communicator = communicator;
            communicator.registerImpl(impl.remote, impl);
            impl.fetch(ACK, s -> s.getSEQ() == x+1 && s.getACK() == y+1);
            initialReadSEQ = x+1;
            initialWriteSEQ = y+1;
            future.cancel(true);
            impl.connected = true;
        } catch (InterruptedException ignored) {}
    }

    private TOUInputStream in;

    @Override
    protected InputStream getInputStream()
            throws IOException {
        if (!connected) throw new IOException("Socket not connected");
        if (in == null) {
            in = new TOUInputStream(this);
        }
        return in;
    }

    @Override
    protected void shutdownInput()
            throws IOException {
        if (shutIn) return;
        shutIn = true;
        if (in != null) {
            in.setEof(true);
            // wake up threads waiting on read
            synchronized (receivedSegmentsQueueMap) {
                receivedSegmentsQueueMap.notifyAll();
            }
        }
    }

    boolean isInShut() {
        return shutIn;
    }

    private TOUOutputStream out;

    @Override
    protected OutputStream getOutputStream()
            throws IOException {
        if (!connected) throw new IOException("Socket not connected");
        if (isClosedOrPending()) throw new IOException("Socket closed");
        if (out == null) {
            out = new TOUOutputStream(this);
        }
        return out;
    }

    @Override
    protected void shutdownOutput()
            throws IOException {
        if (shutOut) return;
        shutOut = true;

        if (!closingPassively) {
            activeClose();
        }
    }

    boolean isOutShut() {
        return shutOut;
    }

    private boolean isClosed() {
        return communicator == null || communicator.isClosed();
    }

    private final AtomicBoolean closePending = new AtomicBoolean(false);

    boolean isClosedOrPending() {
        return closePending.get() || isClosed();
    }

    @Override
    protected void close()
            throws IOException {
        if (closePending.getAndSet(true) || isClosed()) return;
        // wait until all segments are sent
        // and perform three-way handshake FIN->FINACK->ACK
        shutdownOutput();
        shutdownInput();
    }

    private void activeClose()
            throws IOException {
        // three-way handshake: FIN(x,?) -> FINACK(y,x+1) -> ACK(x+1,y+1)
        try {
            TOUSegment fin = segmentFactory.create(FIN);
            ScheduledFuture<?> future = sendRepeatedly(fin, DEFAULT_SEGMENT_TIMEOUT);
            int x = fin.getSEQ();
            TOUSegment finack = fetch(FINACK, s -> s.getACK() == x+1);
            future.cancel(true);
            TOUSegment ack = segmentFactory.create(ACK, finack);
            communicator.sendOnce(ack);
            Thread.sleep(DEFAULT_SEGMENT_TIMEOUT);
            finishClose();
        } catch (InterruptedException ignored) {}
    }

    private boolean closingPassively = false;
    private void passiveClose(TOUSegment fin)
            throws IOException {
        boolean wasClosePending = closePending.getAndSet(true);
        closePending.set(true);

        // second step in three-way handshake: FIN(x,?) -> FIN-ACK(y,x+1) -> ACK(x+1,y+1)
        TOUSegment finack = segmentFactory.create(FINACK, fin);

        communicator.sendOnce(finack);

        if (wasClosePending) return;

        closingPassively = true;

        shutdownOutput();

        // wait until all data segments are read from queue
        waitUntilReceivedSegmentsQueueIsEmpty(ORDINARY);

        shutdownInput();
        receivedSegmentsQueueMap.remove(ORDINARY);
    }

    private void finishClose() {
        bound = false;
        connected = false;
        // close finished
        // notify shared communicator about it so it could terminate
        communicator.socketClosed(this);
    }

    private void waitUntilReceivedSegmentsQueueIsEmpty(TCPSegmentType type) {
        BlockingQueue<TOUSegment> queue = receivedSegmentsQueueMap.get(type);
        synchronized (receivedSegmentsQueueMap) {
            while (!queue.isEmpty()) {
                try {
                    receivedSegmentsQueueMap.wait();
                } catch (InterruptedException ignored) {}
            }
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
            throws InterruptedException, IOException {
        if (segment.dataSize() > 0) {
            if (!shutIn) {
                putInQueue(ORDINARY, segment);
                ackQueue.put(segment.getSEQ());
            } else {
                communicator.sendOnce(segmentFactory.create(ACK, segment.getSEQ()));
            }
        }

        if (segment.isACK()) {
            if (segment.isSYN()) {
                putInQueue(SYNACK, segment);
            } else if (segment.isFIN()) {
                putInQueue(FINACK, segment);
            } else {
                if (closingPassively && shutIn) {
                    finishClose();
                } else {
                    ScheduledFuture<?> future = sendDataFutureTasks.remove(segment.getACK());
                    if (future != null) {
                        future.cancel(true);
                    } else {
                        putInQueue(ACK, segment);
                    }
                }
            }
        } else {
            if (segment.isSYN()) {
                putInQueue(SYN, segment);
            } else if (segment.isFIN()) {
                passiveClose(segment);
            } // else it's just a data segment that's already been handled before.
        }
    }

    private ScheduledFuture<?> sendRepeatedly(TOUSegment s, long timeout)
            throws InterruptedException {
        return communicator.sendRepeatedly(s.setTimeout(timeout), RESENDING_PERIOD, MILLISECONDS);
    }

    void sweepTimedOutSegmentsFromQueues() {
        for (BlockingQueue<TOUSegment> queue : receivedSegmentsQueueMap.values()) {
            queue.removeIf(TOUSegment::timedOut);
        }
    }

    private void putInQueue(TCPSegmentType type, TOUSegment segment)
            throws InterruptedException {
        BlockingQueue<TOUSegment> queue = receivedSegmentsQueueMap.get(type);
        if (queue == null) {
            return;
        }
        synchronized (receivedSegmentsQueueMap) {
            if (queue.contains(segment)) {
                return;
            }
            segment.setTimeout(SEGMENT_TIMEOUT_MAP.getOrDefault(type, (long) 0));
            queue.put(segment);
            receivedSegmentsQueueMap.notifyAll();
        }
    }

    private TOUSegment tryFetch(BlockingQueue<TOUSegment> queue, Predicate<TOUSegment> predicate) {
        Iterator<TOUSegment> iterator = queue.iterator();
        while (iterator.hasNext()) {
            TOUSegment segment = iterator.next();
            if (predicate.test(segment)) {
                iterator.remove();
                return segment;
            }
        }
        return null;
    }


    private TOUSegment tryFetch(TCPSegmentType type, Predicate<TOUSegment> predicate) {
        return tryFetch(receivedSegmentsQueueMap.get(type), predicate);
    }

    /**
     * Blocks until there is a segment in the {@code queue} that satisfies the {@code predicate}.
     * Different threads may block performing a fetch:<br>
     * <li>User thread
     *  <ul>
     *      <li>waiting for SYN-ACK segment in connect()</li>
     *      <li>waiting for FIN-ACK segment in close()</li>
     *      <li>requesting subsequent data in read()</li>
     *  </ul>
     * </li>
     *
     * <li>A TOU internal thread
     *  <ul>
     *      <li>handling a received FIN segment</li>
     *  </ul>
     * </li>
     * @return the first segment in the queue that satisfies the {@code predicate} or {@code null} if ...
     *     The order is specified in {@link ArrayBlockingQueue#iterator}
     */
    private TOUSegment fetch(BlockingQueue<TOUSegment> queue, Predicate<TOUSegment> predicate)
            throws InterruptedException {
        queue.iterator();
        TOUSegment fetched = null;
        while (fetched == null) {
            fetched = tryFetch(queue, predicate);
            synchronized (receivedSegmentsQueueMap) {
                if (fetched == null) {
                    receivedSegmentsQueueMap.wait();
                } else {
                    receivedSegmentsQueueMap.notifyAll();
                }
            }
        }
        return fetched;
    }

    private TOUSegment fetch(TCPSegmentType type, Predicate<TOUSegment> predicate)
            throws InterruptedException {
        BlockingQueue<TOUSegment> queue = receivedSegmentsQueueMap.get(type);
        return fetch(queue, predicate);
    }

    byte[] fetchData(int seq)
            throws InterruptedException, IOException {
        if (isClosedOrPending()) throw new IOException("Socket closed");
        TOUSegment fetched;

        while (true) {
            fetched = tryFetch(ORDINARY, s -> s.getSEQ() == seq);

            if (fetched != null) {
                break;
            }

            if (shutIn) {
                return null;
            }

            synchronized (receivedSegmentsQueueMap) {
                receivedSegmentsQueueMap.wait();
            }
        }

        synchronized (receivedSegmentsQueueMap) {
            receivedSegmentsQueueMap.notifyAll();
        }

        return fetched.getData();

    }

    void flushAndSendIfAvailable()
            throws InterruptedException {
        if (out == null) return;
        int available = out.available();
        if (available <= 0) return;
        TCPSegment tcpSegment = out.flushIntoSegment();
        TOUSegment dataSegment = new TOUSegment(tcpSegment, local, remote);
        Integer ack = ackQueue.poll();
        if (ack != null) {
            dataSegment.setACK(true).setACK(ack);
        }
        sendDataFutureTasks.put(dataSegment.getSEQ(), sendRepeatedly(dataSegment, DEFAULT_SEGMENT_TIMEOUT));
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
