package com.gregtechceu.gtceu.api.data.worldgen;

import com.gregtechceu.gtceu.api.data.worldgen.generator.IndicatorGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.utils.PosUtils;
import com.gregtechceu.gtceu.utils.WeightedEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.fast.fastcollection.OpenCacheHashSet;
import com.google.common.collect.HashBiMap;
import com.gto.datasynclib.datasream.DataComponentKey;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorldGeneratorUtils {

    private static final DataComponentKey<WorldOreVeinCache> KEY = DataComponentKey.createNoCodec("cache");

    public static final RuleTest END_ORE_REPLACEABLES = new TagMatchTest(CustomTags.ENDSTONE_ORE_REPLACEABLES);

    public static final LinkedHashMap<String, IWorldGenLayer> WORLD_GEN_LAYERS = new LinkedHashMap<>();

    public static final HashBiMap<ResourceLocation, Codec<? extends VeinGenerator>> VEIN_GENERATORS = HashBiMap
            .create();
    public static final Map<ResourceLocation, Function<GTOreDefinition, ? extends VeinGenerator>> VEIN_GENERATOR_FUNCTIONS = new O2OOpenCacheHashMap<>();

    public static final HashBiMap<ResourceLocation, Codec<? extends IndicatorGenerator>> INDICATOR_GENERATORS = HashBiMap
            .create();

    public record WeightedVein(GTOreDefinition vein, int weight) implements WeightedEntry {}

    private static class WorldOreVeinCache {

        private final List<GTOreDefinition> worldVeins;
        private final Map<Holder<Biome>, List<WeightedVein>> biomeVeins = new Reference2ObjectOpenHashMap<>();

        public WorldOreVeinCache(ServerLevel level) {
            List<GTOreDefinition> veinsList = new ArrayList<>();
            for (var entry : GTRegistries.ORE_VEINS.values()) {
                boolean match = false;
                for (var dim : entry.dimensionFilter()) {
                    if (WorldGeneratorUtils.isSameDimension(dim, level.dimension())) {
                        match = true;
                        break;
                    }
                }
                if (match) {
                    veinsList.add(entry);
                }
            }
            this.worldVeins = veinsList;
        }

        private List<WeightedVein> getEntry(Holder<Biome> biome) {
            List<WeightedVein> biomeList = new ArrayList<>();
            for (GTOreDefinition vein : worldVeins) {
                if (vein.isForBiome(biome)) {
                    int weight = vein.weightForBiome(biome);
                    if (weight > 0) {
                        biomeList.add(new WeightedVein(vein, weight));
                    }
                }
            }
            this.biomeVeins.put(biome, biomeList);
            return biomeList;
        }
    }

    public static List<WeightedVein> getCachedBiomeVeins(ServerLevel level, Holder<Biome> biome) {
        WorldOreVeinCache cache = ILevel.getCapability(level, KEY);
        if (cache == null) {
            cache = new WorldOreVeinCache(level);
            ILevel.setCapability(level, KEY, cache);
        }
        return cache.getEntry(biome);
    }

    public static Optional<String> getWorldGenLayerKey(IWorldGenLayer layer) {
        for (Map.Entry<String, IWorldGenLayer> entry : WORLD_GEN_LAYERS.entrySet()) {
            if (entry.getValue().equals(layer)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static boolean isSameDimension(ResourceKey<Level> first, ResourceKey<Level> second) {
        return first == second;
    }

    public static <T> Long2ObjectOpenHashMap<Long2ObjectMap<T>> groupByChunks(Long2ObjectMap<T> input) {
        Long2ObjectOpenHashMap<Long2ObjectMap<T>> result = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<T> entry : Long2ObjectMaps.fastIterable(input)) {
            long chunkLong = PosUtils.getChunkLong(entry.getLongKey());
            result.computeIfAbsent(chunkLong, k -> new Long2ObjectOpenHashMap<>()).putIfAbsent(entry.getLongKey(), entry.getValue());
        }
        return result;
    }

    public static Map<ChunkPos, List<BlockPos>> groupByChunks(Collection<BlockPos> positions) {
        var map = new O2OOpenCacheHashMap<ChunkPos, List<BlockPos>>();
        for (BlockPos pos : positions) {
            ChunkPos chunkPos = new ChunkPos(pos);
            map.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(pos);
        }
        return map;
    }

    public static Collection<ChunkPos> getChunks(Collection<BlockPos> positions) {
        Set<ChunkPos> chunkSet = new OpenCacheHashSet<>();
        for (BlockPos pos : positions) {
            chunkSet.add(new ChunkPos(pos));
        }
        return chunkSet;
    }

    public static void generateChunks(WorldGenLevel level, ChunkStatus requiredStatus, Collection<ChunkPos> chunks) {
        List<ChunkPos> previouslyUnloadedChunks = new ArrayList<>();
        var chunkSource = level.getChunkSource();

        for (ChunkPos chunkPos : chunks) {
            var chunk = chunkSource.getChunk(chunkPos.x, chunkPos.z, false);

            if (chunk == null) {
                previouslyUnloadedChunks.add(chunkPos);
            }

            chunkSource.getChunk(chunkPos.x, chunkPos.z, requiredStatus, true);
        }

        if (level instanceof ServerLevel serverLevel) {
            previouslyUnloadedChunks.forEach(chunk -> serverLevel.unload(serverLevel.getChunk(chunk.x, chunk.z)));
        }
    }

    public static Optional<BlockPos> findBlockPos(BlockPos initialPos, Predicate<BlockPos> predicate,
                                                  Consumer<BlockPos.MutableBlockPos> step, int maxSteps) {
        var currentPos = initialPos.mutable();

        while (maxSteps-- >= 0) {
            step.accept(currentPos);

            if (predicate.test(currentPos))
                return Optional.of(currentPos.immutable());
        }

        return Optional.empty();
    }
}
