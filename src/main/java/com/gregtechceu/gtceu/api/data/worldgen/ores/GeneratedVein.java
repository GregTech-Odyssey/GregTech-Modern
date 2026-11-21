package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;

import net.minecraft.MethodsReturnNonnullByDefault;
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
    private final Long2ObjectOpenHashMap<Long2ObjectMap<SectionOreBlockPlacer>> generatedOres;

    /**
     * @param origin        The vein's origin chunk (NOT its actual center, which may be outside the origin chunk)
     * @param oresBySection Section batches keyed by packed section positions.
     */
    public GeneratedVein(ChunkPos origin, IWorldGenLayer layer, Long2ObjectMap<SectionOreBlockPlacer> oresBySection) {
        this.origin = origin;
        this.layer = layer;
        this.generatedOres = new Long2ObjectOpenHashMap<>();
        for (var entry : Long2ObjectMaps.fastIterable(oresBySection)) {
            long pos = entry.getLongKey();
            long chunkPos = ChunkPos.asLong(SectionPos.x(pos), SectionPos.z(pos));
            generatedOres.computeIfAbsent(chunkPos, key -> new Long2ObjectOpenHashMap<>())
                    .put(pos, entry.getValue());
        }
    }

    /** Retrieves section batches for the specified chunk without copying them. */
    public Long2ObjectMap<SectionOreBlockPlacer> consumeOres(long chunk) {
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
