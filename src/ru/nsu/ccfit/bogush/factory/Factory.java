package ru.nsu.ccfit.bogush.factory;

public interface Factory<T, TT> {
    T create(TT type, Object... args);
}
