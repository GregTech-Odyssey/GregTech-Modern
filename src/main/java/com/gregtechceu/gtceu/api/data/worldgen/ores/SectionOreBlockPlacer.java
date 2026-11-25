package com.gregtechceu.gtceu.api.data.worldgen.ores;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Batches generated ore placements for a single 16x16x16 section.
 * Populated single-threaded during vein generation, then safely read-only during chunk placement.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SectionOreBlockPlacer {

    @FunctionalInterface
    public interface BlockPlacer {

        /** Places one precomputed block using the current block state already read from {@code section}. */
        void place(BulkSectionAccess access, LevelChunkSection section, BlockPos.MutableBlockPos pos,
                   BlockState current, long randomSeed, long extraValue, @Nullable Object extraObject);
    }

    private final BlockPlacer blockPlacer;
    private final LongArrayList positions = new LongArrayList();
    private final LongArrayList randomSeeds = new LongArrayList();
    @Nullable
    private LongArrayList extraValues;
    @Nullable
    private ObjectArrayList<Object> extraObjects;

    private SectionOreBlockPlacer(BlockPlacer blockPlacer) {
        this.blockPlacer = blockPlacer;
    }

    /** Creates an empty section batch backed by {@code blockPlacer}. */
    public static SectionOreBlockPlacer create(BlockPlacer blockPlacer) {
        return new SectionOreBlockPlacer(blockPlacer);
    }

    /** Returns the section batch containing the supplied packed block position. */
    public static SectionOreBlockPlacer getOrCreate(Long2ObjectMap<SectionOreBlockPlacer> sections, long blockPos,
                                                    BlockPlacer blockPlacer) {
        long sectionPos = SectionPos.asLong(BlockPos.getX(blockPos) >> 4, BlockPos.getY(blockPos) >> 4,
                BlockPos.getZ(blockPos) >> 4);
        SectionOreBlockPlacer existing = sections.get(sectionPos);
        if (existing != null) return existing;

        SectionOreBlockPlacer created = new SectionOreBlockPlacer(blockPlacer);
        sections.put(sectionPos, created);
        return created;
    }

    /** Adds a position with its placement seed. */
    public void add(long blockPos, long randomSeed) {
        positions.add(blockPos);
        randomSeeds.add(randomSeed);
        if (extraValues != null) extraValues.add(0L);
        if (extraObjects != null) extraObjects.add(null);
    }

    /** Adds a position with a primitive generator-specific value. */
    public void add(long blockPos, long randomSeed, long extraValue) {
        int prevSize = positions.size();
        positions.add(blockPos);
        randomSeeds.add(randomSeed);
        if (extraValues == null) {
            extraValues = new LongArrayList(prevSize + 1);
            if (prevSize > 0) extraValues.size(prevSize);
        }
        extraValues.add(extraValue);
        if (extraObjects != null) extraObjects.add(null);
    }

    /** Adds a position with an object generator-specific value. */
    public void add(long blockPos, long randomSeed, @Nullable Object extraObject) {
        int prevSize = positions.size();
        positions.add(blockPos);
        randomSeeds.add(randomSeed);
        if (extraValues != null) extraValues.add(0L);
        if (extraObjects == null) {
            extraObjects = new ObjectArrayList<>(prevSize + 1);
            if (prevSize > 0) extraObjects.size(prevSize);
        }
        extraObjects.add(extraObject);
    }

    /** Checks each position against the layer target, then places accepted blocks. */
    public void placeSectionBlocks(BulkSectionAccess access, LevelChunkSection section,
                                   RuleTest layerTarget, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0, size = positions.size(); i < size; i++) {
            long blockPos = positions.getLong(i);
            int x = BlockPos.getX(blockPos);
            int y = BlockPos.getY(blockPos);
            int z = BlockPos.getZ(blockPos);
            BlockState current = section.getBlockState(x & 15, y & 15, z & 15);
            if (!layerTarget.test(current, random)) continue;
            pos.set(x, y, z);
            blockPlacer.place(access, section, pos, current, randomSeeds.getLong(i),
                    extraValues == null ? 0L : extraValues.getLong(i),
                    extraObjects == null ? null : extraObjects.get(i));
        }
    }
}
