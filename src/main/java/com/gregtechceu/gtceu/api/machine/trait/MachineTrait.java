package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * represents an abstract capability held by machine. Such as item, fluid, energy, etc.
 * All trait should be added while MetaMachine is creating. you cannot modify it on the fly。
 */
public abstract class MachineTrait implements IFieldDataHolder {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);

    @Getter
    protected final MetaMachine machine;

    public MachineTrait(MetaMachine machine) {
        this.machine = machine;
        machine.attachTraits(this);
    }

    public void onMachineRotated(Direction oldFacing, Direction newFacing) {}

    public void onMachineLoad() {}

    public void onMachineUnLoad() {}

    /**
     * Use for data not able to be saved with the SyncData system, like optional mod compatiblity in internal machines.
     *
     * @param tag     the CompoundTag to load data from
     * @param forDrop if the save is done for dropping the machine as an item.
     */
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {}

    public void loadCustomPersistedData(@NotNull CompoundTag tag) {}

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        machine.scheduleUpdate(side);
    }
}
