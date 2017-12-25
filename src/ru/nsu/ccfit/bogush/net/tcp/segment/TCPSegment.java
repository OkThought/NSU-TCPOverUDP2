package ru.nsu.ccfit.bogush.net.tcp.segment;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * TCPSegment structure:
 * <pre><code>
 *      +--------------------------------------------+
 *      |0|1|2|...7|8  ...  31|32       ...        63|
 *      |   sequence number   |      ack number      |
 *      |S|A|F|0...|data ...                         |
 *      +--------------------------------------------+
 * </code></pre>
 */
public class TCPSegment {
    private static final int SEQ = 0;
    private static final int ACK = SEQ + 4;
    private static final int FLAGS = ACK + 4;

    public static final int HEADER_SIZE = FLAGS + 1;
    public static final byte ACK_BITMAP = (byte) 0b10000000;
    public static final byte SYN_BITMAP = (byte) 0b01000000;
    public static final byte FIN_BITMAP = (byte) 0b00100000;

    private ByteBuffer buffer;
    private byte[] bytes;

    public TCPSegment() {
        this(0);
    }

    public TCPSegment(int capacity) {
        this(new byte[capacity + HEADER_SIZE]);
        Arrays.fill(bytes, (byte) 0);
    }

    public TCPSegment(TCPSegment other) {
        this(other.bytes.clone());
    }

    public TCPSegment(byte[] bytes) {
        if (bytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Byte array too small: " + bytes.length + " < " + HEADER_SIZE);
        }
        this.bytes = bytes;
        this.buffer = ByteBuffer.wrap(this.bytes);
    }

    public TCPSegment(byte[] bytes, int offset, int length) {
        this.bytes = new byte[length];
        System.arraycopy(bytes, offset, this.bytes, 0, length);
        this.buffer = ByteBuffer.wrap(this.bytes);
    }

    public boolean isACK () {
        return getFlag(ACK_BITMAP);
    }

    public TCPSegment setACK (boolean value) {
        setFlag(ACK_BITMAP, value);
        return this;
    }

    public boolean isSYN () {
        return getFlag(SYN_BITMAP);
    }

    public TCPSegment setSYN (boolean value) {
        setFlag(SYN_BITMAP, value);
        return this;
    }

    public boolean isFIN () {
        return getFlag(FIN_BITMAP);
    }

    public TCPSegment setFIN (boolean value) {
        setFlag(FIN_BITMAP, value);
        return this;
    }

    public byte getFlags() {
        return bytes[FLAGS];
    }

    public TCPSegment setFlags(byte flags) {
        bytes[FLAGS] = flags;
        return this;
    }

    public byte[] getHeader() {
        return Arrays.copyOfRange(bytes, 0, HEADER_SIZE);
    }

    public TCPSegment setHeader(byte[] header) {
        System.arraycopy(header, 0, bytes, 0, header.length);
        return this;
    }

    public byte[] getData() {
        return Arrays.copyOfRange(bytes, HEADER_SIZE, bytes.length);
    }

    public TCPSegment setData(byte[] data) {
        System.arraycopy(data, 0, this.bytes, HEADER_SIZE, data.length);
        return this;
    }

    public int getSEQ() {
        return buffer.getInt(SEQ);
    }

    public TCPSegment setSEQ(int sequenceNumber) {
        buffer.putInt(SEQ, sequenceNumber);
        return this;
    }

    public int getACK() {
        return buffer.getInt(ACK);
    }

    public TCPSegment setACK(int ackNumber) {
        buffer.putInt(ACK, ackNumber);
        return this;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int capacity() {
        return bytes.length;
    }

    public int dataSize() {
        return bytes.length - HEADER_SIZE;
    }

    public String flagsToString() {
        return String.valueOf(isSYN() ? 'S' : '-') + (isACK() ? 'A' : '-') + (isFIN() ? 'F' : '-');
    }

    @Override
    public String toString() {
        return String.format("%16s[%s seq: %5d ack: %5d data offset: %3d capacity: %3d]",
                TCPSegment.class.getSimpleName(), flagsToString(), getSEQ(), getACK(), HEADER_SIZE, capacity());
    }

    private static byte setFlagActive (byte b, byte flag) {
        return (byte) (b | flag);
    }

    private static byte setFlagInactive (byte b, byte flag) {
        return (byte) (b & ~flag);
    }

    private static boolean getFlag (byte b, byte flag) {
        return (b & flag) != 0;
    }

    private void setFlag (byte flag, boolean active) {
        if (active) {
            bytes[FLAGS] = setFlagActive(bytes[FLAGS], flag);
        } else {
            bytes[FLAGS] = setFlagInactive(bytes[FLAGS], flag);
        }
    }

    private boolean getFlag (byte flag) {
        return getFlag(bytes[FLAGS], flag);
    }
}
