package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyIntMap;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datastream.data.ByteArrayData;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.IntData;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.datastream.data.NullData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * 物品/流体的唯一物资存储：每槽一个 key 与一个 long 数量，空槽以数量为 0 判定（key 可残留）。
 * 配方提交用 {@code extractQuiet/insertQuiet} 批量改动后由调用方 {@link #notifyChanged()} 一次。
 */
public final class KeyInventory<K extends AEKey> extends AbstractDataSerializable implements IKeyHandler<K> {

    static final int MAGIC = 0x4B455900;
    static final int FLAG_UNIQUE = 1;
    static final int FLAG_COMPACT = 2;
    private static final int INDEX_THRESHOLD = 16;

    private final AEKeyType type;
    private final boolean stackLimited;
    private final boolean growable;
    private AEKey[] keys;
    private long[] amounts;
    private int[] uids;
    @Nullable
    private Object[] views;
    @Nullable
    private Object viewOwner;
    private final long slotLimit;
    private boolean uniqueKeys;
    @Nullable
    private AEKeyIntMap<AEKey> index;
    @Nullable
    private AEKeyFilter filter;
    @Nullable
    private AEKey[] locked;
    private Runnable onChanged = GTUtil.NOOP;
    private int version;
    private int high;

    private KeyInventory(AEKeyType type, int size, long slotLimit, boolean stackLimited, boolean growable) {
        this.type = type;
        this.keys = new AEKey[size];
        this.amounts = new long[size];
        this.uids = new int[size];
        this.slotLimit = slotLimit;
        this.stackLimited = stackLimited;
        this.growable = growable;
    }

    public static KeyInventory<AEItemKey> items(int slots) {
        return new KeyInventory<>(AEKeyTypes.ITEMS, slots, 64, true, false);
    }

    public static KeyInventory<AEItemKey> items(int slots, long slotLimit, boolean stackLimited) {
        return new KeyInventory<>(AEKeyTypes.ITEMS, slots, slotLimit, stackLimited, false);
    }

    public static KeyInventory<AEFluidKey> fluids(int tanks, long capacity) {
        return new KeyInventory<>(AEKeyTypes.FLUIDS, tanks, capacity, false, false);
    }

    public static <T extends AEKey> KeyInventory<T> growable(AEKeyType type, int initial, long slotLimit) {
        var inv = new KeyInventory<T>(type, Math.max(1, initial), slotLimit, false, true);
        inv.setUniqueKeys(true);
        return inv;
    }

    @Override
    public AEKeyType keyType() {
        return type;
    }

    @Override
    public int size() {
        return keys.length;
    }

    @Override
    public boolean fixedSize() {
        return !growable;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable K keyAt(int slot) {
        return amounts[slot] == 0 ? null : (K) keys[slot];
    }

    @Override
    public long amountAt(int slot) {
        return amounts[slot];
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R readSlot(int slot, int index, SlotReader<? super K, R> reader) {
        long amount = amounts[slot];
        return reader.read(index, amount == 0 ? null : (K) keys[slot], amount);
    }

    public int uidAt(int slot) {
        return uids[slot];
    }

    public AEKey rawKeyAt(int slot) {
        return keys[slot];
    }

    @Override
    public long slotLimit(int slot) {
        if (stackLimited && amounts[slot] != 0) return limitFor(keys[slot]);
        return slotLimit;
    }

    public long slotLimit() {
        return slotLimit;
    }

    public boolean isStackLimited() {
        return stackLimited;
    }

    public boolean isGrowable() {
        return growable;
    }

    public boolean isUniqueKeys() {
        return uniqueKeys;
    }

    public KeyInventory<K> setUniqueKeys(boolean uniqueKeys) {
        this.uniqueKeys = uniqueKeys;
        rebuildIndex();
        return this;
    }

    public @Nullable AEKeyFilter getFilter() {
        return filter;
    }

    public KeyInventory<K> setFilter(@Nullable AEKeyFilter filter) {
        this.filter = filter;
        return this;
    }

    public @Nullable AEKey lockedAt(int slot) {
        return locked == null ? null : locked[slot];
    }

    public void setLocked(int slot, @Nullable AEKey key) {
        if (locked == null) {
            if (key == null) return;
            locked = new AEKey[keys.length];
        }
        locked[slot] = key;
    }

    public Runnable getOnChanged() {
        return onChanged;
    }

    public KeyInventory<K> setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public int version() {
        return version;
    }

    @ApiStatus.Internal
    public @Nullable Object viewOwner() {
        return viewOwner;
    }

    @ApiStatus.Internal
    public void attachViews(Object owner, Object[] views) {
        if (viewOwner != null || growable || views.length != keys.length) throw new IllegalStateException("views already attached or inventory not fixed-size");
        this.viewOwner = owner;
        this.views = views;
    }

    public long limitFor(AEKey key) {
        if (stackLimited) {
            long m = ((AEItemKey) key).getMaxStackSize();
            return m < slotLimit ? m : slotLimit;
        }
        return slotLimit;
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        return insertAt(slot, key, amount, simulate, true);
    }

    public long insertExplicit(int slot, K key, long amount, boolean simulate) {
        return insertAt(slot, key, amount, simulate, false);
    }

    private long insertAt(int slot, K key, long amount, boolean simulate, boolean unique) {
        if (amount <= 0) return 0;
        long stored = amounts[slot];
        if (stored != 0) {
            if (keys[slot] != key) return 0;
        } else if (!(unique ? acceptsEmpty(slot, key) : acceptsEmptyNoUnique(slot, key))) {
            return 0;
        }
        long space = limitFor(key) - stored;
        if (space <= 0) return 0;
        long n = amount < space ? amount : space;
        if (!simulate) {
            if (stored == 0) {
                writeKey(slot, key);
                if (slot >= high) high = slot + 1;
            }
            amounts[slot] = stored + n;
            mark(slot);
            notifyChanged();
        }
        return n;
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        long stored = amounts[slot];
        if (amount <= 0 || stored == 0 || keys[slot] != key) return 0;
        long n = amount < stored ? amount : stored;
        if (!simulate) {
            amounts[slot] = stored - n;
            mark(slot);
            if (slot + 1 == high) trimHigh();
            notifyChanged();
        }
        return n;
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long left = insertInto(key, amount, simulate);
        long n = amount - left;
        if (n > 0 && !simulate) notifyChanged();
        return n;
    }

    private long insertInto(AEKey key, long amount, boolean simulate) {
        long left = amount;
        long limit = limitFor(key);
        int firstEmpty = 0;
        if (uniqueKeys) {
            int s = indexOf(key);
            if (s >= 0) {
                long space = limit - amounts[s];
                if (space <= 0) return left;
                long n = left < space ? left : space;
                if (!simulate) {
                    amounts[s] += n;
                    mark(s);
                }
                return left - n;
            }
        } else {
            firstEmpty = -1;
            int h = high;
            for (int i = 0; i < h; i++) {
                long a = amounts[i];
                if (a == 0) {
                    if (firstEmpty < 0) firstEmpty = i;
                } else if (keys[i] == key) {
                    long space = limit - a;
                    if (space > 0) {
                        long n = left < space ? left : space;
                        if (!simulate) {
                            amounts[i] += n;
                            mark(i);
                        }
                        left -= n;
                        if (left == 0) return 0;
                    }
                }
            }
            if (firstEmpty < 0) firstEmpty = h;
        }
        if (limit <= 0) return left;
        for (int i = firstEmpty; i < keys.length; i++) {
            if (amounts[i] == 0 && acceptsEmptyNoUnique(i, key)) {
                long n = left < limit ? left : limit;
                if (!simulate) {
                    writeKey(i, key);
                    amounts[i] = n;
                    mark(i);
                    if (i >= high) high = i + 1;
                }
                left -= n;
                if (left == 0 || uniqueKeys) return left;
            }
        }
        if (growable && left > 0 && acceptsEmptyNoUnique(-1, key)) {
            if (simulate) return 0;
            int slot = keys.length;
            grow(slot + 1);
            writeKey(slot, key);
            amounts[slot] = left;
            mark(slot);
            high = slot + 1;
            return 0;
        }
        return left;
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long left = amount;
        if (uniqueKeys && index != null) {
            int s = indexOf(key);
            if (s >= 0) {
                long stored = amounts[s];
                long n = left < stored ? left : stored;
                if (!simulate) {
                    amounts[s] = stored - n;
                    mark(s);
                }
                left -= n;
            }
        } else {
            int h = high;
            for (int i = 0; i < h; i++) {
                long stored = amounts[i];
                if (stored != 0 && keys[i] == key) {
                    long n = left < stored ? left : stored;
                    if (!simulate) {
                        amounts[i] = stored - n;
                        mark(i);
                    }
                    left -= n;
                    if (left == 0) break;
                }
            }
        }
        long n = amount - left;
        if (n > 0 && !simulate) {
            int h = high;
            if (h > 0 && amounts[h - 1] == 0) trimHigh();
            notifyChanged();
        }
        return n;
    }

    @Override
    public long spaceFor(int slot, K key) {
        return spaceAt(slot, key, true);
    }

    public long spaceForExplicit(int slot, K key) {
        return spaceAt(slot, key, false);
    }

    private long spaceAt(int slot, K key, boolean unique) {
        long stored = amounts[slot];
        if (stored != 0) {
            if (keys[slot] != key) return 0;
        } else if (!(unique ? acceptsEmpty(slot, key) : acceptsEmptyNoUnique(slot, key))) {
            return 0;
        }
        long space = limitFor(key) - stored;
        return space > 0 ? space : 0;
    }

    @Override
    public long count(K key) {
        if (uniqueKeys && index != null) {
            int s = indexOf(key);
            return s >= 0 ? amounts[s] : 0;
        }
        long total = 0;
        int h = high;
        for (int i = 0; i < h; i++) {
            if (amounts[i] != 0 && keys[i] == key) {
                long t = total + amounts[i];
                total = t < 0 ? Long.MAX_VALUE : t;
            }
        }
        return total;
    }

    public int indexOf(AEKey key) {
        if (index != null) {
            int s = index.getInt(key);
            return s >= 0 && amounts[s] != 0 && keys[s] == key ? s : -1;
        }
        int h = high;
        for (int i = 0; i < h; i++) {
            if (amounts[i] != 0 && keys[i] == key) return i;
        }
        return -1;
    }

    public boolean acceptsEmpty(int slot, AEKey key) {
        if (!acceptsEmptyNoUnique(slot, key)) return false;
        if (uniqueKeys) {
            int s = indexOf(key);
            return s < 0 || s == slot;
        }
        return true;
    }

    private boolean acceptsEmptyNoUnique(int slot, AEKey key) {
        if (key.getType() != type) return false;
        if (locked != null && slot >= 0) {
            var l = locked[slot];
            if (l != null && l != key) return false;
        }
        return filter == null || filter.matches(key);
    }

    private void writeKey(int slot, AEKey key) {
        var old = keys[slot];
        if (old == key) return;
        keys[slot] = key;
        uids[slot] = key.getUid();
        var idx = index;
        if (idx != null) {
            if (old != null && idx.getInt(old) == slot) idx.removeInt(old);
            idx.put(key, slot);
        }
    }

    private void grow(int size) {
        int n = Math.max(size, keys.length + (keys.length >> 1) + 1);
        var newAmounts = Arrays.copyOf(amounts, n);
        var newUids = Arrays.copyOf(uids, n);
        var newKeys = Arrays.copyOf(keys, n);
        if (locked != null) locked = Arrays.copyOf(locked, n);
        amounts = newAmounts;
        uids = newUids;
        keys = newKeys;
        if (index == null && uniqueKeys) rebuildIndex();
    }

    private void rebuildIndex() {
        if (uniqueKeys && (growable || keys.length >= INDEX_THRESHOLD)) {
            var idx = new AEKeyIntMap<>(keys.length);
            idx.defaultReturnValue(-1);
            for (int i = 0; i < keys.length; i++) {
                if (amounts[i] != 0) idx.put(keys[i], i);
            }
            index = idx;
        } else {
            index = null;
        }
    }

    public void set(int slot, @Nullable AEKey key, long amount) {
        if (key == null || amount <= 0 || key.getType() != type) {
            amounts[slot] = 0;
            if (slot + 1 == high) trimHigh();
        } else {
            writeKey(slot, key);
            amounts[slot] = amount;
            if (slot >= high) high = slot + 1;
        }
        mark(slot);
        notifyChanged();
    }

    @ApiStatus.Internal
    public void extractQuiet(int slot, long amount) {
        mark(slot);
        if ((amounts[slot] -= amount) == 0 && slot + 1 == high) trimHigh();
    }

    @ApiStatus.Internal
    public void insertQuiet(int slot, AEKey key, long amount) {
        if (amounts[slot] == 0) {
            writeKey(slot, key);
            if (slot >= high) high = slot + 1;
        }
        amounts[slot] += amount;
        mark(slot);
    }

    private void mark(int slot) {
        var v = views;
        if (v != null) v[slot] = null;
    }

    private void markAll() {
        var v = views;
        if (v != null) Arrays.fill(v, null);
    }

    private void trimHigh() {
        var a = amounts;
        int h = high;
        while (h > 0 && a[h - 1] == 0) h--;
        high = h;
    }

    @ApiStatus.Internal
    public void notifyChanged() {
        version++;
        changed = true;
        onChanged.run();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var a = amounts;
        int h = high;
        if (out.isEmpty()) {
            int filled = 0;
            for (int i = 0; i < h; i++) {
                if (a[i] > 0) filled++;
            }
            if (filled == 0) return;
            out.ensureCapacity(filled);
        }
        var k = keys;
        for (int i = 0; i < h; i++) {
            long amount = a[i];
            if (amount > 0) out.add(k[i], amount);
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        getAvailableStacks(out);
    }

    @Override
    public boolean containsAny(Set<AEKey> primaryKeys) {
        var a = amounts;
        var k = keys;
        int h = high;
        for (int i = 0; i < h; i++) {
            if (a[i] > 0 && primaryKeys.contains(k[i].dropSecondary())) return true;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        for (long a : amounts) {
            if (a != 0) return false;
        }
        return true;
    }

    public void clear() {
        Arrays.fill(amounts, 0);
        high = 0;
        markAll();
        notifyChanged();
    }

    public void copyFrom(KeyInventory<?> other) {
        if (growable && other.keys.length > keys.length) grow(other.keys.length);
        int n = Math.min(other.keys.length, keys.length);
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (other.amounts[i] > 0 && other.keys[i].getType() == type) {
                writeKey(i, other.keys[i]);
                amounts[i] = other.amounts[i];
                h = i + 1;
            } else {
                amounts[i] = 0;
            }
        }
        for (int i = n; i < keys.length; i++) amounts[i] = 0;
        high = h;
        uniqueKeys = other.uniqueKeys;
        rebuildIndex();
        markAll();
        notifyChanged();
    }

    @Override
    public void writeBuffer(LogicalSide side, @NotNull FriendlyByteBuf buf) {
        buf.writeVarInt(keys.length);
        for (int i = 0; i < keys.length; i++) {
            if (amounts[i] > 0) {
                buf.writeVarInt(i + 1);
                AEKey.writeKey(buf, keys[i]);
                buf.writeVarLong(amounts[i]);
            }
        }
        buf.writeVarInt(0);
        buf.writeBoolean(uniqueKeys);
    }

    @Override
    public void readBuffer(LogicalSide side, @NotNull FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (growable && size > keys.length && size <= 1 << 16) grow(size);
        Arrays.fill(amounts, 0);
        high = 0;
        int s;
        while ((s = buf.readVarInt()) != 0) {
            var key = AEKey.readKey(buf);
            long amount = buf.readVarLong();
            put(s - 1, key, amount);
        }
        uniqueKeys = buf.readBoolean();
        rebuildIndex();
        markAll();
        version++;
        onChanged.run();
    }

    @Override
    public Data writeData() {
        int count = 0;
        for (long a : amounts) {
            if (a > 0) count++;
        }
        if (count == 0 && !uniqueKeys) return NullData.INSTANCE;
        return new ByteArrayData(KeyInventoryCodec.encode(this, MAGIC | FLAG_COMPACT | (uniqueKeys ? FLAG_UNIQUE : 0), count));
    }

    @Override
    public void readData(@NotNull Data data, int dataVersion) {
        Arrays.fill(amounts, 0);
        high = 0;
        if (data instanceof ByteArrayData array) {
            byte[] bytes = array.getByteArray();
            int header = bytes.length >= 4 ? (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF : 0;
            if ((header & ~0xFF) == MAGIC && (header & FLAG_COMPACT) != 0) {
                uniqueKeys = (header & FLAG_UNIQUE) != 0;
                try {
                    KeyInventoryCodec.decode(this, bytes);
                } catch (RuntimeException e) {
                    GTCEu.LOGGER.error("Failed to read key inventory", e);
                }
            }
        } else if (!(data instanceof ListData list && !list.isEmpty() && list.get(0) instanceof IntData(int header) && (header & ~0xFF) == MAGIC)) {
            LegacyKeyInventoryCodec.decode(this, data, dataVersion);
        }
        rebuildIndex();
        markAll();
        version++;
    }

    public ByteArrayTag serializeNBT() {
        return new ByteArrayTag(writeData().writeToBytes());
    }

    public void deserializeNBT(Tag tag) {
        if (tag instanceof ByteArrayTag bytes) {
            readData(Data.readData(bytes.getAsByteArray()), GTDataFixer.VERSION);
        } else if (tag instanceof CompoundTag compound) {
            readData(DataCodecs.COMPOUND_TAG_CODEC.encode(compound), 0);
        }
    }

    void put(int slot, @Nullable AEKey key, long amount) {
        if (key == null || amount <= 0 || slot < 0 || key.getType() != type) return;
        if (slot >= keys.length) {
            if (!growable || slot > 1 << 16) return;
            grow(slot + 1);
        }
        writeKey(slot, key);
        amounts[slot] = amount;
        mark(slot);
        if (slot >= high) high = slot + 1;
    }

    void legacyUniqueKeys(boolean uniqueKeys) {
        this.uniqueKeys = uniqueKeys;
    }
}
