package com.gregtechceu.gtceu.api.data.worldgen.ores;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Responsible for placing one generated block. The map key supplies its position.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@FunctionalInterface
public interface OreBlockPlacer {

    /** Places the block whose position is the map key. */
    void placeBlock(BulkSectionAccess access, LevelChunkSection section);
}
