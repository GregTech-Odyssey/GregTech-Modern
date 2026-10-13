package com.gregtechceu.gtceu.client.renderer;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

public interface IRenderBoundingBox {

    @Nullable
    AABB getRenderBoundingBox(BlockEntity blockEntity);
}
