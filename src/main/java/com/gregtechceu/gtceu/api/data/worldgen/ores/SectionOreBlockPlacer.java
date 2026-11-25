package com.gregtechceu.gtceu.api.data.worldgen.ores;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

    private static final int DEFAULT_INITIAL_CAPACITY = 32;

    @FunctionalInterface
    public interface BlockPlacer {

        /** Places one precomputed block using the current block state already read from {@code section}. */
        void place(BulkSectionAccess access, LevelChunkSection section, BlockPos.MutableBlockPos pos,
                   BlockState current, long randomSeed, long extraValue, @Nullable Object extraObject);
    }

    /** Single-threaded section batch collector with a 1-slot cache to avoid per-block map lookups. */
    public static final class Collector {

        private final Long2ObjectOpenHashMap<SectionOreBlockPlacer> sections;
        private final BlockPlacer blockPlacer;
        private long lastSectionPos;
        @Nullable
        private SectionOreBlockPlacer lastPlacer;

        public Collector(Long2ObjectOpenHashMap<SectionOreBlockPlacer> sections, BlockPlacer blockPlacer) {
            this.sections = sections;
            this.blockPlacer = blockPlacer;
        }

        public Collector(BlockPlacer blockPlacer) {
            this(new Long2ObjectOpenHashMap<>(), blockPlacer);
        }

        public Long2ObjectOpenHashMap<SectionOreBlockPlacer> sections() {
            return sections;
        }

        private SectionOreBlockPlacer batchFor(long blockPos) {
            long sectionPos = SectionPos.asLong(BlockPos.getX(blockPos) >> 4, BlockPos.getY(blockPos) >> 4,
                    BlockPos.getZ(blockPos) >> 4);
            SectionOreBlockPlacer placer = lastPlacer;
            if (placer != null && sectionPos == lastSectionPos) return placer;
            placer = sections.get(sectionPos);
            if (placer == null) {
                placer = new SectionOreBlockPlacer(blockPlacer);
                sections.put(sectionPos, placer);
            }
            lastSectionPos = sectionPos;
            lastPlacer = placer;
            return placer;
        }

        public void add(long blockPos, long randomSeed) {
            batchFor(blockPos).add(blockPos, randomSeed);
        }

        public void add(long blockPos, long randomSeed, long extraValue) {
            batchFor(blockPos).add(blockPos, randomSeed, extraValue);
        }

        public void add(long blockPos, long randomSeed, @Nullable Object extraObject) {
            batchFor(blockPos).add(blockPos, randomSeed, extraObject);
        }
    }

    private final BlockPlacer blockPlacer;
    private final LongArrayList positions = new LongArrayList(DEFAULT_INITIAL_CAPACITY);
    private final LongArrayList randomSeeds = new LongArrayList(DEFAULT_INITIAL_CAPACITY);
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
    public static SectionOreBlockPlacer getOrCreate(Long2ObjectOpenHashMap<SectionOreBlockPlacer> sections,
                                                    long blockPos, BlockPlacer blockPlacer) {
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
            extraValues = new LongArrayList(Math.max(DEFAULT_INITIAL_CAPACITY, positions.elements().length));
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
            extraObjects = new ObjectArrayList<>(Math.max(DEFAULT_INITIAL_CAPACITY, positions.elements().length));
            if (prevSize > 0) extraObjects.size(prevSize);
        }
        extraObjects.add(extraObject);
    }

    /** Checks each position against the layer target, then places accepted blocks. */
    public void placeSectionBlocks(BulkSectionAccess access, LevelChunkSection section,
                                   RuleTest layerTarget, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long[] posArr = positions.elements();
        long[] seedArr = randomSeeds.elements();
        long[] valArr = extraValues == null ? null : extraValues.elements();
        Object[] objArr = extraObjects == null ? null : extraObjects.elements();
        for (int i = 0, size = positions.size(); i < size; i++) {
            long blockPos = posArr[i];
            int x = BlockPos.getX(blockPos);
            int y = BlockPos.getY(blockPos);
            int z = BlockPos.getZ(blockPos);
            BlockState current = section.getBlockState(x & 15, y & 15, z & 15);
            if (!layerTarget.test(current, random)) continue;
            pos.set(x, y, z);
            blockPlacer.place(access, section, pos, current, seedArr[i],
                    valArr == null ? 0L : valArr[i],
                    objArr == null ? null : objArr[i]);
        }
    }
}
