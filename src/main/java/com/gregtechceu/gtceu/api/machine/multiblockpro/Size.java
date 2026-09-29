package com.gregtechceu.gtceu.api.machine.multiblockpro;

import java.util.Arrays;

public final class Size {

    private final ParamKey[] keys;
    private final int[] values;

    Size(ParamKey[] keys, int[] values) {
        this.keys = keys;
        this.values = values;
    }

    public int get(ParamKey key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) return values[i];
        }
        throw new IllegalArgumentException("not measured: " + key);
    }

    int size() {
        return keys.length;
    }

    ParamKey key(int index) {
        return keys[index];
    }

    int value(int index) {
        return values[index];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Size other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("Size{");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keys[i]).append('=').append(values[i]);
        }
        return sb.append('}').toString();
    }
}
