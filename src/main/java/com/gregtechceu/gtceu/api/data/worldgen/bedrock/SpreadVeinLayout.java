package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.util.RandomSource;

/**
 * Veins start on origin chunks spaced by the configured distance and cover a square of their size; a chunk covered by
 * several veins takes the nearest origin (Manhattan distance, ties to the smaller x then z), and its yield falls off
 * with that distance.
 */
final class SpreadVeinLayout implements BedrockVeinLayout<BedrockOreDefinition> {

    private final int groupSize;
    private int reach = -1;

    SpreadVeinLayout(int groupSize) {
        this.groupSize = groupSize;
    }

    @Override
    public void generate(BedrockVeinSavedData<BedrockOreDefinition> data, int chunkX, int chunkZ) {
        int distance = Math.max(1, ConfigHolder.INSTANCE.worldgen.oreVeins.bedrockOreDistance);
        int reach = reach(data);
        BedrockOreDefinition best = null;
        int bestX = 0, bestZ = 0, bestDistance = Integer.MAX_VALUE;
        for (int ox = Math.floorDiv(chunkX - reach + distance - 1, distance) * distance; ox <= chunkX + reach; ox += distance) {
            for (int oz = Math.floorDiv(chunkZ - reach + distance - 1, distance) * distance; oz <= chunkZ + reach; oz += distance) {
                int dx = Math.abs(chunkX - ox), dz = Math.abs(chunkZ - oz);
                if (dx + dz >= bestDistance) continue;
                var definition = data.origin(ox, oz, groupSize);
                if (definition == null) continue;
                int radius = definition.size() / 2;
                if (dx > radius || dz > radius) continue;
                best = definition;
                bestX = ox;
                bestZ = oz;
                bestDistance = dx + dz;
            }
        }
        if (best != null) data.put(chunkX, chunkZ, best, yieldAt(data, best, bestX, bestZ, chunkX, chunkZ));
    }

    private int reach(BedrockVeinSavedData<BedrockOreDefinition> data) {
        if (reach < 0) {
            int max = 0;
            for (var definition : data.kind().registry()) max = Math.max(max, definition.size() / 2);
            reach = max;
        }
        return reach;
    }

    private static int yieldAt(BedrockVeinSavedData<BedrockOreDefinition> data, BedrockOreDefinition definition, int originX, int originZ, int x, int z) {
        var yield = definition.yield();
        float distance = Math.abs(originX - x) + Math.abs(originZ - z);
        distance = distance == 0 ? 1 : distance;
        distance = (float) Math.pow(distance, 2);
        var random = RandomSource.create(31L * 31 * x + z * 31L + Long.hashCode(data.seed()));
        int maximumYield;
        if ((yield.getMaxValue() - yield.getMinValue()) / distance <= 0) {
            maximumYield = yield.getMinValue();
        } else {
            maximumYield = (int) (yield.sample(random) / distance);
            maximumYield = Math.max(maximumYield, yield.getMinValue());
        }
        return Math.min(maximumYield, yield.getMaxValue());
    }
}
