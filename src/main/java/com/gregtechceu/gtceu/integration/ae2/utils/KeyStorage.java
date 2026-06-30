package com.gregtechceu.gtceu.integration.ae2.utils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.util.DataCodecs;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used to store {@link appeng.api.stacks.GenericStack } in a way that associates key and amount.
 * Provides methods for serialization and deserialization.
 */
@MethodsReturnNonnullByDefault
public class KeyStorage extends AbstractDataSerializable implements Iterable<Reference2LongMap.Entry<AEKey>> {

    public final ReentrantLock lock = new ReentrantLock();

    public Reference2LongOpenHashMap<AEKey> storage = new Reference2LongOpenHashMap<>();

    // not
    @Nullable
    @Setter
    @Getter
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
            if (key == null) {
                it.remove();
                continue;
            }
            var amount = entry.getLongValue();
            if (amount <= 0) {
                it.remove();
                continue;
            }
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
        syncChange = true;
    }

    @Override
    public Iterator<Reference2LongMap.Entry<AEKey>> iterator() {
        return storage.reference2LongEntrySet().fastIterator();
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        data.writeVarInt(storage.size());
        for (var entry : this) {
            if (entry == null || entry.getLongValue() < 1) {
                data.writeVarLong(0);
            } else {
                data.writeVarLong(entry.getLongValue());
                AEKey.writeKey(data, entry.getKey());
            }
        }
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var size = data.readVarInt();
        for (var i = 0; i < size; i++) {
            var value = data.readVarLong();
            if (value == 0) continue;
            var key = AEKey.readKey(data);
            storage.put(key, value);
        }
    }

    @Override
    public Data writeData() {
        var list = new ListData();
        lock.lock();
        try {
            for (var entry : this) {
                var tag = new CompoundTag();
                if (entry == null) continue;
                tag.put("key", entry.getKey().toTagGeneric());
                tag.putLong("value", entry.getLongValue());
                list.add(DataCodecs.COMPOUND_TAG_CODEC.encode(tag));
            }
        } finally {
            lock.unlock();
        }
        return list.isEmpty() ? NullData.INSTANCE : list;
    }

    @Override
    public void readData(@NotNull Data data, int dataVersion) {
        var list = data.getList();
        for (Data item : list) {
            var tag = DataCodecs.COMPOUND_TAG_CODEC.decode(item, dataVersion);
            var key = AEKey.fromTagGeneric(tag.getCompound("key"));
            if (key == null) continue;
            long value = tag.getLong("value");
            lock.lock();
            try {
                storage.put(key, value);
            } finally {
                lock.unlock();
            }
        }
    }
}
