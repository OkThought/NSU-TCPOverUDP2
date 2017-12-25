package ru.nsu.ccfit.bogush.net.tcp.segment;

import ru.nsu.ccfit.bogush.factory.Creator;
import ru.nsu.ccfit.bogush.factory.Factory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import static ru.nsu.ccfit.bogush.net.tcp.segment.TCPSegmentType.*;

public class TCPSegmentFactory implements Factory<TCPSegment, TCPSegmentType> {
    public final HashMap<CreatorKey, Creator<TCPSegment>> creators = new HashMap<>();
    {
        creators.put(new CreatorKey(SYN),
                args -> new TCPSegment()
                        .setSEQ(rand())
                        .setSYN(true));

        creators.put(new CreatorKey(FIN),
                args -> new TCPSegment()
                        .setSEQ(rand())
                        .setFIN(true));

        creators.put(new CreatorKey(SYNACK, Integer.class),
                args -> new TCPSegment()
                        .setSEQ(rand())
                        .setACK((Integer) args[0] + 1)
                        .setSYN(true)
                        .setACK(true));

        creators.put(new CreatorKey(SYNACK, TCPSegment.class),
                args -> new TCPSegment((TCPSegment) args[0])
                        .setSEQ(rand())
                        .setACK(((TCPSegment) args[0]).getSEQ() + 1)
                        .setACK(true));

        creators.put(new CreatorKey(FINACK, Integer.class),
                args -> new TCPSegment()
                        .setSEQ(rand())
                        .setACK((Integer) args[0] + 1)
                        .setFIN(true).setACK(true));

        creators.put(new CreatorKey(FINACK, TCPSegment.class),
                args -> new TCPSegment((TCPSegment) args[0])
                        .setSEQ(rand())
                        .setACK(((TCPSegment) args[0]).getSEQ() + 1)
                        .setACK(true));

        creators.put(new CreatorKey(ACK, Integer.class),
                args -> new TCPSegment()
                        .setACK((Integer) args[0])
                        .setACK(true));

        creators.put(new CreatorKey(ACK, Integer.class, Integer.class),
                args -> new TCPSegment()
                        .setACK((Integer) args[0] + 1)
                        .setSEQ((Integer) args[1])
                        .setACK(true));

        creators.put(new CreatorKey(ACK, TCPSegment.class),
                args -> new TCPSegment((TCPSegment) args[0])
                        .setSYN(false)
                        .setFIN(false)
                        .setSEQ(((TCPSegment) args[0]).getACK())
                        .setACK(((TCPSegment) args[0]).getSEQ() + 1));

        creators.put(new CreatorKey(ORDINARY, byte[].class, Integer.class),
                args -> new TCPSegment((byte[]) args[0]).setSEQ((int) args[1]));
    }

    private static int rand() {
        return ThreadLocalRandom.current().nextInt();
    }

    @Override
    public TCPSegment create(TCPSegmentType type, Object... args) {
        Class[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args.getClass();
        }

        CreatorKey key = new CreatorKey(type, argTypes);
        Creator<TCPSegment> creator = creators.get(key);
        if (creator == null) throw new IllegalArgumentException("Not Implemented creator for key: " + key + " and " +
                "argument types: " + Arrays.toString(argTypes));
        return creator.create(args);
    }
}
