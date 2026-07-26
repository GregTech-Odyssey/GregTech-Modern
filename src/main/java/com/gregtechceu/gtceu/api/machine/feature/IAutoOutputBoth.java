package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.nbt.CompoundTag;

public interface IAutoOutputBoth extends IAutoOutputItem, IAutoOutputFluid {

    // Both parents supply a copy-protocol default, so the conflict has to be resolved here by
    // contributing each side's config in turn.
    @Override
    default void writeConfigTo(CompoundTag tag) {
        ConfigCopySupport.writeAutoOutputItem(tag, this);
        ConfigCopySupport.writeAutoOutputFluid(tag, this);
    }

    @Override
    default void readConfigFrom(CompoundTag tag) {
        ConfigCopySupport.readAutoOutputItem(tag, this);
        ConfigCopySupport.readAutoOutputFluid(tag, this);
    }
}
