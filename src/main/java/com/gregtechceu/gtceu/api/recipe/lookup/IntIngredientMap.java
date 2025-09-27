package com.gregtechceu.gtceu.api.recipe.lookup;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

public class IntIngredientMap extends Int2LongOpenHashMap {

    public static ItemConversion ITEM_CONVERSION = (stack, amount, map) -> map.add(stack.getItem().hashCode(), amount);

    public static FluidConversion FLUID_CONVERSION = (stack, amount, map) -> map.add(stack.getFluid().hashCode(), amount);

    private static final MapEntrySet ENTRIES = new MapEntrySet();

    public static final IntIngredientMap EMPTY = new IntIngredientMap(0) {

        @Override
        public void add(final int k, final long incr) {}

        @Override
        public int[] toIntArray() {
            return new int[0];
        }

        @Override
        public FastEntrySet int2LongEntrySet() {
            return ENTRIES;
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

    @Override
    public FastEntrySet int2LongEntrySet() {
        return size == 0 ? ENTRIES : super.int2LongEntrySet();
    }

    public void add(final int k, final long incr) {
        if (k == 0 || incr == 0) return;
        int pos;
        int curr;
        if (!((curr = key[pos = HashCommon.mix(k) & mask]) == 0)) {
            do if (curr == k) {
                value[pos] = value[pos] + incr;
                return;
            }
            while (!((curr = key[pos = (pos + 1) & mask]) == 0));
        }
        key[pos] = k;
        value[pos] = incr;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
    }

    public int[] toIntArray() {
        int[] a = new int[size];
        int pos = n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                a[i++] = k;
            }
        }
        return a;
    }

    public interface ItemConversion {

        void convert(ItemStack stack, long amount, IntIngredientMap map);
    }

    public interface FluidConversion {

        void convert(FluidStack stack, long amount, IntIngredientMap map);
    }

    private static final class MapEntrySet extends AbstractObjectSet<Entry> implements FastEntrySet {

        @Override
        public ObjectIterator<Entry> fastIterator() {
            return ObjectIterators.emptyIterator();
        }

        @Override
        public @NotNull ObjectIterator<Entry> iterator() {
            return ObjectIterators.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void fastForEach(final Consumer<? super Entry> consumer) {}
    }
}
