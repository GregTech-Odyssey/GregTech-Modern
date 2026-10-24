package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.NotNull;

/**
 * Implemented by blocks which push their contents into neighbouring blocks on their own.
 * <p>
 * Pipes use this to skip pulling from a neighbour which is already pushing into them, so the same contents are not
 * moved twice (once by the neighbour and once by the pipe).
 */
public interface IAutoOutput {

    /**
     * @param side the side of this block which faces the neighbour in question
     * @return whether this block pushes its items to that side by itself
     */
    default boolean isAutoOutputtingItems(@NotNull Direction side) {
        return false;
    }

    /**
     * @param side the side of this block which faces the neighbour in question
     * @return whether this block pushes its fluids to that side by itself
     */
    default boolean isAutoOutputtingFluids(@NotNull Direction side) {
        return false;
    }
}
