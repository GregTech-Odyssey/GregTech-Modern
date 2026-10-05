package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IFilteredHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.StorageAccess;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.recipesearch.IntLongMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 机器的物品或流体仓：持有一个 {@link KeyInventory}，作为配方处理器参与匹配，对外以 {@link IKeyHandler} 视图按能力方向放行插取。
 */
public class NotifiableInventory<K extends AEKey> extends NotifiableContentHandler implements IRecipeHandler, ICapabilityTrait, IKeyHandler<K> {

    private static final ClassValue<Boolean> PLAIN_READS = new ClassValue<>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("keyAt", int.class).getDeclaringClass() == NotifiableInventory.class &&
                        type.getMethod("amountAt", int.class).getDeclaringClass() == NotifiableInventory.class &&
                        type.getMethod("size").getDeclaringClass() == NotifiableInventory.class &&
                        type.getMethod("fixedSize").getDeclaringClass() == NotifiableInventory.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    @Getter
    public final IO capabilityIO;
    @Setter
    @Getter
    @SuppressWarnings("unchecked")
    protected Predicate<@Nullable Direction> capabilityValidator = GTUtil.FAVORABLE;
    public final KeyInventory<K> storage;
    @SaveToDisk(key = "storage")
    @SuppressWarnings("rawtypes")
    private final KeyInventory persistedStorage;
    @Getter
    @SaveToDisk
    @SyncToClient
    @Nullable
    protected AEKey lockedKey;
    @SaveToDisk(defaultValue = "false")
    public boolean isVoiding;
    @Nullable
    private AEKeyFilter userFilter;

    @Nullable
    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    @SaveToDisk(key = "storages", listener = "migrateLegacyTanks")
    private KeyInventory<AEFluidKey> legacyTanks;
    @Nullable
    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    @SaveToDisk(key = "lockedFluid", listener = "migrateLegacyLock")
    private KeyInventory<AEFluidKey> legacyLock;

    public NotifiableInventory(MetaMachine machine, KeyInventory<K> storage, IO handlerIO, IO capabilityIO) {
        super(machine, handlerIO);
        this.storage = storage;
        this.persistedStorage = storage;
        this.capabilityIO = capabilityIO;
        storage.setOnChanged(this::onContentsChanged);
        if (storage.keyType() == AEKeyTypes.FLUIDS) {
            legacyTanks = KeyInventory.fluids(Math.max(1, storage.size()), Long.MAX_VALUE);
            legacyLock = KeyInventory.fluids(1, Long.MAX_VALUE);
        }
    }

    public static NotifiableInventory<AEItemKey> items(MetaMachine machine, int slots, IO handlerIO, IO capabilityIO) {
        return new NotifiableInventory<>(machine, KeyInventory.items(slots), handlerIO, capabilityIO);
    }

    public static NotifiableInventory<AEItemKey> items(MetaMachine machine, int slots, IO handlerIO) {
        return items(machine, slots, handlerIO, handlerIO);
    }

    public static NotifiableInventory<AEItemKey> items(MetaMachine machine, KeyInventory<AEItemKey> storage, IO handlerIO, IO capabilityIO) {
        return new NotifiableInventory<>(machine, storage, handlerIO, capabilityIO);
    }

    public static NotifiableInventory<AEFluidKey> fluids(MetaMachine machine, int tanks, long capacity, IO handlerIO, IO capabilityIO) {
        var inv = new NotifiableInventory<>(machine, KeyInventory.fluids(tanks, capacity), handlerIO, capabilityIO);
        if (tanks > 1 && handlerIO == IO.IN) inv.storage.setUniqueKeys(true);
        return inv;
    }

    public static NotifiableInventory<AEFluidKey> fluids(MetaMachine machine, int tanks, long capacity, IO handlerIO) {
        return fluids(machine, tanks, capacity, handlerIO, handlerIO);
    }

    public static <T extends AEKey> NotifiableInventory<T> empty(MetaMachine machine, AEKeyType type) {
        var storage = type == AEKeyTypes.FLUIDS ? KeyInventory.fluids(0, 0) : KeyInventory.items(0);
        @SuppressWarnings("unchecked")
        var inv = new NotifiableInventory<>(machine, (KeyInventory<T>) storage, IO.NONE, IO.NONE);
        return inv.setAvailable(false);
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    private void migrateLegacyTanks(@Nullable KeyInventory<AEFluidKey> tanks) {
        if (tanks != null && !tanks.isEmpty()) {
            int n = Math.min(tanks.size(), storage.size());
            for (int i = 0; i < n; i++) {
                var key = tanks.keyAt(i);
                if (key != null) storage.set(i, key, tanks.amountAt(i));
            }
            tanks.clear();
        }
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    private void migrateLegacyLock(@Nullable KeyInventory<AEFluidKey> lock) {
        if (lock != null) {
            var key = lock.keyAt(0);
            if (key != null) setLockedKey(key);
            lock.clear();
        }
    }

    @Override
    public @Nullable KeyInventory<?> storage(AEKeyType type) {
        return type == storage.keyType() ? storage : null;
    }

    @Override
    public boolean isInfiniteCapacity(AEKeyType type) {
        return false;
    }

    @Override
    public int getPriority() {
        return lockedKey == null ? super.getPriority() : IFilteredHandler.HIGH - storage.size();
    }

    public boolean isLocked() {
        return lockedKey != null;
    }

    public void setLocked(boolean locked) {
        setLocked(locked, storage.keyAt(0));
    }

    public void setLocked(boolean locked, @Nullable AEKey key) {
        if (isLocked() == locked) return;
        setLockedKey(locked ? key : null);
        notifyListeners();
    }

    private void setLockedKey(@Nullable AEKey key) {
        lockedKey = key;
        applyFilter();
    }

    public NotifiableInventory<K> setFilter(@Nullable AEKeyFilter filter) {
        this.userFilter = filter;
        applyFilter();
        return this;
    }

    private void applyFilter() {
        var lock = lockedKey;
        var user = userFilter;
        if (lock == null) {
            storage.setFilter(user);
        } else if (user == null) {
            storage.setFilter(k -> k == lockedKey);
        } else {
            storage.setFilter(k -> k == lockedKey && userFilter.matches(k));
        }
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        applyFilter();
    }

    @Override
    public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {
        int size = storage.size();
        for (int i = 0; i < size; i++) {
            long amount = storage.amountAt(i);
            if (amount > 0) type.convertKey(storage.rawKeyAt(i), amount, map);
        }
    }

    @Override
    public boolean updateEmpty() {
        return storage.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public void exportToNearby(@NotNull Direction... facings) {
        if (isEmpty()) return;
        var m = getMachine();
        var level = m.getLevel();
        var pos = m.getPos();
        var cache = m.holder.blockEntityDirectionCache;
        var inv = storage;
        var type = inv.keyType();
        for (Direction facing : facings) {
            var target = cache.getAdjacentKeyHandler(level, pos, facing, type, StorageAccess.INSERT);
            if (target != null) KeyTransfer.transfer(inv, (IKeyHandler<K>) target, Long.MAX_VALUE, m.getKeyCapFilter(facing, IO.OUT, type));
        }
    }

    @SuppressWarnings("unchecked")
    public void importFromNearby(@NotNull Direction... facings) {
        var m = getMachine();
        var level = m.getLevel();
        var pos = m.getPos();
        var cache = m.holder.blockEntityDirectionCache;
        var inv = storage;
        var type = inv.keyType();
        for (Direction facing : facings) {
            var source = cache.getAdjacentKeyHandler(level, pos, facing, type, StorageAccess.EXTRACT);
            if (source != null) KeyTransfer.transfer((IKeyHandler<K>) source, inv, Long.MAX_VALUE, m.getKeyCapFilter(facing, IO.IN, type));
        }
    }

    @Override
    public AEKeyType keyType() {
        return storage.keyType();
    }

    @Override
    public IKeyHandler<K> unrestricted() {
        return storage;
    }

    @ApiStatus.Internal
    public final @Nullable KeyInventory<K> readStorage() {
        return PLAIN_READS.get(getClass()) ? storage : null;
    }

    @Override
    public int size() {
        return storage.size();
    }

    @Override
    public boolean fixedSize() {
        return storage.fixedSize();
    }

    @Override
    public @Nullable K keyAt(int slot) {
        return storage.keyAt(slot);
    }

    @Override
    public long amountAt(int slot) {
        return storage.amountAt(slot);
    }

    @Override
    public long slotLimit(int slot) {
        return storage.slotLimit(slot);
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        if (!canCapInput()) return 0;
        long n = storage.insert(slot, key, amount, simulate);
        if (!isVoiding || n >= amount) return n;
        boolean holds = storage.amountAt(slot) > 0 ? storage.rawKeyAt(slot) == key : storage.acceptsEmpty(slot, key);
        return holds ? amount : n;
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        return canCapOutput() ? storage.extract(slot, key, amount, simulate) : 0;
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        if (!canCapInput()) return 0;
        long n = storage.insert(key, amount, simulate);
        return isVoiding && n < amount && voidable(key) ? amount : n;
    }

    private boolean voidable(K key) {
        if (storage.indexOf(key) >= 0) return true;
        for (int i = 0, size = storage.size(); i < size; i++) {
            if (storage.amountAt(i) == 0 && storage.acceptsEmpty(i, key)) return true;
        }
        return false;
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        return canCapOutput() ? storage.extract(key, amount, simulate) : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var plain = readStorage();
        if (plain != null) plain.getAvailableStacks(out);
        else IKeyHandler.super.getAvailableStacks(out);
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        if (!extractableOnly || canCapOutput()) getAvailableStacks(out);
    }

    public NotifiableInventory<K> setAvailable(boolean available) {
        this.isAvailable = available;
        return this;
    }
}
