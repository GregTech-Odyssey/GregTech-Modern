package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreVeinUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.WeightedEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.TargetBlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DikeVeinGenerator extends VeinGenerator {

    public static final Codec<DikeVeinGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.list(DikeBlockDefinition.CODEC).fieldOf("blocks").forGetter(it -> it.blocks), Codec.INT.fieldOf("min_y").forGetter(it -> it.minYLevel), Codec.INT.fieldOf("max_y").forGetter(it -> it.maxYLevel)).apply(instance, DikeVeinGenerator::new));
    public List<DikeBlockDefinition> blocks;
    public int minYLevel;
    public int maxYLevel;

    public DikeVeinGenerator(GTOreDefinition entry) {
        super(entry);
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        List<VeinEntry> entries = new ArrayList<>(this.blocks.size());
        for (var def : this.blocks) {
            VeinGenerator.mapTarget(def.block, def.weight).forEach(entries::add);
        }
        return entries;
    }

    @Override
    public Long2ObjectMap<OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry, BlockPos origin) {
        Long2ObjectMap<OreBlockPlacer> generatedBlocks = new Long2ObjectOpenHashMap<>();
        NormalNoise normalNoise = createNoise(level.getSeed());
        ChunkPos chunkPos = new ChunkPos(origin);
        int xPos = chunkPos.getMinBlockX() + level.getRandom().nextInt(16);
        int zPos = chunkPos.getMinBlockZ() + level.getRandom().nextInt(16);
        trace(random, entry, normalNoise, xPos, zPos, null, (pos, randomSeed) -> generatedBlocks
                .put(pos.asLong(), (access, section) -> placeBlock(access, section, randomSeed, pos, entry)));
        return generatedBlocks;
    }

    @Override
    public boolean sample(GTOreDefinition entry, RandomSource random, BlockPos origin, BoundingBox area, SampleSink sink) {
        trace(random, entry, createNoise(random.nextLong()), origin.getX(), origin.getZ(), area, new DikeSink() {

            @Override
            public void place(BlockPos pos, long randomSeed) {
                var block = chooseBlock(new XoroshiroRandomSource(randomSeed), pos.getY());
                sink.accept(pos, block == null ? null : block.block());
            }

            @Override
            public void skip(BlockPos pos) {
                sink.accept(pos, null);
            }
        });
        return true;
    }

    @FunctionalInterface
    private interface DikeSink {

        void place(BlockPos pos, long randomSeed);

        default void skip(BlockPos pos) {}
    }

    private static NormalNoise createNoise(long seed) {
        return NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(seed)), -2, 4.0);
    }

    private void trace(RandomSource random, GTOreDefinition entry, NormalNoise normalNoise, int xPos, int zPos,
                       @Nullable BoundingBox area, DikeSink sink) {
        float density = entry.density();
        int size = entry.clusterSize().sample(random);
        int radius = Mth.ceil(size / 2.0F);
        int yBottom = clipFrom(area, Direction.Axis.Y, 0, minYLevel), yTop = clipTo(area, Direction.Axis.Y, 0, maxYLevel);
        int xFrom = clipFrom(area, Direction.Axis.X, xPos, -radius), xTo = clipTo(area, Direction.Axis.X, xPos, radius);
        int zFrom = clipFrom(area, Direction.Axis.Z, zPos, -radius), zTo = clipTo(area, Direction.Axis.Z, zPos, radius);
        for (int dY = yBottom; dY <= yTop; dY++) {
            for (int dX = xFrom; dX <= xTo; dX++) {
                for (int dZ = zFrom; dZ <= zTo; dZ++) {
                    float dist = (dX * dX) + (dZ * dZ);
                    if (dist > radius * 2) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(xPos + dX, dY, zPos + dZ);
                    if (normalNoise.getValue(dX, dY, dZ) >= 0.5 && random.nextFloat() <= density) {
                        final var randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order
                        sink.place(pos, randomSeed);
                    } else {
                        sink.skip(pos);
                    }
                }
            }
        }
    }

    @Nullable
    private DikeBlockDefinition chooseBlock(RandomSource random, int y) {
        DikeBlockDefinition blockDefinition = GTUtil.getRandomItem(random, blocks);
        return blockDefinition != null && y >= blockDefinition.minY() && y <= blockDefinition.maxY() ? blockDefinition : null;
    }

    private void placeBlock(BulkSectionAccess access, LevelChunkSection section, long randomSeed, BlockPos pos, GTOreDefinition entry) {
        var random = new XoroshiroRandomSource(randomSeed);
        DikeBlockDefinition blockDefinition = chooseBlock(random, pos.getY());
        if (blockDefinition != null) {
            OreVeinUtil.placeOre(blockDefinition.block, access.getBlockState(pos), access, section, random, pos, entry);
        }
    }

    @Override
    public VeinGenerator build() {
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new DikeVeinGenerator(new ArrayList<>(blocks), minYLevel, maxYLevel);
    }

    @Override
    public Codec<? extends VeinGenerator> codec() {
        return CODEC;
    }

    public DikeVeinGenerator withBlock(Material block, int weight, int minY, int maxY) {
        return this.withBlock(new DikeBlockDefinition(block, weight, minY, maxY));
    }

    public DikeVeinGenerator withBlock(BlockState blockState, int weight, int minY, int maxY) {
        TargetBlockState target = OreConfiguration.target(AlwaysTrueTest.INSTANCE, blockState);
        return this.withBlock(new DikeBlockDefinition(List.of(target), weight, minY, maxY));
    }

    public DikeVeinGenerator withBlock(DikeBlockDefinition block) {
        if (this.blocks == null) this.blocks = new ArrayList<>();
        this.blocks.add(block);
        return this;
    }

    public record DikeBlockDefinition(Either<List<TargetBlockState>, Material> block, int weight, int minY, int maxY) implements WeightedEntry {

        public static final Codec<DikeBlockDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.either(TargetBlockState.CODEC.listOf(), GTCEuAPI.materialManager.codec()).fieldOf("block").forGetter(x -> x.block), Codec.INT.fieldOf("weight").forGetter(x -> x.weight), Codec.INT.fieldOf("min_y").orElse(320).forGetter(x -> x.minY), Codec.INT.fieldOf("max_y").orElse(-64).forGetter(x -> x.maxY)).apply(instance, DikeBlockDefinition::new));

        public DikeBlockDefinition(Material block, int weight, int minY, int maxY) {
            this(Either.right(block), weight, minY, maxY);
        }

        public DikeBlockDefinition(List<TargetBlockState> block, int weight, int minY, int maxY) {
            this(Either.left(block), weight, minY, maxY);
        }
    }

    public DikeVeinGenerator(final List<DikeBlockDefinition> blocks, final int minYLevel, final int maxYLevel) {
        this.blocks = blocks;
        this.minYLevel = minYLevel;
        this.maxYLevel = maxYLevel;
    }

    /**
     * @return {@code this}.
     */
    public DikeVeinGenerator minYLevel(final int minYLevel) {
        this.minYLevel = minYLevel;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public DikeVeinGenerator maxYLevel(final int maxYLevel) {
        this.maxYLevel = maxYLevel;
        return this;
    }
}
