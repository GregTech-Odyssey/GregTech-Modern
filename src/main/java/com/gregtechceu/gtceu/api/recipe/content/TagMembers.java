package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.hooks.IUnique;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class TagMembers {

    static final TagMembers EMPTY = new TagMembers(new int[0], null, 0, null);
    private static final int LINEAR_LIMIT = 8;

    private final int[] uids;
    @Nullable
    private final long[] bits;
    private final int minUid;
    @Nullable
    final AEKey first;

    private TagMembers(int[] uids, @Nullable long[] bits, int minUid, @Nullable AEKey first) {
        this.uids = uids;
        this.bits = bits;
        this.minUid = minUid;
        this.first = first;
    }

    boolean contains(int uid) {
        var b = bits;
        if (b != null) {
            int d = uid - minUid;
            return Integer.compareUnsigned(d, b.length << 6) < 0 && (b[d >>> 6] & (1L << d)) != 0;
        }
        for (int u : uids) {
            if (u == uid) return true;
        }
        return false;
    }

    int size() {
        return uids.length;
    }

    int[] uids() {
        return uids;
    }

    @SuppressWarnings("unchecked")
    static TagMembers ofItems(TagKey<?> tag) {
        var set = BuiltInRegistries.ITEM.getTag((TagKey<Item>) tag);
        if (set.isEmpty()) return EMPTY;
        var list = new IntArrayList();
        AEKey first = null;
        for (var holder : set.get()) {
            var item = holder.value();
            list.add(IUnique.getUid(item));
            if (first == null) first = Keys.item(item);
        }
        return build(list, first);
    }

    @SuppressWarnings("unchecked")
    static TagMembers ofFluids(TagKey<?> tag) {
        var set = BuiltInRegistries.FLUID.getTag((TagKey<Fluid>) tag);
        if (set.isEmpty()) return EMPTY;
        var list = new IntArrayList();
        AEKey first = null;
        for (var holder : set.get()) {
            var fluid = holder.value();
            list.add(IUnique.getUid(fluid));
            if (first == null) first = AEFluidKey.of(fluid);
        }
        return build(list, first);
    }

    static TagMembers ofUnion(int[] memberUids, @Nullable AEKey first) {
        return build(new IntArrayList(memberUids), first);
    }

    private static TagMembers build(IntArrayList list, @Nullable AEKey first) {
        int[] uids = list.toIntArray();
        Arrays.sort(uids);
        int n = 0;
        for (int i = 0; i < uids.length; i++) {
            if (i == 0 || uids[i] != uids[i - 1]) uids[n++] = uids[i];
        }
        uids = Arrays.copyOf(uids, n);
        if (n <= LINEAR_LIMIT) return new TagMembers(uids, null, 0, first);
        int min = uids[0];
        int span = uids[n - 1] - min + 1;
        long[] bits = new long[(span + 63) >>> 6];
        for (int u : uids) {
            int d = u - min;
            bits[d >>> 6] |= 1L << d;
        }
        return new TagMembers(uids, bits, min, first);
    }
}
