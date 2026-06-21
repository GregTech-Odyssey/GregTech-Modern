package com.gregtechceu.gtceu.core;

import net.minecraft.resources.ResourceKey;

public interface IGTTagLoader {

    void gtceu$setRegistry(ResourceKey<?> registry);

    ResourceKey<?> gtceu$getRegistry();
}
