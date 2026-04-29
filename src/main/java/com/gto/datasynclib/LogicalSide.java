package com.gto.datasynclib;

public enum LogicalSide {

    CLIENT,
    SERVER;

    public final boolean isClient() {
        return this == CLIENT;
    }

    public final boolean isServer() {
        return this == SERVER;
    }
}
