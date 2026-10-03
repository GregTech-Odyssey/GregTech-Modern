package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * 一个物品存储借给 Forge 的视图栈：cache 与存储共享，存储写某槽即置空该槽；lent 记录借出栈与借出时的数量/标签，
 * 被外部改写的借出栈在该槽下一次经适配器访问时结算（减少按差额从 src 抽走，其余恢复并告警）。
 */
final class ItemViews {

    private static long lastWarn;

    final IKeyHandler<AEItemKey> src;
    final ItemStack[] cache;
    final ItemStack[] lent;
    final AEItemKey[] keys;
    final int[] counts;
    final CompoundTag[] tags;
    long lentBits;
    boolean viewed;

    private ItemViews(IKeyHandler<AEItemKey> src, int size) {
        this.src = src;
        this.cache = new ItemStack[size];
        this.lent = new ItemStack[size];
        this.keys = new AEItemKey[size];
        this.counts = new int[size];
        this.tags = new CompoundTag[size];
    }

    private ItemViews(ItemViews from, int size) {
        this.src = from.src;
        this.cache = Arrays.copyOf(from.cache, size);
        this.lent = Arrays.copyOf(from.lent, size);
        this.keys = Arrays.copyOf(from.keys, size);
        this.counts = Arrays.copyOf(from.counts, size);
        this.tags = Arrays.copyOf(from.tags, size);
        this.lentBits = from.lentBits;
        this.viewed = from.viewed;
    }

    static @Nullable ItemViews peek(KeyInventory<?> inv) {
        return inv.viewOwner() instanceof ItemViews v ? v : null;
    }

    @SuppressWarnings("unchecked")
    static ItemViews attach(KeyInventory<?> inv) {
        if (inv.viewOwner() instanceof ItemViews v) return v;
        var v = new ItemViews((IKeyHandler<AEItemKey>) inv, inv.size());
        inv.attachViews(v, v.cache);
        return v;
    }

    static ItemViews local(IKeyHandler<AEItemKey> src, int size) {
        return new ItemViews(src, size);
    }

    ItemViews grown(int size) {
        return size <= lent.length ? this : new ItemViews(this, size);
    }

    boolean violated(int i) {
        var v = lent[i];
        return v != null && (v.getCount() != counts[i] || v.getTag() != tags[i]);
    }

    void touch(int i) {
        if (violated(i)) settle(i);
    }

    void settle(int i) {
        var view = lent[i];
        int expect = counts[i];
        int now = view.getCount();
        var key = keys[i];
        cache[i] = null;
        if (view.getTag() == tags[i] && now < expect && (now <= 0 || view.getItem() == key.getItem())) {
            long taken = expect - Math.max(now, 0);
            if (now > 0) counts[i] = now;
            else drop(i);
            long done = src.extract(i, key, taken, false);
            if (done < taken) warn("removed more than the view allowed", view);
            return;
        }
        drop(i);
        warn("modified a read view", view);
    }

    void touchKey(AEKey key) {
        if ((lentBits & 1L << key.getUid()) == 0) return;
        var ks = keys;
        long bits = 0;
        for (int i = 0; i < ks.length; i++) {
            var k = ks[i];
            if (k == null) continue;
            if (k == key && violated(i)) {
                settle(i);
                k = ks[i];
                if (k == null) continue;
            }
            bits |= 1L << k.getUid();
        }
        lentBits = bits;
    }

    ItemStack view(int i, @Nullable AEItemKey key, long amount) {
        int count = key == null ? 0 : Keys.saturatedInt(amount);
        if (count <= 0) {
            if (keys[i] != null) drop(i);
            return ItemStack.EMPTY;
        }
        if (keys[i] == key) {
            var v = lent[i];
            if (counts[i] != count) {
                v.setCount(count);
                counts[i] = count;
            }
            return v;
        }
        var v = key.toStack(count);
        lent[i] = v;
        keys[i] = key;
        tags[i] = v.getTag();
        counts[i] = count;
        lentBits |= 1L << key.getUid();
        viewed = true;
        return v;
    }

    void placed(KeyInventory<?> inv, int i, AEItemKey key) {
        var v = lent[i];
        if (v == null || keys[i] != key || v.getCount() != counts[i] || v.getTag() != tags[i]) return;
        long amount = inv.amountAt(i);
        if (amount == 0 || inv.rawKeyAt(i) != key) return;
        int count = Keys.saturatedInt(amount);
        v.setCount(count);
        counts[i] = count;
        cache[i] = v;
    }

    ItemStack taken(KeyInventory<?> inv, int i, AEItemKey key, long n) {
        var v = lent[i];
        if (v == null || keys[i] != key || v.getCount() != counts[i] || v.getTag() != tags[i]) return key.toStack((int) n);
        long left = inv.amountAt(i);
        if (left > 0) {
            if (inv.rawKeyAt(i) == key) {
                int count = Keys.saturatedInt(left);
                v.setCount(count);
                counts[i] = count;
                cache[i] = v;
            }
            return key.toStack((int) n);
        }
        boolean whole = counts[i] == n && tags[i] == null;
        drop(i);
        cache[i] = ItemStack.EMPTY;
        return whole ? v : key.toStack((int) n);
    }

    private void drop(int i) {
        cache[i] = null;
        lent[i] = null;
        keys[i] = null;
        tags[i] = null;
        counts[i] = 0;
    }

    private static void warn(String what, ItemStack view) {
        long now = System.currentTimeMillis();
        if (now - lastWarn > 10000) {
            lastWarn = now;
            GTCEu.LOGGER.warn("An external mod {} of a GregTech inventory ({})", what, view);
        }
    }
}
