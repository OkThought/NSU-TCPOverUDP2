package ru.nsu.ccfit.bogush.factory;

public interface Creator<T> {
    T create(Object... args);
}
