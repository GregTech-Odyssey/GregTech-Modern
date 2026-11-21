package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Responsible for placing ores of surrounding veins for the current chunk.
 * 
 * <p>
 * Surrounding veins are resolved from the {@link OreGenCache} and placed using each section's
 * {@link SectionOreBlockPlacer}.
 */
@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class OrePlacer {

    private final OreGenCache oreGenCache = new OreGenCache();

    /**
     * Place the contents of all surrounding ore veins in the current chunk.
     * 
     * <p>
     * Consumes the current chunk for all of the relevant veins, allowing the cache to unload the vein,
     * once all of its chunks have been generated.
     */
    public void placeOres(WorldGenLevel level, ChunkGenerator chunkGenerator, ChunkAccess chunk) {
        if (!ConfigHolder.INSTANCE.dev.doSuperflatOres && chunkGenerator instanceof FlatLevelSource) return;
        var posLong = chunk.getPos().toLong();
        var random = new XoroshiroRandomSource(level.getSeed() ^ posLong);
        var generatedVeins = oreGenCache.consumeChunkVeins(level, chunkGenerator, chunk);
        var generatedIndicators = oreGenCache.consumeChunkIndicators(level, chunkGenerator, chunk);
        try (BulkSectionAccess access = new BulkSectionAccess(level)) {
            generatedVeins.forEach(generatedVein -> placeVein(posLong, random, access, generatedVein, null));
            generatedIndicators.forEach(generatedIndicator -> placeIndicators(level, chunk, access, generatedIndicator));
        }
    }

    public void placeVein(long chunk, RandomSource random, BulkSectionAccess access, GeneratedVein generatedVein, @Nullable RuleTest targetOverride) {
        RuleTest layerTarget = targetOverride != null ? targetOverride : generatedVein.getLayer().getTarget();
        Long2ObjectMap<SectionOreBlockPlacer> placers = generatedVein.consumeOres(chunk);
        for (ObjectIterator<Long2ObjectMap.Entry<SectionOreBlockPlacer>> it = Long2ObjectMaps.fastIterator(placers); it.hasNext();) {
            var entry = it.next();
            SectionPos sectionPos = SectionPos.of(entry.getLongKey());
            LevelChunkSection section = access.getSection(sectionPos.origin());
            if (section != null) entry.getValue().placeSectionBlocks(access, section, layerTarget, random);
        }
    }

    private void placeIndicators(WorldGenLevel level, ChunkAccess chunk, BulkSectionAccess access, GeneratedIndicators generatedVein) {
        if (!ConfigHolder.INSTANCE.worldgen.oreVeins.oreIndicators) return;
        generatedVein.consumeIndicators(chunk.getPos()).forEach(placer -> placer.placeIndicators(access, level));
    }
}
