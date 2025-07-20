package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.ChunkPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Holds a vein's {@link OreBlockPlacer}s for each of its blocks, grouped by chunk.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GeneratedVein {

    private final ChunkPos origin;
    private final IWorldGenLayer layer;
    private final Long2ObjectOpenHashMap<Long2ObjectMap<OreBlockPlacer>> generatedOres;

    /**
     * @param origin         The vein's origin chunk (NOT its actual center, which may be outside the origin chunk)
     * @param oresByPosition The ore placers for each ore block position.<br>
     *                       Doesn't need to be ordered, grouping by chunks is done internally.
     */
    public GeneratedVein(ChunkPos origin, IWorldGenLayer layer, Long2ObjectMap<OreBlockPlacer> oresByPosition) {
        this.origin = origin;
        this.layer = layer;
        this.generatedOres = WorldGeneratorUtils.groupByChunks(oresByPosition);
    }

    /**
     * Retrieve the ore placers for all blocks inside the specified chunk.
     */
    public Long2ObjectMap<OreBlockPlacer> consumeOres(long chunk) {
        return this.generatedOres.getOrDefault(chunk, Long2ObjectMaps.emptyMap());
    }

    public LongSet getGeneratedChunks() {
        return generatedOres.keySet();
    }

    @Override
    public String toString() {
        return "GeneratedVein[origin=" + origin + ", chunks={" + generatedOres.keySet().longStream().mapToObj(ChunkPos::new).map(ChunkPos::toString).collect(Collectors.joining(", ")) + "}]";
    }

    public ChunkPos getOrigin() {
        return this.origin;
    }

    public IWorldGenLayer getLayer() {
        return this.layer;
    }
}
