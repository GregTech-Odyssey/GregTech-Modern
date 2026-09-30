package com.gregtechceu.gtceu.api.pattern;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class FormedCells {

    public static final FormedCells EMPTY = new FormedCells(0, 0, 0, 0, 0, 0, 0, 0, null, null);

    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;
    private final int bitsY, bitsZ;
    private final long[] bits;
    private final int[] packed;

    private FormedCells(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, int bitsY, int bitsZ, long[] bits, int[] packed) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.bitsY = bitsY;
        this.bitsZ = bitsZ;
        this.bits = bits;
        this.packed = packed;
    }

    @Nullable
    public static FormedCells of(LongCollection positions) {
        if (positions.isEmpty()) return EMPTY;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var it = positions.iterator(); it.hasNext();) {
            long pos = it.nextLong();
            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;
        int bitsX = bitsFor(sizeX), bitsY = bitsFor(sizeY), bitsZ = bitsFor(sizeZ);
        long volume = (long) sizeX * sizeY * sizeZ;
        long bitsetWords = (volume + 63) >>> 6;
        boolean packable = bitsX + bitsY + bitsZ <= 31;
        if (volume <= Integer.MAX_VALUE && (!packable || bitsetWords * 2 <= positions.size())) {
            var words = new long[(int) bitsetWords];
            for (var it = positions.iterator(); it.hasNext();) {
                long pos = it.nextLong();
                long index = ((long) (BlockPos.getX(pos) - minX) * sizeY + (BlockPos.getY(pos) - minY)) * sizeZ + (BlockPos.getZ(pos) - minZ);
                words[(int) (index >>> 6)] |= 1L << index;
            }
            return new FormedCells(minX, minY, minZ, sizeX, sizeY, sizeZ, bitsY, bitsZ, words, null);
        }
        if (!packable) return null;
        var array = new int[positions.size()];
        int i = 0;
        for (var it = positions.iterator(); it.hasNext();) {
            long pos = it.nextLong();
            array[i++] = pack(BlockPos.getX(pos) - minX, BlockPos.getY(pos) - minY, BlockPos.getZ(pos) - minZ, bitsY, bitsZ);
        }
        Arrays.sort(array);
        return new FormedCells(minX, minY, minZ, sizeX, sizeY, sizeZ, bitsY, bitsZ, null, array);
    }

    private static int bitsFor(int size) {
        return size <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(size - 1);
    }

    private static int pack(int x, int y, int z, int bitsY, int bitsZ) {
        return (x << (bitsY + bitsZ)) | (y << bitsZ) | z;
    }

    public boolean contains(long pos) {
        if (sizeX == 0) return false;
        int x = BlockPos.getX(pos) - minX;
        int y = BlockPos.getY(pos) - minY;
        int z = BlockPos.getZ(pos) - minZ;
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return false;
        if (bits != null) {
            long index = ((long) x * sizeY + y) * sizeZ + z;
            return (bits[(int) (index >>> 6)] & (1L << index)) != 0;
        }
        return Arrays.binarySearch(packed, pack(x, y, z, bitsY, bitsZ)) >= 0;
    }

    public void forEach(LongConsumer consumer) {
        if (sizeX == 0) return;
        if (bits != null) {
            for (int word = 0; word < bits.length; word++) {
                long value = bits[word];
                while (value != 0) {
                    int bit = Long.numberOfTrailingZeros(value);
                    value &= value - 1;
                    long index = ((long) word << 6) + bit;
                    int z = (int) (index % sizeZ);
                    long rest = index / sizeZ;
                    int y = (int) (rest % sizeY);
                    int x = (int) (rest / sizeY);
                    consumer.accept(BlockPos.asLong(x + minX, y + minY, z + minZ));
                }
            }
            return;
        }
        int maskY = (1 << bitsY) - 1, maskZ = (1 << bitsZ) - 1;
        for (int value : packed) {
            consumer.accept(BlockPos.asLong((value >>> (bitsY + bitsZ)) + minX, ((value >>> bitsZ) & maskY) + minY, (value & maskZ) + minZ));
        }
    }

    public int size() {
        if (sizeX == 0) return 0;
        if (packed != null) return packed.length;
        int count = 0;
        for (long word : bits) count += Long.bitCount(word);
        return count;
    }

    public int getWidth() {
        return sizeX;
    }

    public int getHeight() {
        return sizeY;
    }

    public int getDepth() {
        return sizeZ;
    }
}
