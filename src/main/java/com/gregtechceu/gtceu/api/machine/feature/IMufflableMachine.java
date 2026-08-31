package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.nbt.CompoundTag;

public interface IMufflableMachine extends IMachineFeature, IConfigCopyable {

    boolean isMuffled();

    void setMuffled(boolean isMuffled);

    /** @see IAutoOutputItem#writeConfigTo(CompoundTag) */
    @Override
    default void writeConfigTo(CompoundTag tag) {
        ConfigCopySupport.writeMuffled(tag, this);
    }

    @Override
    default void readConfigFrom(CompoundTag tag) {
        ConfigCopySupport.readMuffled(tag, this);
    }
}
