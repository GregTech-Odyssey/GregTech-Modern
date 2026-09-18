package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.Nullable;

public interface IAutoOutputItem extends IMachineFeature, IConfigCopyable {

    boolean isAutoOutputItems();

    void setAutoOutputItems(boolean allow);

    boolean isAllowInputFromOutputSideItems();

    void setAllowInputFromOutputSideItems(boolean allow);

    @Nullable
    Direction getOutputFacingItems();

    void setOutputFacingItems(@Nullable Direction outputFacing);

    default boolean hasAutoOutputItem() {
        return true;
    }

    /**
     * Item auto output is fully described by this interface's own accessors, so the copy protocol is
     * implemented here once rather than in every machine. Types that combine this with another
     * {@link IConfigCopyable} source must override and chain both (see {@link IAutoOutputBoth}).
     */
    @Override
    default void writeConfigTo(CompoundTag tag) {
        ConfigCopySupport.writeAutoOutputItem(tag, this);
    }

    @Override
    default void readConfigFrom(CompoundTag tag) {
        ConfigCopySupport.readAutoOutputItem(tag, this);
    }
}
