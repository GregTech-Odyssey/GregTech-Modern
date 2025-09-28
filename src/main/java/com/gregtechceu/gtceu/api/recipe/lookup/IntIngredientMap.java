package com.gregtechceu.gtceu.api.recipe.lookup;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

public class IntIngredientMap extends Int2LongOpenHashMap {

    public static Conversion<ItemStack> ITEM_CONVERSION = (stack, amount, map) -> map.add(stack.getItem().hashCode(), amount);

    public static Conversion<FluidStack> FLUID_CONVERSION = (stack, amount, map) -> map.add(stack.getFluid().hashCode(), amount);

    public static final IntIngredientMap EMPTY = new IntIngredientMap(0) {

        @Override
        public void fill(IntIngredientMap map) {}

        @Override
        public void add(final int k, final long incr) {}

        @Override
        public void fillArray(int[] key, long[] value) {}

        @Override
        public int[] toIntArray() {
            return new int[0];
        }
    };

    public IntIngredientMap(int expected) {
        super(expected);
    }

    public IntIngredientMap() {
        super();
    }

    @Override
    public long addTo(final int k, final long incr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long put(final int k, final long v) {
        throw new UnsupportedOperationException();
    }

    public void fill(IntIngredientMap map) {
        if (size == 0) return;
        int pos = n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                map.add(k, value[pos]);
                if (++i == size) break;
            }
        }
    }

    public void add(final int k, final long incr) {
        if (k == 0 || incr == 0) return;
        int pos;
        int curr;
        if ((curr = key[pos = HashCommon.mix(k) & mask]) != 0) {
            do if (curr == k) {
                var v = value[pos] + incr;
                if (v < 0) v = Long.MAX_VALUE;
                value[pos] = v;
                return;
            }
            while ((curr = key[pos = (pos + 1) & mask]) != 0);
        }
        key[pos] = k;
        value[pos] = incr;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
    }

    public void fillArray(int[] key, long[] value) {
        int pos = n;
        int i = 0;
        while (pos-- != 0) {
            int k = this.key[pos];
            if (k != 0) {
                key[i] = k;
                value[i] = this.value[pos];
                if (++i == size) break;
            }
        }
    }

    public int[] toIntArray() {
        int[] a = new int[size];
        int pos = n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                a[i] = k;
                if (++i == size) break;
            }
        }
        return a;
    }

    public interface Conversion<T> {

        void convert(T stack, long amount, IntIngredientMap map);
    }
}
