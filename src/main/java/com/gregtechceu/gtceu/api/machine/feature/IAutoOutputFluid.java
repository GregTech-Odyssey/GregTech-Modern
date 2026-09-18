package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.Nullable;

public interface IAutoOutputFluid extends IMachineFeature, IConfigCopyable {

    boolean isAutoOutputFluids();

    void setAutoOutputFluids(boolean allow);

    boolean isAllowInputFromOutputSideFluids();

    void setAllowInputFromOutputSideFluids(boolean allow);

    @Nullable
    Direction getOutputFacingFluids();

    void setOutputFacingFluids(@Nullable Direction outputFacing);

    default boolean hasAutoOutputFluid() {
        return true;
    }

    /** @see IAutoOutputItem#writeConfigTo(CompoundTag) */
    @Override
    default void writeConfigTo(CompoundTag tag) {
        ConfigCopySupport.writeAutoOutputFluid(tag, this);
    }

    @Override
    default void readConfigFrom(CompoundTag tag) {
        ConfigCopySupport.readAutoOutputFluid(tag, this);
    }
}
