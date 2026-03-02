package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.jetbrains.annotations.NotNull;

import java.util.function.ObjLongConsumer;

public class TickContentMap {

    public static final TickContentMap EMPTY = new TickContentMap() {

        @Override
        public boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, boolean simulated) {
            return true;
        }

        @Override
        public void forEach(ObjLongConsumer<TickContent> consumer) {}

        @Override
        public void applyModifier(double multiplier) {}

        @Override
        public long get(@NotNull TickContent dataKey) {
            return 0;
        }

        @Override
        public void put(@NotNull TickContent key, long value) {
            throw new UnsupportedOperationException("Cannot modify empty map.");
        }
    };

    public TickContent[] key;
    public long[] value;

    public int size;

    public TickContentMap() {
        key = new TickContent[1];
        value = new long[1];
    }

    public TickContentMap(TickContentMap map) {
        this.key = map.key.clone();
        this.value = map.value.clone();
        this.size = map.size;
    }

    public boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, boolean simulated) {
        if (size > 0) {
            final TickContent[] key = this.key;
            final long[] value = this.value;
            final int size = this.size;
            int pos = 0;
            long currValue;
            while (pos < size) {
                if ((currValue = value[pos]) > 0) {
                    if (!key[pos].handleRecipe(holder, recipe, currValue, simulated)) return false;
                }
                pos++;
            }
        }
        return true;
    }

    public void forEach(ObjLongConsumer<TickContent> consumer) {
        final TickContent[] key = this.key;
        final long[] value = this.value;
        final int size = this.size;
        int pos = 0;
        while (pos < size) {
            consumer.accept(key[pos], value[pos]);
            pos++;
        }
    }

    public void applyModifier(double multiplier) {
        final long[] value = this.value;
        final int size = this.size;
        int pos = 0;
        long curr;
        while (pos < size) {
            if ((curr = value[pos]) != 0) value[pos] = (long) (curr * multiplier);
            pos++;
        }
    }

    public long get(@NotNull TickContent content) {
        final TickContent[] key = this.key;
        final int size = this.size;
        for (int i = 0; i < size; i++) {
            if (key[i] == content) {
                return value[i];
            }
        }
        return 0;
    }

    public void put(@NotNull TickContent content, long v) {
        final TickContent[] key = this.key;
        final int size = this.size;
        for (int i = 0; i < size; i++) {
            if (key[i] == content) {
                this.value[i] = v;
                return;
            }
        }
        if (size >= key.length) {
            resize(size + 1);
        }
        this.key[size] = content;
        this.value[size] = v;
        this.size++;
    }

    private void resize(int newSize) {
        TickContent[] newKey = new TickContent[newSize];
        long[] newValue = new long[newSize];
        System.arraycopy(key, 0, newKey, 0, key.length);
        System.arraycopy(value, 0, newValue, 0, value.length);
        this.key = newKey;
        this.value = newValue;
    }

    public TickContentMap copy() {
        return new TickContentMap(this);
    }
}
