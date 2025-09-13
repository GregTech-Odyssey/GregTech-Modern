package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.utils.collection.O2LOpenCacheHashMap;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used to store {@link appeng.api.stacks.GenericStack } in a way that associates key and amount.
 * Provides methods for serialization and deserialization.
 */
@MethodsReturnNonnullByDefault
public class KeyStorage implements ITagSerializable<ListTag>, IContentChangeAware, Iterable<Object2LongMap.Entry<AEKey>> {

    public final ReentrantLock lock = new ReentrantLock();

    public final Object2LongOpenHashMap<AEKey> storage = new O2LOpenCacheHashMap<>();

    // not
    @Nullable
    private Runnable onContentsChanged;

    /**
     * Insert the stacks into the inventory as much as possible
     *
     * @param inventory the inventory into which stacks will be inserted
     * @param source    the source of the action
     */
    public void insertInventory(MEStorage inventory, IActionSource source) {
        var it = iterator();
        boolean changed = false;
        while (it.hasNext()) {
            var entry = it.next();
            var key = entry.getKey();
            var amount = entry.getLongValue();
            long inserted = inventory.insert(key, amount, Actionable.MODULATE, source);
            if (inserted > 0) {
                changed = true;
                if (inserted >= amount) {
                    it.remove();
                } else {
                    entry.setValue(amount - inserted);
                }
            }
        }
        if (changed) {
            onChanged();
        }
    }

    public void onChanged() {
        if (onContentsChanged != null) {
            onContentsChanged.run();
        }
    }

    @Override
    public ListTag serializeNBT() {
        var list = new ListTag();
        lock.lock();
        try {
            for (Object2LongMap.Entry<AEKey> entry : this) {
                var tag = new CompoundTag();
                tag.put("key", entry.getKey().toTagGeneric());
                tag.putLong("value", entry.getLongValue());
                list.add(tag);
            }
        } finally {
            lock.unlock();
        }
        return list;
    }

    @Override
    public void deserializeNBT(ListTag tags) {
        for (int i = 0; i < tags.size(); i++) {
            var tag = tags.getCompound(i);
            var key = AEKey.fromTagGeneric(tag.getCompound("key"));
            long value = tag.getLong("value");
            lock.lock();
            try {
                storage.put(key, value);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        return storage.object2LongEntrySet().fastIterator();
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    @Nullable
    public Runnable getOnContentsChanged() {
        return this.onContentsChanged;
    }

    public void setOnContentsChanged(@Nullable final Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
    }
}
