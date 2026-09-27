package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreVeinUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ClassicVeinGenerator extends VeinGenerator {

    public static final Codec<ClassicVeinGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(Layer.CODEC.fieldOf("primary").forGetter(val -> val.primary), Layer.CODEC.fieldOf("secondary").forGetter(val -> val.secondary), Layer.CODEC.fieldOf("between").forGetter(val -> val.between), Layer.CODEC.fieldOf("sporadic").forGetter(val -> val.sporadic), ExtraCodecs.POSITIVE_INT.optionalFieldOf("y_radius", 3).forGetter(val -> val.yRadius)).apply(instance, ClassicVeinGenerator::new));
    private Layer primary;
    private Layer secondary;
    private Layer between;
    private Layer sporadic;
    private int yRadius = 6;
    // Provided for readability
    private int sporadicDivisor;
    private int startPrimary;
    private int startBetween;
    private RuleTest[] rules;

    public ClassicVeinGenerator(GTOreDefinition entry) {
        super(entry);
    }

    public ClassicVeinGenerator(Layer primary, Layer secondary, Layer between, Layer sporadic, int yRadius) {
        this.primary = primary;
        this.secondary = secondary;
        this.between = between;
        this.sporadic = sporadic;
        this.yRadius = yRadius;
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        List<VeinEntry> entries = new ArrayList<>(primary.size() + secondary.size() + between.size() + sporadic.size());
        VeinGenerator.mapTarget(primary.target, primary.layers).forEach(entries::add);
        VeinGenerator.mapTarget(secondary.target, secondary.layers).forEach(entries::add);
        VeinGenerator.mapTarget(between.target, between.layers).forEach(entries::add);
        VeinGenerator.mapTarget(sporadic.target, 1).forEach(entries::add);
        return entries;
    }

    @Override
    public Long2ObjectMap<OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry, BlockPos origin) {
        Long2ObjectMap<OreBlockPlacer> generatedBlocks = new Long2ObjectOpenHashMap<>();
        trace(random, entry, origin, level, null, (pos, randomSeed, layer) -> generatedBlocks
                .put(pos.asLong(), (access, section) -> placeBlock(access, section, randomSeed, entry, pos, layer)));
        return generatedBlocks;
    }

    @Override
    public boolean sample(GTOreDefinition entry, RandomSource random, BlockPos origin, BoundingBox area, SampleSink sink) {
        float density = entry.density();
        trace(random, entry, origin, UNBOUNDED_HEIGHT, area, (pos, randomSeed, layer) -> {
            Layer chosen = chooseLayer(new XoroshiroRandomSource(randomSeed), layer, density);
            sink.accept(pos, chosen == null ? null : chosen.target);
        });
        return true;
    }

    @FunctionalInterface
    private interface ClassicSink {

        void accept(BlockPos pos, long randomSeed, int layer);
    }

    private void trace(RandomSource random, GTOreDefinition entry, BlockPos origin, LevelHeightAccessor heights,
                       @Nullable BoundingBox area, ClassicSink sink) {
        int radius = entry.clusterSize().sample(random) / 2;
        int ySize = radius / 2;
        int xy2 = radius * radius * ySize * ySize;
        int xz2 = radius * radius * radius * radius;
        int yz2 = ySize * ySize * radius * radius;
        int xyz2 = xy2 * radius * radius;
        int xPos = origin.getX();
        int yPos = origin.getY();
        int zPos = origin.getZ();
        int max = Math.max(ySize, radius);
        int yMax = Math.min(max, yRadius);
        int lowestY = yPos - yMax;
        int xFrom = clipFrom(area, Direction.Axis.X, xPos, -max), xTo = clipTo(area, Direction.Axis.X, xPos, max);
        int yFrom = clipFrom(area, Direction.Axis.Y, yPos, -yMax), yTo = clipTo(area, Direction.Axis.Y, yPos, yMax);
        int zFrom = clipFrom(area, Direction.Axis.Z, zPos, -max), zTo = clipTo(area, Direction.Axis.Z, zPos, max);
        for (int xOffset = xFrom; xOffset <= xTo; xOffset++) {
            int xr = yz2 * xOffset * xOffset;
            if (xr > xyz2) continue;
            for (int yOffset = yFrom; yOffset <= yTo; yOffset++) {
                int yr = xr + xz2 * yOffset * yOffset + xy2;
                if (yr > xyz2) continue;
                if (heights.isOutsideBuildHeight(yOffset + yPos)) continue;
                for (int zOffset = zFrom; zOffset <= zTo; zOffset++) {
                    int zr = yr + xy2 * zOffset * zOffset;
                    if (zr > xyz2) continue;
                    final var randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order
                    BlockPos currentPos = new BlockPos(xOffset + xPos, yOffset + yPos, zOffset + zPos);
                    sink.accept(currentPos, randomSeed, currentPos.getY() - lowestY);
                }
            }
        }
    }

    @Nullable
    private Layer chooseLayer(RandomSource random, int layer, float density) {
        if (layer >= startBetween && layer - startBetween + 1 <= between.layers && random.nextFloat() <= density / 2) {
            return between;
        }
        if (random.nextFloat() <= density) {
            return layer >= startPrimary ? primary : secondary;
        }
        return random.nextFloat() <= density / sporadicDivisor ? sporadic : null;
    }

    private void placeBlock(BulkSectionAccess access, LevelChunkSection section, long randomSeed, GTOreDefinition entry, BlockPos blockPos, int layer) {
        BlockState current = OreVeinUtil.sectionState(section, blockPos);
        Layer chosen = chooseLayer(new XoroshiroRandomSource(randomSeed), layer, entry.density());
        if (chosen != null) {
            OreVeinUtil.placeOre(chosen.target, current, access, section, new XoroshiroRandomSource(randomSeed), blockPos, entry);
        }
    }

    @Override
    public VeinGenerator build() {
        primary.layers = primary.layers == -1 ? 4 : primary.layers;
        secondary.layers = secondary.layers == -1 ? 3 : secondary.layers;
        between.layers = between.layers == -1 ? 3 : between.layers;
        // Ensure "between" is not more than the total primary and secondary layers
        Preconditions.checkArgument(primary.layers + secondary.layers >= between.layers, "Error: cannot have more \"between\" layers than primary and secondary layers combined!");
        this.sporadicDivisor = primary.layers + secondary.layers - 1;
        this.startPrimary = secondary.layers;
        this.startBetween = secondary.layers - between.layers / 2;
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new ClassicVeinGenerator(this.primary.copy(), this.secondary.copy(), this.between.copy(), this.sporadic.copy(), this.yRadius);
    }

    @Override
    public Codec<? extends VeinGenerator> codec() {
        return CODEC;
    }

    public ClassicVeinGenerator primary(Consumer<Layer.Builder> builder) {
        Layer.Builder layerBuilder = new Layer.Builder(rules != null ? rules : new RuleTest[] { AlwaysTrueTest.INSTANCE });
        builder.accept(layerBuilder);
        primary = layerBuilder.build();
        return this;
    }

    public ClassicVeinGenerator secondary(Consumer<Layer.Builder> builder) {
        Layer.Builder layerBuilder = new Layer.Builder(rules != null ? rules : new RuleTest[] { AlwaysTrueTest.INSTANCE });
        builder.accept(layerBuilder);
        secondary = layerBuilder.build();
        return this;
    }

    public ClassicVeinGenerator between(Consumer<Layer.Builder> builder) {
        Layer.Builder layerBuilder = new Layer.Builder(rules != null ? rules : new RuleTest[] { AlwaysTrueTest.INSTANCE });
        builder.accept(layerBuilder);
        between = layerBuilder.build();
        return this;
    }

    public ClassicVeinGenerator sporadic(Consumer<Layer.Builder> builder) {
        Layer.Builder layerBuilder = new Layer.Builder(rules != null ? rules : new RuleTest[] { AlwaysTrueTest.INSTANCE });
        builder.accept(layerBuilder);
        sporadic = layerBuilder.build();
        return this;
    }

    public static class Layer {

        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.either(OreConfiguration.TargetBlockState.CODEC.listOf(), GTCEuAPI.materialManager.codec()).fieldOf("targets").forGetter(layer -> layer.target), ExtraCodecs.intRange(-1, Integer.MAX_VALUE).optionalFieldOf("layers", -1).forGetter(layer -> layer.layers)).apply(instance, Layer::new));
        public final Either<List<OreConfiguration.TargetBlockState>, Material> target;
        public int layers;

        public Layer copy() {
            return new Layer(this.target.mapBoth(ArrayList::new, Function.identity()), layers);
        }

        public int size() {
            return target.left().isPresent() ? target.left().get().size() : 1;
        }

        public static class Builder {

            private Either<List<OreConfiguration.TargetBlockState>, Material> target;
            private int size = -1;
            private final RuleTest[] rules;

            protected Builder(RuleTest... rules) {
                this.rules = rules;
            }

            public Layer.Builder block(Supplier<? extends Block> block) {
                return state(block.get().defaultBlockState());
            }

            public Layer.Builder state(Supplier<? extends BlockState> state) {
                return state(state.get());
            }

            public Layer.Builder state(BlockState state) {
                this.target = Either.left(Arrays.stream(this.rules).map(rule -> OreConfiguration.target(rule, state)).toList());
                return this;
            }

            public Layer.Builder mat(Material material) {
                this.target = Either.right(material);
                return this;
            }

            public Layer.Builder size(int size) {
                this.size = size;
                return this;
            }

            public Layer build() {
                return new Layer(target, size);
            }
        }

        public Layer(final Either<List<OreConfiguration.TargetBlockState>, Material> target, final int layers) {
            this.target = target;
            this.layers = layers;
        }
    }

    /**
     * @return {@code this}.
     */
    public ClassicVeinGenerator yRadius(final int yRadius) {
        this.yRadius = yRadius;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public ClassicVeinGenerator rules(final RuleTest[] rules) {
        this.rules = rules;
        return this;
    }
}
