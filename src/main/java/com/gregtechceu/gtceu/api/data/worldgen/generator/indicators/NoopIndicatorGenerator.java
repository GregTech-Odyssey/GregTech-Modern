package com.gregtechceu.gtceu.api.data.worldgen.generator.indicators;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.generator.IndicatorGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreIndicatorPlacer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class NoopIndicatorGenerator extends IndicatorGenerator {

    public static final NoopIndicatorGenerator INSTANCE = new NoopIndicatorGenerator();
    public static final Codec<NoopIndicatorGenerator> CODEC = Codec.unit(() -> INSTANCE);

    @Override
    public Long2ObjectMap<OreIndicatorPlacer> generate(RandomSource random,
                                                       GeneratedVeinMetadata metadata) {
        // Nothing to do here
        return Long2ObjectMaps.emptyMap();
    }

    @Override
    public int getSearchRadiusModifier(int veinRadius) {
        return 0;
    }

    @Nullable
    @Override
    public Either<BlockState, Material> block() {
        return null;
    }

    @Override
    public Codec<? extends IndicatorGenerator> codec() {
        return CODEC;
    }
}
