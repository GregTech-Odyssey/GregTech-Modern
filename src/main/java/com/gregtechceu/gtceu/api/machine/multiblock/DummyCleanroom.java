package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;

import org.jetbrains.annotations.NotNull;

public final class DummyCleanroom implements ICleanroomProvider {

    private final int allowedTier;
    public static final DummyCleanroom ALL_TYPES = new DummyCleanroom(Integer.MAX_VALUE);

    /**
     * Create a Dummy Cleanroom that provides specific tiers
     * 
     * @param tier the tier to provide
     */
    @NotNull
    public static DummyCleanroom create(int tier) {
        return new DummyCleanroom(tier);
    }

    /**
     * Create a Dummy Cleanroom that provides all types
     */
    @NotNull
    public static DummyCleanroom createForAllTypes() {
        return new DummyCleanroom(Integer.MAX_VALUE);
    }

    private DummyCleanroom(int tier) {
        this.allowedTier = tier;
    }

    @Override
    public boolean isClean() {
        return true;
    }

    @Override
    public int getCleanroomTier() {
        return allowedTier;
    }
}
