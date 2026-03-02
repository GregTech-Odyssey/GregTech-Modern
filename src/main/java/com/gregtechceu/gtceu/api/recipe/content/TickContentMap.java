package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.ObjLongConsumer;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

public class TickContentMap extends Reference2LongOpenHashMap<TickContent> {

    public static final TickContentMap EMPTY = new TickContentMap() {

        @Override
        public long getData(@NotNull TickContent dataKey) {
            return 0;
        }

        @Override
        public long put(@NotNull TickContent key, long value) {
            throw new UnsupportedOperationException("Cannot modify empty map.");
        }

        @Override
        public long removeLong(@NotNull Object key) {
            throw new UnsupportedOperationException("Cannot modify empty map.");
        }

        @Override
        public TickContentMap clone() {
            return new TickContentMap();
        }
    };

    public TickContentMap() {
        super(2, 0.75F);
    }

    public TickContentMap(TickContentMap map) {
        super(map.size, 0.75F);
        map.fastForEach(this::put);
    }

    public boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, boolean simulated) {
        if (size > 0) {
            final Object[] key = this.key;
            final long[] value = this.value;
            final int len = key.length;
            int pos = 0;
            Object curr;
            long currValue;
            while (pos < len) {
                if ((curr = key[pos]) != null && (currValue = value[pos]) > 0) {
                    if (!((TickContent) curr).handleRecipe(holder, recipe, currValue, simulated)) return false;
                }
                pos++;
            }
        }
        return true;
    }

    public void fastForEach(ObjLongConsumer<TickContent> consumer) {
        final Object[] key = this.key;
        final long[] value = this.value;
        final int len = key.length;
        int pos = 0;
        Object curr;
        while (pos < len) {
            if ((curr = key[pos]) != null) consumer.accept((TickContent) curr, value[pos]);
            pos++;
        }
    }

    public void applyModifier(double multiplier) {
        final long[] value = this.value;
        final int len = value.length;
        int pos = 0;
        long curr;
        while (pos < len) {
            if ((curr = value[pos]) != 0) value[pos] = (long) (curr * multiplier);
            pos++;
        }
    }

    public long getData(@NotNull TickContent dataKey) {
        final Object[] key = this.key;
        Object curr;
        int pos;
        if ((curr = key[pos = dataKey.mixCode & this.mask]) == null) {
            return 0;
        } else if (dataKey == curr) {
            return this.value[pos];
        } else {
            while ((curr = key[pos = pos + 1 & this.mask]) != null) {
                if (dataKey == curr) {
                    return this.value[pos];
                }
            }
            return 0;
        }
    }

    @Override
    public long put(@NotNull TickContent dataKey, long v) {
        final Object[] key = this.key;
        int pos;
        Object curr;
        if ((curr = key[pos = dataKey.mixCode & mask]) != null) {
            do if (curr == dataKey) {
                final long oldValue = value[pos];
                value[pos] = v;
                return oldValue;
            }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = dataKey;
        value[pos] = v;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        return 0;
    }

    @Override
    public TickContentMap clone() {
        return (TickContentMap) super.clone();
    }
}
