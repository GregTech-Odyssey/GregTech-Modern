package com.gregtechceu.gtceu.uiwidgets.structure;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

@OnlyIn(Dist.CLIENT)
final class PreviewBounds {

    int minX, minY, minZ, maxX, maxY, maxZ;
    boolean empty = true;

    static PreviewBounds of(Long2ObjectOpenHashMap<?> blocks) {
        var bounds = new PreviewBounds();
        bounds.measure(blocks);
        return bounds;
    }

    void measure(Long2ObjectOpenHashMap<?> blocks) {
        empty = blocks.isEmpty();
        if (empty) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
            return;
        }
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (var it = blocks.keySet().iterator(); it.hasNext();) {
            long pos = it.nextLong();
            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
    }

    int width() {
        return maxX - minX + 1;
    }

    int height() {
        return maxY - minY + 1;
    }

    int depth() {
        return maxZ - minZ + 1;
    }
}
