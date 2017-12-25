package ru.nsu.ccfit.bogush.net.tcp.segment;

import java.util.Arrays;

public class CreatorKey {
    private TCPSegmentType type;
    private Class[] argTypes;

    public CreatorKey(TCPSegmentType type, Class... argTypes) {
        this.type = type;
        this.argTypes = argTypes;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CreatorKey that = (CreatorKey) o;

        if (type != that.type) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(argTypes, that.argTypes);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Arrays.hashCode(argTypes);
        return result;
    }
}