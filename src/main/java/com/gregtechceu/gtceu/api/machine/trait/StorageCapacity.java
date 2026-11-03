package com.gregtechceu.gtceu.api.machine.trait;

import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.Nullable;

/** Reads the user-selected capacity limit without changing a storage item's base capacity. */
public final class StorageCapacity {

    public static final String LIMIT_KEY = "storageCapacityLimit";

    private StorageCapacity() {}

    public static long limit(@Nullable CompoundTag tag, long maximum) {
        if (tag == null) return maximum;
        if (tag.contains(LIMIT_KEY)) return Math.clamp(tag.getLong(LIMIT_KEY), 1, maximum);
        // Older compartment items stored their user-selected limit under this key.
        return tag.contains("compartmentLimit") ? Math.clamp(tag.getLong("compartmentLimit"), 1, maximum) : maximum;
    }
}
