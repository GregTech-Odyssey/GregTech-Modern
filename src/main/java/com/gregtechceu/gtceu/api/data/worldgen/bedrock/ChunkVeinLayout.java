package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;

import net.minecraft.util.RandomSource;

/**
 * Every chunk is its own vein; chunks of the same group pick from the same random query.
 */
final class ChunkVeinLayout implements BedrockVeinLayout<BedrockFluidDefinition> {

    private final int groupSize;

    ChunkVeinLayout(int groupSize) {
        this.groupSize = groupSize;
    }

    @Override
    public void generate(BedrockVeinSavedData<BedrockFluidDefinition> data, int chunkX, int chunkZ) {
        var definition = data.selectDefinition(Math.floorDiv(chunkX, groupSize), Math.floorDiv(chunkZ, groupSize), chunkX, chunkZ);
        if (definition == null) return;
        var random = RandomSource.create(31L * 31 * chunkX + chunkZ * 31L + Long.hashCode(data.seed()));
        int min = definition.minimumYield();
        int max = definition.maximumYield();
        int yield = max - min <= 0 ? min : random.nextInt(max - min) + min;
        data.put(chunkX, chunkZ, definition, Math.min(yield, max));
    }
}
