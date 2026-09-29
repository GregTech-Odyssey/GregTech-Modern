package com.gregtechceu.gtceu.api.machine.multiblockpro;

public final class PortKey {

    public static final PortKey IN = new PortKey("in");
    public static final PortKey OUT = new PortKey("out");

    private final String name;

    private PortKey(String name) {
        this.name = name;
    }

    public static PortKey of(String name) {
        return new PortKey(name);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
