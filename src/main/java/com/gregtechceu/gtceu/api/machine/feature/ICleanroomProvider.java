package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Implement this interface in order to make a Machine into a block that provides a Cleanroom to other blocks
 */
public interface ICleanroomProvider {

    /**
     * @return max tier of cleanroom that this machine can provide
     */
    int getCleanroomTier();

    /**
     * @return whether the cleanroom is currently clean
     */
    boolean isClean();

    static MutableComponent getCleanroomTooltip(int tier) {
        return Component.translatable("gtceu.recipe.cleanroom." + tier);
    }
}
