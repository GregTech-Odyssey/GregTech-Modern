package com.gregtechceu.gtceu.api.block;

import net.minecraft.util.StringRepresentable;

public interface IFilterType extends StringRepresentable {

    /**
     * @return The cleanroom type of this filter.
     */
    int getCleanroomTier();
}
