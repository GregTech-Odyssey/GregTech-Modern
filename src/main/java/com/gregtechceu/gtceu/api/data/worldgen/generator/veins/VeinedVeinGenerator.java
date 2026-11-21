package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreVeinUtil;
import com.gregtechceu.gtceu.api.data.worldgen.ores.SectionOreBlockPlacer;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTFeatures;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.WeightedEntry;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.TargetBlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class VeinedVeinGenerator extends VeinGenerator {

    public static final Codec<Either<List<TargetBlockState>, Material>> BLOCK_ENTRY_CODEC = Codec.either(TargetBlockState.CODEC.listOf(), GTRegistries.MATERIALS.codec());
    public static final Codec<VeinedVeinGenerator> CODEC = RecordCodecBuilder.create(instance -> // ,
    instance.group(VeinBlockDefinition.CODEC.listOf().fieldOf("ore_blocks").forGetter(it -> it.oreBlocks), VeinBlockDefinition.CODEC.listOf().fieldOf("rare_blocks").forGetter(it -> it.rareBlocks), BlockState.CODEC.fieldOf("filler_block").orElse(Blocks.AIR.defaultBlockState()).forGetter(it -> it.fillerBlock), Codec.INT.fieldOf("min_y").forGetter(it -> it.minYLevel), Codec.INT.fieldOf("max_y").forGetter(it -> it.maxYLevel), Codec.FLOAT.fieldOf("veininess_threshold").orElse(0.4F).forGetter(it -> it.veininessThreshold), Codec.INT.fieldOf("edge_roundoff_begin").orElse(20).forGetter(it -> it.edgeRoundoffBegin), Codec.DOUBLE.fieldOf("max_edge_roundoff").orElse(0.2).forGetter(it -> it.maxEdgeRoundoff), Codec.FLOAT.fieldOf("min_richness").orElse(0.1F).forGetter(it -> it.minRichness), Codec.FLOAT.fieldOf("max_richness").orElse(0.3F).forGetter(it -> it.maxRichness), Codec.FLOAT.fieldOf("max_richness_threshold").orElse(0.6F).forGetter(it -> it.maxRichnessThreshold), Codec.FLOAT.fieldOf("rare_block_chance").orElse(0.02F).forGetter(it -> it.rareBlockChance)).apply(instance, VeinedVeinGenerator::new));
    public List<VeinBlockDefinition> oreBlocks = new ArrayList<>();
    public List<VeinBlockDefinition> rareBlocks = new ArrayList<>();
    public BlockState fillerBlock = Blocks.AIR.defaultBlockState();
    public int minYLevel;
    public int maxYLevel;
    public float veininessThreshold = 0.4F;
    public int edgeRoundoffBegin = 20;
    public double maxEdgeRoundoff = 0.2;
    public float minRichness = 0.1F;
    public float maxRichness = 0.3F;
    public float maxRichnessThreshold = 0.6F;
    public float rareBlockChance = 0.02F;

    public VeinedVeinGenerator(GTOreDefinition entry) {
        super(entry);
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        List<VeinEntry> entries = new ArrayList<>(oreBlocks.size() + rareBlocks.size());
        for (var def : oreBlocks) {
            VeinGenerator.mapTarget(def.block, def.weight).forEach(entries::add);
        }
        for (var def : rareBlocks) {
            VeinGenerator.mapTarget(def.block, def.weight).forEach(entries::add);
        }
        return entries;
    }

    @Override
    public Long2ObjectMap<SectionOreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry, BlockPos origin) {
        Long2ObjectMap<SectionOreBlockPlacer> generatedBlocks = new Long2ObjectOpenHashMap<>();
        Registry<? extends DensityFunction> densityFunctions = level.registryAccess().registry(Registries.DENSITY_FUNCTION).get();
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        Blender blender;
        if (level instanceof WorldGenRegion region) {
            blender = Blender.of(region);
        } else {
            blender = Blender.empty();
        }
        final Blender finalizedBlender = blender;
        DensityFunction veinToggle = mapToNoise(densityFunctions.get(GTFeatures.NEW_ORE_VEIN_TOGGLE), randomState);
        DensityFunction veinRidged = mapToNoise(densityFunctions.get(GTFeatures.NEW_ORE_VEIN_RIDGED), randomState);
        int originY = origin.getY();
        int minY = originY - this.minYLevel;
        int maxY = this.maxYLevel - originY;
        if (minY < 0 || maxY < 0) return generatedBlocks;

        int lowY = Math.min(maxY, minY);
        double edgeRoundoff = Mth.clampedMap(lowY, 0.0, edgeRoundoffBegin, -maxEdgeRoundoff, 0.0);
        int size = entry.clusterSize().sample(random);
        int radius = Mth.ceil(size / 2.0F);
        int randOffsetX = random.nextInt(16);
        int randOffsetY = random.nextInt(16);
        int randOffsetZ = random.nextInt(16);
        int minBlockX = origin.getX() - radius;
        int maxBlockX = origin.getX() + radius;
        int minBlockY = originY - radius;
        int maxBlockY = originY + radius;
        int minBlockZ = origin.getZ() - radius;
        int maxBlockZ = origin.getZ() + radius;
        int minSectionX = SectionPos.blockToSectionCoord(minBlockX);
        int maxSectionX = SectionPos.blockToSectionCoord(maxBlockX);
        int minSectionY = SectionPos.blockToSectionCoord(minBlockY);
        int maxSectionY = SectionPos.blockToSectionCoord(maxBlockY);
        int minSectionZ = SectionPos.blockToSectionCoord(minBlockZ);
        int maxSectionZ = SectionPos.blockToSectionCoord(maxBlockZ);
        SectionOreBlockPlacer.BlockPlacer blockPlacer = (access, section, pos, randomSeed, chance, ignored) -> placeBlock(access, section, randomSeed, entry, Double.longBitsToDouble(chance), rareBlocks, pos, oreBlocks);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    int sectionMinX = SectionPos.sectionToBlockCoord(sectionX);
                    int sectionMaxX = sectionMinX + 15;
                    int sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
                    int sectionMaxY = sectionMinY + 15;
                    int sectionMinZ = SectionPos.sectionToBlockCoord(sectionZ);
                    int sectionMaxZ = sectionMinZ + 15;
                    int fromX = Math.max(sectionMinX, minBlockX);
                    int toX = Math.min(sectionMaxX, maxBlockX);
                    int fromY = Math.max(sectionMinY, minBlockY);
                    int toY = Math.min(sectionMaxY, maxBlockY);
                    int fromZ = Math.max(sectionMinZ, minBlockZ);
                    int toZ = Math.min(sectionMaxZ, maxBlockZ);
                    InterpolatedNoiseCache noiseCache = new InterpolatedNoiseCache(veinToggle, veinRidged,
                            finalizedBlender, randOffsetX, randOffsetY, randOffsetZ,
                            fromX, fromY, fromZ, toX, toY, toZ);

                    for (int x = fromX; x <= toX; x++) {
                        for (int y = fromY; y <= toY; y++) {
                            for (int z = fromZ; z <= toZ; z++) {
                                double absToggleNoise = Math.abs(noiseCache.toggle(x, y, z));
                                if (absToggleNoise + edgeRoundoff < veininessThreshold) continue;
                                if (random.nextFloat() > entry.density()) continue;
                                if (noiseCache.ridged(x, y, z) >= 0.0) continue;

                                double chance = Mth.clampedMap(absToggleNoise, veininessThreshold,
                                        maxRichnessThreshold, minRichness, maxRichness);
                                pos.set(x, y, z);
                                long randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order
                                SectionOreBlockPlacer.getOrCreate(generatedBlocks, pos.asLong(), blockPlacer)
                                        .add(pos.asLong(), randomSeed, Double.doubleToRawLongBits(chance));
                            }
                        }
                    }
                }
            }
        }
        return generatedBlocks;
    }

    private static final class InterpolatedNoiseCache implements DensityFunction.FunctionContext {

        private static final KeyDispatchDataCodec<? extends DensityFunction> INTERPOLATED_CODEC = DensityFunctions.interpolated(DensityFunctions.zero()).codec();

        // shortcut: assumes vanilla 4x8 noise cells; derive spacing from dimension NoiseSettings if custom settings
        // need support.
        private static final int XZ_STEP = 4;
        private static final int Y_STEP = 8;

        private final DensityFunction toggleFunction;
        private final DensityFunction ridgedFunction;
        private final Blender blender;
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final int minCellX;
        private final int minCellY;
        private final int minCellZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private int sampleX;
        private int sampleY;
        private int sampleZ;

        private InterpolatedNoiseCache(DensityFunction toggleFunction, DensityFunction ridgedFunction,
                                       Blender blender, int offsetX, int offsetY, int offsetZ,
                                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.blender = blender;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.minCellX = Math.floorDiv(minX + offsetX, XZ_STEP);
            this.minCellY = Math.floorDiv(minY + offsetY, Y_STEP);
            this.minCellZ = Math.floorDiv(minZ + offsetZ, XZ_STEP);
            this.sizeX = Math.floorDiv(maxX + offsetX, XZ_STEP) - minCellX + 2;
            this.sizeY = Math.floorDiv(maxY + offsetY, Y_STEP) - minCellY + 2;
            this.sizeZ = Math.floorDiv(maxZ + offsetZ, XZ_STEP) - minCellZ + 2;
            // Preserve registry expressions: abs/max must run after their interpolated inputs.
            DensityFunction.Visitor visitor = function -> function instanceof DensityFunctions.MarkerOrMarked marker && marker.codec() == INTERPOLATED_CODEC ?
                    new InterpolatedFunction(marker.wrapped()) : function;
            this.toggleFunction = toggleFunction.mapAll(visitor);
            this.ridgedFunction = ridgedFunction.mapAll(visitor);
        }

        private double toggle(int x, int y, int z) {
            return compute(toggleFunction, x, y, z);
        }

        private double ridged(int x, int y, int z) {
            return compute(ridgedFunction, x, y, z);
        }

        private double compute(DensityFunction function, int x, int y, int z) {
            sampleX = x + offsetX;
            sampleY = y + offsetY;
            sampleZ = z + offsetZ;
            return function.compute(this);
        }

        private final class InterpolatedFunction implements DensityFunction.SimpleFunction, DensityFunction.FunctionContext {

            private final DensityFunction function;
            private final double[] values = new double[sizeX * sizeY * sizeZ];
            private final byte[] sampled = new byte[values.length];
            private int sampleX;
            private int sampleY;
            private int sampleZ;

            private InterpolatedFunction(DensityFunction function) {
                this.function = function;
            }

            @Override
            public double compute(DensityFunction.FunctionContext context) {
                int x = context.blockX();
                int y = context.blockY();
                int z = context.blockZ();
                int cellX = Math.floorDiv(x, XZ_STEP) - minCellX;
                int cellY = Math.floorDiv(y, Y_STEP) - minCellY;
                int cellZ = Math.floorDiv(z, XZ_STEP) - minCellZ;
                assert cellX >= 0 && cellX < sizeX;
                assert cellY >= 0 && cellY < sizeY;
                assert cellZ >= 0 && cellZ < sizeZ;
                double deltaX = Math.floorMod(x, XZ_STEP) / (double) XZ_STEP;
                double deltaY = Math.floorMod(y, Y_STEP) / (double) Y_STEP;
                double deltaZ = Math.floorMod(z, XZ_STEP) / (double) XZ_STEP;
                if (deltaX == 0.0 && deltaY == 0.0 && deltaZ == 0.0) return sample(cellX, cellY, cellZ);
                assert cellX + 1 < sizeX && cellY + 1 < sizeY && cellZ + 1 < sizeZ;

                double x00 = Mth.lerp(deltaX, sample(cellX, cellY, cellZ), sample(cellX + 1, cellY, cellZ));
                double x10 = Mth.lerp(deltaX, sample(cellX, cellY + 1, cellZ), sample(cellX + 1, cellY + 1, cellZ));
                double x01 = Mth.lerp(deltaX, sample(cellX, cellY, cellZ + 1), sample(cellX + 1, cellY, cellZ + 1));
                double x11 = Mth.lerp(deltaX, sample(cellX, cellY + 1, cellZ + 1), sample(cellX + 1, cellY + 1, cellZ + 1));
                return Mth.lerp(deltaZ, Mth.lerp(deltaY, x00, x10), Mth.lerp(deltaY, x01, x11));
            }

            private double sample(int cellX, int cellY, int cellZ) {
                int index = (cellX * sizeY + cellY) * sizeZ + cellZ;
                if (sampled[index] == 0) {
                    sampleX = (minCellX + cellX) * XZ_STEP;
                    sampleY = (minCellY + cellY) * Y_STEP;
                    sampleZ = (minCellZ + cellZ) * XZ_STEP;
                    values[index] = function.compute(this);
                    sampled[index] = 1;
                }
                return values[index];
            }

            @Override
            public double minValue() {
                return function.minValue();
            }

            @Override
            public double maxValue() {
                return function.maxValue();
            }

            @Override
            public KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException("Cannot serialize vein noise cache");
            }

            @Override
            public int blockX() {
                return sampleX;
            }

            @Override
            public int blockY() {
                return sampleY;
            }

            @Override
            public int blockZ() {
                return sampleZ;
            }

            @Override
            public Blender getBlender() {
                return blender;
            }
        }

        @Override
        public int blockX() {
            return sampleX;
        }

        @Override
        public int blockY() {
            return sampleY;
        }

        @Override
        public int blockZ() {
            return sampleZ;
        }

        @Override
        public Blender getBlender() {
            return blender;
        }
    }

    private void placeBlock(BulkSectionAccess access, LevelChunkSection section, long randomSeed, GTOreDefinition entry, double chance, List<VeinBlockDefinition> rareEntries, BlockPos.MutableBlockPos pos, List<VeinBlockDefinition> commonEntries) {
        RandomSource random = new XoroshiroRandomSource(randomSeed);
        int sectionX = SectionPos.sectionRelative(pos.getX());
        int sectionY = SectionPos.sectionRelative(pos.getY());
        int sectionZ = SectionPos.sectionRelative(pos.getZ());
        BlockState current = section.getBlockState(sectionX, sectionY, sectionZ);
        if (random.nextFloat() <= entry.density()) {
            if (random.nextFloat() < chance) {
                if (rareBlocks != null && !rareBlocks.isEmpty() && random.nextFloat() < rareBlockChance) {
                    var ore = GTUtil.getRandomItem(random, rareEntries);
                    if (ore == null) return;
                    OreVeinUtil.placeOre(ore.block, current, access, section, random, pos, entry);
                } else {
                    var ore = GTUtil.getRandomItem(random, commonEntries);
                    if (ore == null) return;
                    OreVeinUtil.placeOre(ore.block, current, access, section, random, pos, entry);
                }
            } else {
                if (fillerBlock == null || fillerBlock.isAir()) return;
                if (!OreVeinUtil.canPlaceOre(current, access::getBlockState, random, entry, pos)) return;
                section.setBlockState(sectionX, sectionY, sectionZ, fillerBlock, false);
            }
        }
    }

    @Override
    public VeinGenerator build() {
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new VeinedVeinGenerator(new ArrayList<>(this.oreBlocks), new ArrayList<>(this.rareBlocks), this.fillerBlock, this.minYLevel, this.maxYLevel, this.veininessThreshold, this.edgeRoundoffBegin, this.maxEdgeRoundoff, this.minRichness, this.maxRichness, this.maxRichnessThreshold, this.rareBlockChance);
    }

    @Override
    public Codec<? extends VeinGenerator> codec() {
        return CODEC;
    }

    public VeinedVeinGenerator oreBlock(Material block, int weight) {
        return this.oreBlock(new VeinBlockDefinition(block, weight));
    }

    public VeinedVeinGenerator oreBlock(BlockState blockState, int weight) {
        TargetBlockState target = OreConfiguration.target(AlwaysTrueTest.INSTANCE, blockState);
        return this.oreBlock(new VeinBlockDefinition(List.of(target), weight));
    }

    public VeinedVeinGenerator oreBlock(VeinBlockDefinition material) {
        this.oreBlocks.add(material);
        return this;
    }

    public VeinedVeinGenerator rareBlock(Material block, int weight) {
        return this.rareBlock(new VeinBlockDefinition(block, weight));
    }

    public VeinedVeinGenerator rareBlock(BlockState blockState, int weight) {
        TargetBlockState target = OreConfiguration.target(AlwaysTrueTest.INSTANCE, blockState);
        return this.rareBlock(new VeinBlockDefinition(List.of(target), weight));
    }

    public VeinedVeinGenerator rareBlock(VeinBlockDefinition material) {
        this.rareBlocks.add(material);
        return this;
    }

    public record VeinBlockDefinition(Either<List<TargetBlockState>, Material> block, int weight) implements WeightedEntry {

        public static final Codec<VeinBlockDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(BLOCK_ENTRY_CODEC.fieldOf("block").forGetter(x -> x.block), Codec.INT.fieldOf("weight").forGetter(x -> x.weight)).apply(instance, VeinBlockDefinition::new));

        public VeinBlockDefinition(Material block, int weight) {
            this(Either.right(block), weight);
        }

        public VeinBlockDefinition(List<TargetBlockState> block, int weight) {
            this(Either.left(block), weight);
        }
    }

    private static DensityFunction mapToNoise(DensityFunction function, RandomState randomState) {
        return function.mapAll(new DensityFunction.Visitor() {

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return densityFunction;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
                var holder = noiseHolder.noiseData();
                var noise = randomState.getOrCreateNoise(holder.unwrapKey().orElseThrow());
                return new DensityFunction.NoiseHolder(holder, noise);
            }
        });
    }

    public VeinedVeinGenerator(final List<VeinBlockDefinition> oreBlocks, final List<VeinBlockDefinition> rareBlocks, final BlockState fillerBlock, final int minYLevel, final int maxYLevel, final float veininessThreshold, final int edgeRoundoffBegin, final double maxEdgeRoundoff, final float minRichness, final float maxRichness, final float maxRichnessThreshold, final float rareBlockChance) {
        this.oreBlocks = oreBlocks;
        this.rareBlocks = rareBlocks;
        this.fillerBlock = fillerBlock;
        this.minYLevel = minYLevel;
        this.maxYLevel = maxYLevel;
        this.veininessThreshold = veininessThreshold;
        this.edgeRoundoffBegin = edgeRoundoffBegin;
        this.maxEdgeRoundoff = maxEdgeRoundoff;
        this.minRichness = minRichness;
        this.maxRichness = maxRichness;
        this.maxRichnessThreshold = maxRichnessThreshold;
        this.rareBlockChance = rareBlockChance;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator fillerBlock(final BlockState fillerBlock) {
        this.fillerBlock = fillerBlock;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator minYLevel(final int minYLevel) {
        this.minYLevel = minYLevel;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator maxYLevel(final int maxYLevel) {
        this.maxYLevel = maxYLevel;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator veininessThreshold(final float veininessThreshold) {
        this.veininessThreshold = veininessThreshold;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator edgeRoundoffBegin(final int edgeRoundoffBegin) {
        this.edgeRoundoffBegin = edgeRoundoffBegin;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator maxEdgeRoundoff(final double maxEdgeRoundoff) {
        this.maxEdgeRoundoff = maxEdgeRoundoff;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator minRichness(final float minRichness) {
        this.minRichness = minRichness;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator maxRichness(final float maxRichness) {
        this.maxRichness = maxRichness;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator maxRichnessThreshold(final float maxRichnessThreshold) {
        this.maxRichnessThreshold = maxRichnessThreshold;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public VeinedVeinGenerator rareBlockChance(final float rareBlockChance) {
        this.rareBlockChance = rareBlockChance;
        return this;
    }
}
