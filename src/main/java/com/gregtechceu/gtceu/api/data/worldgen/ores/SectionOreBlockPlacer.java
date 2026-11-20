package com.gregtechceu.gtceu.api.data.worldgen.ores;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** Batches the placements for all generated blocks in one section. */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SectionOreBlockPlacer implements OreBlockPlacer {

    @FunctionalInterface
    public interface BlockPlacer {

        /** Places one precomputed block. */
        void place(BulkSectionAccess access, LevelChunkSection section, BlockPos.MutableBlockPos pos,
                   long randomSeed, long extraValue, @Nullable Object extraObject);
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

    @Override
    public void placeBlock(BulkSectionAccess access, LevelChunkSection section) {
        throw new UnsupportedOperationException("Section placer must be dispatched by OrePlacer");
    }

    /**
     * Returns the section batch containing the supplied block position.
     */
    public static SectionOreBlockPlacer getOrCreate(Long2ObjectMap<OreBlockPlacer> sections, long blockPos,
                                                    BlockPlacer blockPlacer) {
        long sectionPos = SectionPos.asLong(BlockPos.getX(blockPos) >> 4, BlockPos.getY(blockPos) >> 4,
                BlockPos.getZ(blockPos) >> 4);
        OreBlockPlacer existing = sections.get(sectionPos);
        if (existing != null) return (SectionOreBlockPlacer) existing;

        SectionOreBlockPlacer created = new SectionOreBlockPlacer(blockPlacer);
        sections.put(sectionPos, created);
        return created;
    }

    /** Adds a position with its placement seed. */
    public void add(long blockPos, long randomSeed) {
        positions.add(blockPos);
        randomSeeds.add(randomSeed);
    }

    /** Adds a position with a primitive generator-specific value. */
    public void add(long blockPos, long randomSeed, long extraValue) {
        positions.add(blockPos);
        randomSeeds.add(randomSeed);
        if (extraValues == null) extraValues = new LongArrayList();
        extraValues.add(extraValue);
    }

    /** Adds a position with an object generator-specific value. */
    public void add(long blockPos, long randomSeed, Object extraObject) {
        positions.add(blockPos);
        randomSeeds.add(randomSeed);
        if (extraObjects == null) extraObjects = new ObjectArrayList<>();
        extraObjects.add(extraObject);
    }

    public static Long2ObjectMap<OreBlockPlacer> toBlockPositionMap(
                                                                    Long2ObjectMap<OreBlockPlacer> sectionPlacers) {
        Long2ObjectOpenHashMap<OreBlockPlacer> blockPlacers = new Long2ObjectOpenHashMap<>();
        for (var entry : Long2ObjectMaps.fastIterable(sectionPlacers)) {
            if (entry.getValue() instanceof SectionOreBlockPlacer sectionPlacer) {
                sectionPlacer.copyBlockPlacersTo(blockPlacers);
            } else {
                blockPlacers.put(entry.getLongKey(), entry.getValue());
            }
        }
        return blockPlacers;
    }

    private void copyBlockPlacersTo(Long2ObjectMap<OreBlockPlacer> blockPlacers) {
        for (int i = 0; i < positions.size(); i++) {
            long blockPos = positions.getLong(i);
            long randomSeed = randomSeeds.getLong(i);
            long extraValue = extraValues == null ? 0L : extraValues.getLong(i);
            Object extraObject = extraObjects == null ? null : extraObjects.get(i);
            blockPlacers.put(blockPos, (access, section) -> {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                pos.set(BlockPos.getX(blockPos), BlockPos.getY(blockPos), BlockPos.getZ(blockPos));
                blockPlacer.place(access, section, pos, randomSeed, extraValue, extraObject);
            });
        }
    }

    /** Checks each position against the layer target, then places accepted blocks. */
    public void placeSectionBlocks(BulkSectionAccess access, LevelChunkSection section,
                                   RuleTest layerTarget, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < positions.size(); i++) {
            long blockPos = positions.getLong(i);
            pos.set(BlockPos.getX(blockPos), BlockPos.getY(blockPos), BlockPos.getZ(blockPos));
            int sectionX = SectionPos.sectionRelative(pos.getX());
            int sectionY = SectionPos.sectionRelative(pos.getY());
            int sectionZ = SectionPos.sectionRelative(pos.getZ());
            if (!layerTarget.test(section.getBlockState(sectionX, sectionY, sectionZ), random)) continue;
            blockPlacer.place(access, section, pos, randomSeeds.getLong(i),
                    extraValues == null ? 0L : extraValues.getLong(i),
                    extraObjects == null ? null : extraObjects.get(i));
        }
    }
}
