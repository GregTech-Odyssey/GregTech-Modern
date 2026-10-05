package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.KeyTypedStorage;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 机器同一面的物品与流体存储组合成的 MEStorage：按 key 类型路由到该面已带 IO 限制与覆盖板过滤的 GT 存储。
 */
public final class MachineKeyStorage implements KeyTypedStorage {

    private final IKeyHandler<AEItemKey> items;
    private final IKeyHandler<AEFluidKey> fluids;
    private final Component description;

    public MachineKeyStorage(IKeyHandler<AEItemKey> items, IKeyHandler<AEFluidKey> fluids, Component description) {
        this.items = items;
        this.fluids = fluids;
        this.description = description;
    }

    public IKeyHandler<AEItemKey> items() {
        return items;
    }

    public IKeyHandler<AEFluidKey> fluids() {
        return fluids;
    }

    @Override
    public boolean supportsKeyType(AEKeyType type) {
        return type == AEKeyTypes.ITEMS || type == AEKeyTypes.FLUIDS;
    }

    @Override
    public @Nullable MEStorage forKeyType(AEKeyType type) {
        return type == AEKeyTypes.ITEMS ? items : type == AEKeyTypes.FLUIDS ? fluids : null;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        if (what instanceof AEItemKey key) return items.insert(key, amount, mode == Actionable.SIMULATE);
        if (what instanceof AEFluidKey key) return fluids.insert(key, amount, mode == Actionable.SIMULATE);
        return 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        if (what instanceof AEItemKey key) return items.extract(key, amount, mode == Actionable.SIMULATE);
        if (what instanceof AEFluidKey key) return fluids.extract(key, amount, mode == Actionable.SIMULATE);
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        items.getAvailableStacks(out);
        fluids.getAvailableStacks(out);
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        items.getAvailableStacks(out, extractableOnly);
        fluids.getAvailableStacks(out, extractableOnly);
    }

    @Override
    public boolean containsAny(Set<AEKey> primaryKeys) {
        return items.containsAny(primaryKeys) || fluids.containsAny(primaryKeys);
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty() && fluids.isEmpty();
    }

    @Override
    public Component getDescription() {
        return description;
    }
}
