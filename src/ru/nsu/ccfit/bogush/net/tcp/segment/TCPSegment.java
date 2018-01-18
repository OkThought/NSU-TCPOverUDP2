package ru.nsu.ccfit.bogush.net.tcp.segment;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * TCPSegment structure:
 * <pre><code>
 * +---------+-----+-----------------------+-----------------------+-----------------------+-----------------------+
 * | offsets |octet|           0           |           1           |           2           |           3           |
 * +---------+-----+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |  octet  |byte | 0| 1| 2| 3| 4| 5| 6| 7| 8| 9|10|11|12|13|14|15|16|17|18|19|20|21|22|23|24|25|26|27|28|29|30|31|
 * +---------+-----+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |    0    |  0  |                                        sequence number                                        |
 * +---------+-----+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |    4    |  32 |                                           ack number                                          |
 * +---------+-----+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |    8    |  64 |                                           data size                                           |
 * +---------+-----+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |         |     |                       | A| S| F|              |                                               |
 * |    12   |  96 |       data offset     | C| Y| I|    zeros     |                      data                     |
 * |         |     |                       | K| N| N|              |        (starting at data offset octet)        |
 * +---------+-----+-----------------------+--+--+--+--------------+-----------------------------------------------+
 * |   ...   | ... |                                              ...                                              |
 * +---------+-----+-----------------------------------------------------------------------------------------------+
 * </code></pre>
 */
public class TCPSegment {
    private static int off = 0;
    private static int len = 0;
    private static final int SEQ                = off = off + len; static {len = 4;}
    private static final int ACK                = off = off + len; static {len = 4;}
    private static final int DATA_SIZE          = off = off + len; static {len = 4;}
    private static final int DATA_OFFSET        = off = off + len; static {len = 1;}
    private static final int FLAGS              = off = off + len; static {len = 1;}

    private static final int DATA_OFFSET_MIN    = off;
    public static final int HEADER_SIZE         = DATA_OFFSET_MIN + 1;
    public static final byte ACK_BITMAP         = (byte) 0b10000000;
    public static final byte SYN_BITMAP         = (byte) 0b01000000;
    public static final byte FIN_BITMAP         = (byte) 0b00100000;

    private ByteBuffer buffer;
    private byte[] bytes;

    public TCPSegment() {
        this(0);
    }

    public TCPSegment(int capacity) {
        this(new byte[capacity + HEADER_SIZE]);
        Arrays.fill(bytes, (byte) 0);
        setDataOffset(DATA_OFFSET_MIN);
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

    public int getDataOffset() {
        return buffer.get(DATA_OFFSET) & 0xff;
    }

    public TCPSegment setDataOffset(int dataOffset) {
        buffer.put(DATA_OFFSET, (byte) dataOffset);
        return this;
    }

    public byte[] getData() {
        int offset = getDataOffset();
        return Arrays.copyOfRange(bytes, offset, offset + dataSize());
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
        return bytes.length - getDataOffset();
    }

    public int dataSize() {
        return buffer.getChar(DATA_SIZE);
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
