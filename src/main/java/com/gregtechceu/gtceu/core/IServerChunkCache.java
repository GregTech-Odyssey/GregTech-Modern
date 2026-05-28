package com.gregtechceu.gtceu.core;

import net.minecraft.world.level.chunk.LevelChunk;

public interface IServerChunkCache {

    LevelChunk gtceu$getCachedChunk(int pChunkX, int pChunkZ);
}
