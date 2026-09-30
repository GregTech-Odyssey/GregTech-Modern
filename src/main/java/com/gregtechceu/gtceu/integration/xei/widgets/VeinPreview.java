package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.veins.VeinedVeinGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.TargetBlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ToIntFunction;

public final class VeinPreview {

    public static final int SIZE = 12;
    public static final int HEIGHT = 13;
    public static final int BLOCKS_PER_VOXEL = 4;
    public static final byte EMPTY = 0;
    public static final byte HOST = 1;
    private static final int ENTRY_OFFSET = 2;
    private static final int SAMPLE_REACH = 256;
    private static final float EDGE_JITTER = 0.18f;
    private static final float RIBBON_RADIUS = 1.3f;
    private static final float RIBBON_AMPLITUDE = 2.6f;

    private final byte[] voxels = new byte[SIZE * SIZE * HEIGHT];
    private final boolean schematic;
    @Nullable
    private final Component caption;

    private VeinPreview(boolean schematic, @Nullable Component caption) {
        this.schematic = schematic;
        this.caption = caption;
    }

    public byte get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= HEIGHT || z >= SIZE) return EMPTY;
        return voxels[index(x, y, z)];
    }

    public static int entryIndex(byte value) {
        return value - ENTRY_OFFSET;
    }

    public boolean isSchematic() {
        return schematic;
    }

    @Nullable
    public Component caption() {
        return caption;
    }

    private static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    private void set(int x, int y, int z, int value) {
        voxels[index(x, y, z)] = (byte) value;
    }

    public static VeinPreview of(GTOreDefinition definition, List<VeinInfo.Entry> entries,
                                 ToIntFunction<Either<List<TargetBlockState>, Material>> index, long seed) {
        var random = new XoroshiroRandomSource(seed);
        var generator = definition.veinGenerator();
        if (generator != null) {
            var samples = new Long2ByteOpenHashMap();
            int half = SIZE * BLOCKS_PER_VOXEL / 2;
            var area = new BoundingBox(-half, -SAMPLE_REACH, -half, half - 1, SAMPLE_REACH, half - 1);
            boolean sampled = generator.sample(definition, random, BlockPos.ZERO, area, (pos, target) -> samples.put(pos.asLong(),
                    (byte) (target == null ? HOST : Math.max(HOST, index.applyAsInt(target) + ENTRY_OFFSET))));
            if (sampled) return voxelize(samples);
        }
        var preview = new VeinPreview(true, null);
        if (generator instanceof VeinedVeinGenerator) {
            preview.ribbon(entries, definition.density(), random);
        } else {
            preview.blob(entries, definition, random);
        }
        return preview;
    }

    public static VeinPreview of(BedrockOreDefinition definition, List<VeinInfo.Entry> entries, long seed) {
        var preview = new VeinPreview(true, Component.translatable("gtceu.jei.vein.chunk_area", definition.size(), definition.size()));
        var random = new XoroshiroRandomSource(seed);
        int total = totalWeight(entries);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int layers = 1 + random.nextInt(2);
                for (int y = 0; y < layers; y++) preview.set(x, y, z, weighted(entries, total, random) + ENTRY_OFFSET);
            }
        }
        return preview;
    }

    private static VeinPreview voxelize(Long2ByteOpenHashMap samples) {
        var preview = new VeinPreview(false, null);
        if (samples.isEmpty()) return preview;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (long key : samples.keySet()) {
            int y = BlockPos.getY(key);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int half = SIZE * BLOCKS_PER_VOXEL / 2;
        int bottom = (minY + maxY) / 2 - HEIGHT * BLOCKS_PER_VOXEL / 2;
        int center = BLOCKS_PER_VOXEL / 2;
        var centered = new boolean[preview.voxels.length];
        for (var it = samples.long2ByteEntrySet().fastIterator(); it.hasNext();) {
            var sample = it.next();
            long key = sample.getLongKey();
            int bx = BlockPos.getX(key) + half, by = BlockPos.getY(key) - bottom, bz = BlockPos.getZ(key) + half;
            int x = Math.floorDiv(bx, BLOCKS_PER_VOXEL), y = Math.floorDiv(by, BLOCKS_PER_VOXEL), z = Math.floorDiv(bz, BLOCKS_PER_VOXEL);
            if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= HEIGHT || z >= SIZE) continue;
            int voxel = index(x, y, z);
            boolean isCenter = Math.floorMod(bx, BLOCKS_PER_VOXEL) == center && Math.floorMod(by, BLOCKS_PER_VOXEL) == center &&
                    Math.floorMod(bz, BLOCKS_PER_VOXEL) == center;
            if (centered[voxel] || (!isCenter && preview.voxels[voxel] != EMPTY)) continue;
            preview.voxels[voxel] = sample.getByteValue();
            centered[voxel] = isCenter;
        }
        return preview;
    }

    private void ribbon(List<VeinInfo.Entry> entries, float density, RandomSource random) {
        int total = totalWeight(entries);
        float phase = random.nextFloat() * Mth.TWO_PI;
        float frequency = 0.35f + random.nextFloat() * 0.15f;
        for (int x = 0; x < SIZE; x++) {
            float cz = SIZE / 2f - 0.5f + RIBBON_AMPLITUDE * Mth.sin(x * frequency + phase);
            float cy = HEIGHT / 2f - 0.5f + 1.6f * Mth.sin(x * frequency * 0.7f + phase * 1.7f);
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    float dy = y - cy, dz = z - cz;
                    if (dy * dy + dz * dz > RIBBON_RADIUS * RIBBON_RADIUS * (1.4f - 0.6f * random.nextFloat())) continue;
                    set(x, y, z, random.nextFloat() < density && !entries.isEmpty() ? weighted(entries, total, random) + ENTRY_OFFSET : HOST);
                }
            }
        }
    }

    private void blob(List<VeinInfo.Entry> entries, GTOreDefinition definition, RandomSource random) {
        var size = definition.clusterSize();
        float radius = Math.max(BLOCKS_PER_VOXEL, (size.getMinValue() + size.getMaxValue()) / 4f);
        int total = totalWeight(entries);
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    float fx = offset(x, SIZE) / radius, fy = offset(y, HEIGHT) / radius, fz = offset(z, SIZE) / radius;
                    if (fx * fx + fy * fy + fz * fz > 1 - EDGE_JITTER * random.nextFloat()) continue;
                    set(x, y, z, random.nextFloat() < definition.density() && !entries.isEmpty() ? weighted(entries, total, random) + ENTRY_OFFSET : HOST);
                }
            }
        }
    }

    private static float offset(int voxel, int count) {
        return (voxel - count / 2f + 0.5f) * BLOCKS_PER_VOXEL;
    }

    private static int totalWeight(List<VeinInfo.Entry> entries) {
        int total = 0;
        for (var entry : entries) total += entry.weight();
        return Math.max(1, total);
    }

    private static int weighted(List<VeinInfo.Entry> entries, int total, RandomSource random) {
        int roll = random.nextInt(total);
        for (int i = 0; i < entries.size(); i++) {
            roll -= entries.get(i).weight();
            if (roll < 0) return i;
        }
        return Math.max(0, entries.size() - 1);
    }
}
