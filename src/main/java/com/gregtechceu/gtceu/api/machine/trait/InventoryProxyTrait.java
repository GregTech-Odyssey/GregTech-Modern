package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

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

    public void exportToNearby(Direction... facings) {
        var p = proxy;
        if (p == null || p.isEmpty()) return;
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var target = GTCapabilityHelper.getAdjacentKeyHandler(machine.holder.blockEntityDirectionCache, level, pos, facing, type);
            if (target == null) continue;
            @SuppressWarnings("unchecked")
            var to = (IKeyHandler<K>) target;
            KeyTransfer.transfer(p, to, Long.MAX_VALUE, getMachine().getKeyCapFilter(facing, IO.OUT, type));
        }
    }
}
