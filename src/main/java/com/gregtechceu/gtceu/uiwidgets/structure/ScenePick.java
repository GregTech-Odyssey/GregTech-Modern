package com.gregtechceu.gtceu.uiwidgets.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
final class ScenePick {

    private static final double ENTRY_EPSILON = 1e-7;
    private static final double PARALLEL_EPSILON = 1e-12;
    private static final int EXTRA_STEPS = 4;

    private ScenePick() {}

    @Nullable
    static BlockHitResult pick(PreviewLevel.View view, PreviewBounds bounds, Vector3f from, Vector3f to) {
        int minX = bounds.minX, minY = bounds.minY, minZ = bounds.minZ, maxX = bounds.maxX, maxY = bounds.maxY, maxZ = bounds.maxZ;
        double dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        double[] range = { 0, 1 };
        if (!slab(from.x(), dx, minX, maxX + 1, range) || !slab(from.y(), dy, minY, maxY + 1, range) ||
                !slab(from.z(), dz, minZ, maxZ + 1, range))
            return null;
        double t = range[0] + ENTRY_EPSILON;
        int x = Mth.clamp(Mth.floor(from.x() + dx * t), minX, maxX);
        int y = Mth.clamp(Mth.floor(from.y() + dy * t), minY, maxY);
        int z = Mth.clamp(Mth.floor(from.z() + dz * t), minZ, maxZ);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tMaxX = dx == 0 ? Double.MAX_VALUE : ((stepX > 0 ? x + 1 : x) - from.x()) / dx;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : ((stepY > 0 ? y + 1 : y) - from.y()) / dy;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? z + 1 : z) - from.z()) / dz;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        var start = new Vec3(from.x(), from.y(), from.z());
        var end = new Vec3(to.x(), to.y(), to.z());
        int limit = (maxX - minX) + (maxY - minY) + (maxZ - minZ) + EXTRA_STEPS;
        for (int i = 0; i < limit; i++) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return null;
            var state = view.get(BlockPos.asLong(x, y, z));
            if (!state.isAir()) {
                var pos = new BlockPos(x, y, z);
                var shape = state.getShape(view, pos);
                if (!shape.isEmpty()) {
                    var hit = shape.clip(start, end, pos);
                    if (hit != null) return hit;
                }
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > range[1]) return null;
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > range[1]) return null;
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > range[1]) return null;
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    private static boolean slab(double origin, double delta, double min, double max, double[] range) {
        if (Math.abs(delta) < PARALLEL_EPSILON) return origin >= min && origin <= max;
        double a = (min - origin) / delta, b = (max - origin) / delta;
        if (a > b) {
            double swap = a;
            a = b;
            b = swap;
        }
        range[0] = Math.max(range[0], a);
        range[1] = Math.min(range[1], b);
        return range[0] <= range[1];
    }
}
