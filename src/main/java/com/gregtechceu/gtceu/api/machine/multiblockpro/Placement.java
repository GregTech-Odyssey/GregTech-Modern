package com.gregtechceu.gtceu.api.machine.multiblockpro;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

final class Placement {

    final CompiledPiece piece;
    final int rot;
    final int ox;
    final int oy;
    final int oz;
    int wx, wy, wz;
    int e0x, e0y, e0z;
    int e1x, e1y, e1z;
    int e2x, e2y, e2z;
    int tested;
    final int minX, minY, minZ, maxX, maxY, maxZ;

    Placement(CompiledPiece piece, int rot, int ox, int oy, int oz) {
        this.piece = piece;
        this.rot = rot;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        int[] m = Orientation.ROT[rot];
        int[] b = piece.bounds;
        int ax = m[0] * b[0] + m[1] * b[1] + m[2] * b[2], bx = m[0] * b[3] + m[1] * b[4] + m[2] * b[5];
        int ay = m[3] * b[0] + m[4] * b[1] + m[5] * b[2], by = m[3] * b[3] + m[4] * b[4] + m[5] * b[5];
        int az = m[6] * b[0] + m[7] * b[1] + m[8] * b[2], bz = m[6] * b[3] + m[7] * b[4] + m[8] * b[5];
        this.minX = ox + Math.min(ax, bx);
        this.maxX = ox + Math.max(ax, bx);
        this.minY = oy + Math.min(ay, by);
        this.maxY = oy + Math.max(ay, by);
        this.minZ = oz + Math.min(az, bz);
        this.maxZ = oz + Math.max(az, bz);
    }

    boolean intersects(Placement other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    static Placement root(CompiledPiece root) {
        int c = root.controller;
        return new Placement(root, 0, -root.x(c), -root.y(c), -root.z(c));
    }

    @Nullable
    Placement attach(Piece.Port hostPort, CompiledPiece child, Piece.Port childPort) {
        int[] m = Orientation.ROT[rot];
        int[] dirs = Orientation.ROT_DIR[rot];
        int facing = dirs[hostPort.facing()];
        int r = Orientation.align(childPort.facing(), childPort.up(), Orientation.OPPOSITE[facing], dirs[hostPort.up()]);
        if (r < 0) return null;
        int[] step = Orientation.VEC[facing];
        int tx = ox + m[0] * hostPort.x() + m[1] * hostPort.y() + m[2] * hostPort.z() + step[0];
        int ty = oy + m[3] * hostPort.x() + m[4] * hostPort.y() + m[5] * hostPort.z() + step[1];
        int tz = oz + m[6] * hostPort.x() + m[7] * hostPort.y() + m[8] * hostPort.z() + step[2];
        int[] c = Orientation.ROT[r];
        return new Placement(child, r,
                tx - (c[0] * childPort.x() + c[1] * childPort.y() + c[2] * childPort.z()),
                ty - (c[3] * childPort.x() + c[4] * childPort.y() + c[5] * childPort.z()),
                tz - (c[6] * childPort.x() + c[7] * childPort.y() + c[8] * childPort.z()));
    }

    int relX(int cell) {
        int[] m = Orientation.ROT[rot];
        return ox + m[0] * piece.x(cell) + m[1] * piece.y(cell) + m[2] * piece.z(cell);
    }

    int relY(int cell) {
        int[] m = Orientation.ROT[rot];
        return oy + m[3] * piece.x(cell) + m[4] * piece.y(cell) + m[5] * piece.z(cell);
    }

    int relZ(int cell) {
        int[] m = Orientation.ROT[rot];
        return oz + m[6] * piece.x(cell) + m[7] * piece.y(cell) + m[8] * piece.z(cell);
    }

    void toWorld(int[] w, int cx, int cy, int cz) {
        int[] m = Orientation.ROT[rot];
        wx = cx + ox * w[0] + oy * w[3] + oz * w[6];
        wy = cy + ox * w[1] + oy * w[4] + oz * w[7];
        wz = cz + ox * w[2] + oy * w[5] + oz * w[8];
        e0x = m[0] * w[0] + m[3] * w[3] + m[6] * w[6];
        e0y = m[0] * w[1] + m[3] * w[4] + m[6] * w[7];
        e0z = m[0] * w[2] + m[3] * w[5] + m[6] * w[8];
        e1x = m[1] * w[0] + m[4] * w[3] + m[7] * w[6];
        e1y = m[1] * w[1] + m[4] * w[4] + m[7] * w[7];
        e1z = m[1] * w[2] + m[4] * w[5] + m[7] * w[8];
        e2x = m[2] * w[0] + m[5] * w[3] + m[8] * w[6];
        e2y = m[2] * w[1] + m[5] * w[4] + m[8] * w[7];
        e2z = m[2] * w[2] + m[5] * w[5] + m[8] * w[8];
    }

    BlockPos worldPos(int x, int y, int z) {
        return new BlockPos(wx + x * e0x + y * e1x + z * e2x, wy + x * e0y + y * e1y + z * e2y, wz + x * e0z + y * e1z + z * e2z);
    }

    int worldX(int cell) {
        return wx + piece.x(cell) * e0x + piece.y(cell) * e1x + piece.z(cell) * e2x;
    }

    int worldY(int cell) {
        return wy + piece.x(cell) * e0y + piece.y(cell) * e1y + piece.z(cell) * e2y;
    }

    int worldZ(int cell) {
        return wz + piece.x(cell) * e0z + piece.y(cell) * e1z + piece.z(cell) * e2z;
    }
}
