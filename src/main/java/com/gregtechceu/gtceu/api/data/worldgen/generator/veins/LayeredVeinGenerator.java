package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTLayerPattern;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreVeinUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.gto.registrate.util.nullness.NonNullSupplier;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LayeredVeinGenerator extends VeinGenerator {

    public static final Codec<LayeredVeinGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GTLayerPattern.CODEC.listOf().fieldOf("layer_patterns")
                    .forGetter(LayeredVeinGenerator::getLayerPatterns))
            .apply(instance, LayeredVeinGenerator::new));

    private final List<NonNullSupplier<GTLayerPattern>> bakingLayerPatterns = new ArrayList<>();

    public List<GTLayerPattern> layerPatterns;

    public LayeredVeinGenerator(GTOreDefinition entry) {
        super(entry);
    }

    public List<GTLayerPattern> getLayerPatterns() {
        if (layerPatterns == null || this.layerPatterns.isEmpty()) {
            layerPatterns = bakingLayerPatterns.stream().map(Supplier::get).collect(Collectors.toList());
        }
        return layerPatterns;
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        return getLayerPatterns().stream()
                .flatMap(pattern -> pattern.layers.stream())
                .flatMap(GTLayerPattern.Layer::asVeinEntries)
                .distinct()
                .toList();
    }

    @Override
    public Long2ObjectMap<OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry,
                                                   BlockPos origin) {
        Long2ObjectMap<OreBlockPlacer> generatedBlocks = new Long2ObjectOpenHashMap<>();
        float density = entry.density();
        boolean traced = trace(random, entry, origin, level, null, (pos, state, randomSeed) -> generatedBlocks
                .put(pos.asLong(), (access, section) -> placeBlock(access, section, randomSeed, entry, density, state, pos)));
        return traced ? generatedBlocks : Long2ObjectMaps.emptyMap();
    }

    @Override
    public boolean sample(GTOreDefinition entry, RandomSource random, BlockPos origin, BoundingBox area, SampleSink sink) {
        float density = entry.density();
        return trace(random, entry, origin, UNBOUNDED_HEIGHT, area, (pos, state, randomSeed) -> sink.accept(pos,
                passesDensity(new XoroshiroRandomSource(randomSeed), density) ? state : null));
    }

    @FunctionalInterface
    private interface LayerSink {

        void accept(BlockPos pos, Either<List<OreConfiguration.TargetBlockState>, Material> state, long randomSeed);
    }

    private boolean trace(RandomSource random, GTOreDefinition entry, BlockPos origin, LevelHeightAccessor heights,
                          @Nullable BoundingBox area, LayerSink sink) {
        var patternPool = this.getLayerPatterns();

        if (patternPool.isEmpty())
            return false;

        GTLayerPattern layerPattern = patternPool.get(random.nextInt(patternPool.size()));

        int size = entry.clusterSize().sample(random);

        int radius = Mth.ceil(size / 2f);

        int xMin = origin.getX() - radius;
        int yMin = origin.getY() - radius;
        int zMin = origin.getZ() - radius;
        int width = (radius * 2) + 1;
        int length = (radius * 2) + 1;
        int height = (radius * 2) + 1;

        if (origin.getY() >= heights.getMaxBuildHeight())
            return false;

        int xFrom = clipFrom(area, Direction.Axis.X, xMin, 0), xTo = clipTo(area, Direction.Axis.X, xMin, width - 1);
        int yFrom = clipFrom(area, Direction.Axis.Y, yMin, 0), yTo = clipTo(area, Direction.Axis.Y, yMin, height - 1);
        int zFrom = clipFrom(area, Direction.Axis.Z, zMin, 0), zTo = clipTo(area, Direction.Axis.Z, zMin, length - 1);

        List<GTLayerPattern.Layer> resolvedLayers = new ArrayList<>();
        FloatList layerDiameterOffsets = new FloatArrayList();

        int layerCoordinate = random.nextInt(4);
        int slantyCoordinate = random.nextInt(3);
        float slope = random.nextFloat() * .75f;

        for (int xOffset = xFrom; xOffset <= xTo; xOffset++) {
            float sizeFractionX = xOffset * 2f / width - 1;
            float xSizeSqr = sizeFractionX * sizeFractionX;
            if (xSizeSqr > 1)
                continue;

            for (int yOffset = yFrom; yOffset <= yTo; yOffset++) {
                float sizeFractionY = yOffset * 2f / height - 1;
                float ySizeSqr = sizeFractionY * sizeFractionY;
                if (xSizeSqr + ySizeSqr > 1)
                    continue;
                if (heights.isOutsideBuildHeight(yMin + yOffset))
                    continue;

                for (int zOffset = zFrom; zOffset <= zTo; zOffset++) {
                    float sizeFractionZ = zOffset * 2f / length - 1;
                    float zSizeSqr = sizeFractionZ * sizeFractionZ;

                    int layerIndex = layerCoordinate == 0 ? zOffset : layerCoordinate == 1 ? xOffset : yOffset;
                    if (slantyCoordinate != layerCoordinate)
                        layerIndex += Mth.floor(
                                slantyCoordinate == 0 ? zOffset : slantyCoordinate == 1 ? xOffset : yOffset) * slope;

                    while (layerIndex >= resolvedLayers.size()) {
                        GTLayerPattern.Layer next = layerPattern.rollNext(
                                resolvedLayers.isEmpty() ? null : resolvedLayers.getLast(),
                                random);
                        float offset = random.nextFloat() * .5f + .5f;
                        for (int i = 0; i < next.minSize + random.nextInt(1 + next.maxSize - next.minSize); i++) {
                            resolvedLayers.add(next);
                            layerDiameterOffsets.add(offset);
                        }
                    }

                    if (xSizeSqr + ySizeSqr + zSizeSqr > layerDiameterOffsets.getFloat(layerIndex))
                        continue;

                    GTLayerPattern.Layer layer = resolvedLayers.get(layerIndex);
                    Either<List<OreConfiguration.TargetBlockState>, Material> state = layer.rollBlock(random);

                    int currentX = xMin + xOffset;
                    int currentY = yMin + yOffset;
                    int currentZ = zMin + zOffset;

                    final var randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order

                    sink.accept(new BlockPos(currentX, currentY, currentZ), state, randomSeed);
                }
            }
        }

        return true;
    }

    private static boolean passesDensity(RandomSource random, float density) {
        return random.nextFloat() <= density;
    }

    private static void placeBlock(BulkSectionAccess access, LevelChunkSection section, long randomSeed,
                                   GTOreDefinition entry, float density,
                                   Either<List<OreConfiguration.TargetBlockState>, Material> state, BlockPos pos) {
        RandomSource random = new XoroshiroRandomSource(randomSeed);
        BlockState current = OreVeinUtil.sectionState(section, pos);
        if (passesDensity(random, density)) {
            OreVeinUtil.placeOre(state, current, access, section, random, pos, entry);
        }
    }

    public LayeredVeinGenerator(List<GTLayerPattern> layerPatterns) {
        super();
        this.layerPatterns = layerPatterns;
    }

    public LayeredVeinGenerator buildLayerPattern(Consumer<GTLayerPattern.Builder> config) {
        var builder = GTLayerPattern.builder(parent().layer().getTarget());
        config.accept(builder);

        return withLayerPattern(builder::build);
    }

    public LayeredVeinGenerator withLayerPattern(NonNullSupplier<GTLayerPattern> pattern) {
        this.bakingLayerPatterns.add(pattern);
        return this;
    }

    public VeinGenerator build() {
        if (this.layerPatterns != null && !this.layerPatterns.isEmpty()) return this;
        this.layerPatterns = this.bakingLayerPatterns.stream()
                .map(NonNullSupplier::get)
                .toList();
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new LayeredVeinGenerator(new ArrayList<>(this.layerPatterns));
    }

    @Override
    public Codec<? extends VeinGenerator> codec() {
        return CODEC;
    }
}
