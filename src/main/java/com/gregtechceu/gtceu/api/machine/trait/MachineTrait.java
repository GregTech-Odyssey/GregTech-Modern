package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * represents an abstract capability held by machine. Such as item, fluid, energy, etc.
 * All trait should be added while MetaMachine is creating. you cannot modify it on the fly。
 */
public abstract class MachineTrait implements IEnhancedManaged {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    protected final MetaMachine machine;
    protected Predicate<@Nullable Direction> capabilityValidator;

    public MachineTrait(MetaMachine machine) {
        this.machine = machine;
        this.capabilityValidator = GTUtil.FAVORABLE;
        machine.attachTraits(this);
    }

    public boolean hasCapability(@Nullable Direction side) {
        return capabilityValidator.test(side);
    }

    @Override
    public void onChanged() {
        machine.onChanged();
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
    public void scheduleRenderUpdate() {
        machine.scheduleRenderUpdate();
    }

    public FieldManagedStorage getSyncStorage() {
        return this.syncStorage;
    }

    public MetaMachine getMachine() {
        return this.machine;
    }

    public void setCapabilityValidator(final Predicate<@Nullable Direction> capabilityValidator) {
        this.capabilityValidator = capabilityValidator;
    }
}
