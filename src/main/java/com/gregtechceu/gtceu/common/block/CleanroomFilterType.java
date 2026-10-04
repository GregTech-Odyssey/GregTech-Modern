package com.gregtechceu.gtceu.common.block;

import com.gregtechceu.gtceu.api.block.IFilterType;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public enum CleanroomFilterType implements IFilterType {

    FILTER_CASING("filter_casing", 1),
    FILTER_CASING_STERILE("sterilizing_filter_casing", 2);

    private final String name;
    @Getter
    private final int cleanroomTier;

    CleanroomFilterType(String name, int cleanroomTier) {
        this.name = name;
        this.cleanroomTier = cleanroomTier;
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return this.name;
    }

    @NotNull
    @Override
    public String toString() {
        return getSerializedName();
    }
}
