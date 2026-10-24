package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IAutoOutputFluid extends IAutoOutput, IMachineFeature {

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

    @Override
    default boolean isAutoOutputtingFluids(@NotNull Direction side) {
        return isAutoOutputFluids() && getOutputFacingFluids() == side;
    }
}
