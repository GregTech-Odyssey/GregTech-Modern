package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageAccess;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

public class InventoryProxyTrait<K extends AEKey> extends MachineTrait implements IKeyHandler<K>, ICapabilityTrait {

    @Getter
    public final IO capabilityIO;
    private final AEKeyType type;
    @Nullable
    private IKeyHandler<K> proxy;
    @Setter
    @Getter
    @SuppressWarnings("unchecked")
    protected Predicate<@Nullable Direction> capabilityValidator = GTUtil.FAVORABLE;

    public InventoryProxyTrait(MetaMachine machine, AEKeyType type, IO capabilityIO) {
        super(machine);
        this.type = type;
        this.capabilityIO = capabilityIO;
    }

    public @Nullable IKeyHandler<K> getProxy() {
        return proxy;
    }

    public InventoryProxyTrait<K> setProxy(@Nullable IKeyHandler<K> proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public AEKeyType keyType() {
        return type;
    }

    @Override
    public IKeyHandler<K> unrestricted() {
        var p = proxy;
        return p == null ? this : p.unrestricted();
    }

    @Override
    public int size() {
        var p = proxy;
        return p == null ? 0 : p.size();
    }

    @Override
    public @Nullable K keyAt(int slot) {
        var p = proxy;
        return p == null ? null : p.keyAt(slot);
    }

    @Override
    public long amountAt(int slot) {
        var p = proxy;
        return p == null ? 0 : p.amountAt(slot);
    }

    @Override
    public long slotLimit(int slot) {
        var p = proxy;
        return p == null ? 0 : p.slotLimit(slot);
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        var p = proxy;
        return p != null && canCapInput() ? p.insert(slot, key, amount, simulate) : 0;
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        var p = proxy;
        return p != null && canCapOutput() ? p.extract(slot, key, amount, simulate) : 0;
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        var p = proxy;
        return p != null && canCapInput() ? p.insert(key, amount, simulate) : 0;
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        var p = proxy;
        return p != null && canCapOutput() ? p.extract(key, amount, simulate) : 0;
    }

    @Override
    public boolean isEmpty() {
        var p = proxy;
        return p == null || p.isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var p = proxy;
        if (p != null) p.getAvailableStacks(out);
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        var p = proxy;
        if (p != null && (!extractableOnly || canCapOutput())) p.getAvailableStacks(out, extractableOnly);
    }

    @Override
    public boolean containsAny(Set<AEKey> primaryKeys) {
        var p = proxy;
        return p != null && p.containsAny(primaryKeys);
    }

    @Override
    public long count(K key) {
        var p = proxy;
        return p == null ? 0 : p.count(key);
    }

    @Override
    public Component getDescription() {
        var p = proxy;
        return p == null ? type.getDescription() : p.getDescription();
    }

    @SuppressWarnings("unchecked")
    public void exportToNearby(Direction... facings) {
        var p = proxy;
        if (p == null || p.isEmpty()) return;
        var m = getMachine();
        var level = m.getLevel();
        var pos = m.getPos();
        var cache = m.holder.blockEntityDirectionCache;
        for (Direction facing : facings) {
            var target = cache.getAdjacentKeyHandler(level, pos, facing, type, StorageAccess.INSERT);
            if (target != null) KeyTransfer.transfer(p, (IKeyHandler<K>) target, Long.MAX_VALUE, m.getKeyCapFilter(facing, IO.OUT, type));
        }
    }
}
