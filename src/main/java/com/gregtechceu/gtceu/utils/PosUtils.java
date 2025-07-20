package com.gregtechceu.gtceu.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class PosUtils {

    public static long getChunkLong(long pos) {
        return ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
    }

    public static long getChunkLong(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
