package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import lombok.Getter;

import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Holds a vein's section placers, grouped by chunk.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GeneratedVein {

    @Getter
    private final ChunkPos origin;
    @Getter
    private final IWorldGenLayer layer;
    private final Long2ObjectOpenHashMap<Long2ObjectMap<OreBlockPlacer>> generatedOres;

    /**
     * @param origin         The vein's origin chunk (NOT its actual center, which may be outside the origin chunk)
     * @param oresByPosition Block-position keys for legacy placers, or section-position keys for
     *                       {@link SectionOreBlockPlacer} values. Entries must use one key convention.
     */
    public GeneratedVein(ChunkPos origin, IWorldGenLayer layer, Long2ObjectMap<OreBlockPlacer> oresByPosition) {
        this.origin = origin;
        this.layer = layer;
        this.generatedOres = new Long2ObjectOpenHashMap<>();
        for (var entry : Long2ObjectMaps.fastIterable(oresByPosition)) {
            long pos = entry.getLongKey();
            boolean sectionKey = entry.getValue() instanceof SectionOreBlockPlacer;
            long chunkPos = sectionKey ? ChunkPos.asLong(SectionPos.x(pos), SectionPos.z(pos)) : ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
            generatedOres.computeIfAbsent(chunkPos, key -> new Long2ObjectOpenHashMap<>())
                    .put(pos, entry.getValue());
        }
    }

    /** Retrieve the legacy block-position keyed placers for the specified chunk. */
    public Long2ObjectMap<OreBlockPlacer> consumeOres(long chunk) {
        Long2ObjectMap<OreBlockPlacer> ores = generatedOres.getOrDefault(chunk, Long2ObjectMaps.emptyMap());
        return ores.isEmpty() || !(ores.values().iterator().next() instanceof SectionOreBlockPlacer) ? ores : SectionOreBlockPlacer.toBlockPositionMap(ores);
    }

    /** Retrieve the internal section placers for optimized placement. */
    public Long2ObjectMap<OreBlockPlacer> consumeSectionOres(long chunk) {
        return this.generatedOres.getOrDefault(chunk, Long2ObjectMaps.emptyMap());
    }

    public LongSet getGeneratedChunks() {
        return generatedOres.keySet();
    }

    @Override
    public String toString() {
        return "GeneratedVein[origin=" + origin + ", chunks={" + generatedOres.keySet().longStream().mapToObj(ChunkPos::new).map(ChunkPos::toString).collect(Collectors.joining(", ")) + "}]";
    }
}
