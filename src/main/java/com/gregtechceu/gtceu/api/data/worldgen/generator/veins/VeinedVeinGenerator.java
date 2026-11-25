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
import java.util.Arrays;
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
        Long2ObjectOpenHashMap<SectionOreBlockPlacer> generatedBlocks = new Long2ObjectOpenHashMap<>();
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
        int minBlockY = Math.max(originY - radius, level.getMinBuildHeight());
        int maxBlockY = Math.min(originY + radius, level.getMaxBuildHeight() - 1);
        if (minBlockY > maxBlockY) return generatedBlocks;

        int minBlockZ = origin.getZ() - radius;
        int maxBlockZ = origin.getZ() + radius;
        int minSectionX = SectionPos.blockToSectionCoord(minBlockX);
        int maxSectionX = SectionPos.blockToSectionCoord(maxBlockX);
        int minSectionY = SectionPos.blockToSectionCoord(minBlockY);
        int maxSectionY = SectionPos.blockToSectionCoord(maxBlockY);
        int minSectionZ = SectionPos.blockToSectionCoord(minBlockZ);
        int maxSectionZ = SectionPos.blockToSectionCoord(maxBlockZ);
        InterpolatedNoiseCache noiseCache = new InterpolatedNoiseCache(veinToggle, veinRidged,
                finalizedBlender, randOffsetX, randOffsetY, randOffsetZ,
                minBlockX, minBlockY, minBlockZ, maxBlockX, maxBlockY, maxBlockZ);
        SectionOreBlockPlacer.BlockPlacer blockPlacer = (access, section, pos, current, randomSeed, chance, ignored) -> placeBlock(access, section, current, randomSeed, entry, Double.longBitsToDouble(chance), rareBlocks, pos, oreBlocks);

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            int sectionMinX = SectionPos.sectionToBlockCoord(sectionX);
            int fromX = Math.max(sectionMinX, minBlockX);
            int toX = Math.min(sectionMinX + 15, maxBlockX);
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                int sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
                int fromY = Math.max(sectionMinY, minBlockY);
                int toY = Math.min(sectionMinY + 15, maxBlockY);
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    int sectionMinZ = SectionPos.sectionToBlockCoord(sectionZ);
                    int fromZ = Math.max(sectionMinZ, minBlockZ);
                    int toZ = Math.min(sectionMinZ + 15, maxBlockZ);
                    SectionOreBlockPlacer sectionPlacer = null;

                    for (int x = fromX; x <= toX; x++) {
                        for (int y = fromY; y <= toY; y++) {
                            for (int z = fromZ; z <= toZ; z++) {
                                double absToggleNoise = Math.abs(noiseCache.toggle(x, y, z));
                                if (absToggleNoise + edgeRoundoff < veininessThreshold) continue;
                                if (random.nextFloat() > entry.density()) continue;
                                if (noiseCache.ridged(x, y, z) >= 0.0) continue;

                                double chance = Mth.clampedMap(absToggleNoise, veininessThreshold,
                                        maxRichnessThreshold, minRichness, maxRichness);
                                long randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order
                                if (sectionPlacer == null) {
                                    sectionPlacer = SectionOreBlockPlacer.create(blockPlacer);
                                    generatedBlocks.put(SectionPos.asLong(sectionX, sectionY, sectionZ), sectionPlacer);
                                }
                                sectionPlacer.add(BlockPos.asLong(x, y, z), randomSeed, Double.doubleToRawLongBits(chance));
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
        private static final int XZ_SHIFT = 2;
        private static final int XZ_MASK = (1 << XZ_SHIFT) - 1;
        private static final double INV_XZ_STEP = 1.0 / (1 << XZ_SHIFT);
        private static final int Y_SHIFT = 3;
        private static final int Y_MASK = (1 << Y_SHIFT) - 1;
        private static final double INV_Y_STEP = 1.0 / (1 << Y_SHIFT);

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
            this.minCellX = (minX + offsetX) >> XZ_SHIFT;
            this.minCellY = (minY + offsetY) >> Y_SHIFT;
            this.minCellZ = (minZ + offsetZ) >> XZ_SHIFT;
            int maxShiftedX = maxX + offsetX;
            int maxShiftedY = maxY + offsetY;
            int maxShiftedZ = maxZ + offsetZ;
            this.sizeX = (maxShiftedX >> XZ_SHIFT) - minCellX + ((maxShiftedX & XZ_MASK) == 0 ? 1 : 2);
            this.sizeY = (maxShiftedY >> Y_SHIFT) - minCellY + ((maxShiftedY & Y_MASK) == 0 ? 1 : 2);
            this.sizeZ = (maxShiftedZ >> XZ_SHIFT) - minCellZ + ((maxShiftedZ & XZ_MASK) == 0 ? 1 : 2);
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
            private int sampleX;
            private int sampleY;
            private int sampleZ;

            private InterpolatedFunction(DensityFunction function) {
                this.function = function;
                Arrays.fill(values, Double.NaN);
            }

            @Override
            public double compute(DensityFunction.FunctionContext context) {
                int x = context.blockX();
                int y = context.blockY();
                int z = context.blockZ();
                int cellX = (x >> XZ_SHIFT) - minCellX;
                int cellY = (y >> Y_SHIFT) - minCellY;
                int cellZ = (z >> XZ_SHIFT) - minCellZ;
                int modX = x & XZ_MASK;
                int modY = y & Y_MASK;
                int modZ = z & XZ_MASK;
                int maxCellX = modX == 0 ? cellX : cellX + 1;
                int maxCellY = modY == 0 ? cellY : cellY + 1;
                int maxCellZ = modZ == 0 ? cellZ : cellZ + 1;
                if (cellX < 0 || maxCellX >= sizeX || cellY < 0 || maxCellY >= sizeY || cellZ < 0 || maxCellZ >= sizeZ) {
                    return function.compute(context);
                }
                if (modX == 0 && modY == 0 && modZ == 0) return sample(cellX, cellY, cellZ);

                double deltaX = modX * INV_XZ_STEP;
                double deltaY = modY * INV_Y_STEP;
                double z0 = interpolateXY(cellX, maxCellX, cellY, maxCellY, cellZ, deltaX, deltaY);
                if (modZ == 0) return z0;
                double z1 = interpolateXY(cellX, maxCellX, cellY, maxCellY, maxCellZ, deltaX, deltaY);
                return Mth.lerp(modZ * INV_XZ_STEP, z0, z1);
            }

            private double interpolateXY(int x0, int x1, int y0, int y1, int z, double deltaX, double deltaY) {
                double y0Val = x0 == x1 ? sample(x0, y0, z) : Mth.lerp(deltaX, sample(x0, y0, z), sample(x1, y0, z));
                if (y0 == y1) return y0Val;
                double y1Val = x0 == x1 ? sample(x0, y1, z) : Mth.lerp(deltaX, sample(x0, y1, z), sample(x1, y1, z));
                return Mth.lerp(deltaY, y0Val, y1Val);
            }

            private double sample(int cellX, int cellY, int cellZ) {
                int index = (cellX * sizeY + cellY) * sizeZ + cellZ;
                double value = values[index];
                if (Double.isNaN(value)) {
                    sampleX = (minCellX + cellX) << XZ_SHIFT;
                    sampleY = (minCellY + cellY) << Y_SHIFT;
                    sampleZ = (minCellZ + cellZ) << XZ_SHIFT;
                    value = function.compute(this);
                    values[index] = value;
                }
                return value;
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

    private void placeBlock(BulkSectionAccess access, LevelChunkSection section, BlockState current, long randomSeed, GTOreDefinition entry, double chance, List<VeinBlockDefinition> rareEntries, BlockPos.MutableBlockPos pos, List<VeinBlockDefinition> commonEntries) {
        RandomSource random = new XoroshiroRandomSource(randomSeed);
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
                section.setBlockState(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()), fillerBlock, false);
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
