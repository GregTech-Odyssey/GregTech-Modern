package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Orientation {

    static final int UP = RelativeDirection.UP.ordinal();
    static final int DOWN = RelativeDirection.DOWN.ordinal();
    static final int LEFT = RelativeDirection.LEFT.ordinal();
    static final int RIGHT = RelativeDirection.RIGHT.ordinal();
    static final int FRONT = RelativeDirection.FRONT.ordinal();
    static final int BACK = RelativeDirection.BACK.ordinal();

    static final int[][] VEC = new int[6][];
    static final int[] OPPOSITE = new int[6];
    static final int[][] ROT;
    static final int[][] ROT_DIR;

    private static final byte[] ALIGN = new byte[6 * 6 * 6 * 6];
    private static final int[][] WORLD = new int[6 * 6 * 2][];

    static {
        VEC[UP] = new int[] { 0, 1, 0 };
        VEC[DOWN] = new int[] { 0, -1, 0 };
        VEC[LEFT] = new int[] { -1, 0, 0 };
        VEC[RIGHT] = new int[] { 1, 0, 0 };
        VEC[FRONT] = new int[] { 0, 0, -1 };
        VEC[BACK] = new int[] { 0, 0, 1 };
        for (var dir : RelativeDirection.values()) {
            OPPOSITE[dir.ordinal()] = dir.getOpposite().ordinal();
        }
        List<int[]> rotations = new ArrayList<>(24);
        rotations.add(new int[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 });
        int[][] permutations = { { 0, 1, 2 }, { 0, 2, 1 }, { 1, 0, 2 }, { 1, 2, 0 }, { 2, 0, 1 }, { 2, 1, 0 } };
        int[] parities = { 1, -1, -1, 1, 1, -1 };
        for (int p = 0; p < permutations.length; p++) {
            for (int signs = 0; signs < 8; signs++) {
                int[] m = new int[9];
                int product = parities[p];
                for (int row = 0; row < 3; row++) {
                    int sign = (signs >> row & 1) == 0 ? 1 : -1;
                    m[row * 3 + permutations[p][row]] = sign;
                    product *= sign;
                }
                if (product == 1 && !Arrays.equals(m, rotations.getFirst())) rotations.add(m);
            }
        }
        ROT = rotations.toArray(int[][]::new);
        ROT_DIR = new int[ROT.length][6];
        Arrays.fill(ALIGN, (byte) -1);
        for (int r = 0; r < ROT.length; r++) {
            for (int d = 0; d < 6; d++) {
                ROT_DIR[r][d] = dirOf(apply(ROT[r], VEC[d]));
            }
        }
        for (int r = ROT.length - 1; r >= 0; r--) {
            for (int facing = 0; facing < 6; facing++) {
                for (int up = 0; up < 6; up++) {
                    if (up == facing || up == OPPOSITE[facing]) continue;
                    ALIGN[index(facing, up, ROT_DIR[r][facing], ROT_DIR[r][up])] = (byte) r;
                }
            }
        }
        for (var facing : Direction.values()) {
            for (var up : Direction.values()) {
                for (int flip = 0; flip < 2; flip++) {
                    WORLD[(facing.ordinal() * 6 + up.ordinal()) * 2 + flip] = basis(facing, up, flip == 1);
                }
            }
        }
    }

    private Orientation() {}

    private static int index(int facing, int up, int targetFacing, int targetUp) {
        return ((facing * 6 + up) * 6 + targetFacing) * 6 + targetUp;
    }

    private static int[] apply(int[] m, int[] v) {
        return new int[] {
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2] };
    }

    private static int dirOf(int[] v) {
        for (int d = 0; d < 6; d++) {
            if (Arrays.equals(VEC[d], v)) return d;
        }
        throw new IllegalStateException();
    }

    static int align(int facing, int up, int targetFacing, int targetUp) {
        return ALIGN[index(facing, up, targetFacing, targetUp)];
    }

    static int upFor(int facing) {
        return facing == UP || facing == DOWN ? FRONT : UP;
    }

    static int[] world(Direction facing, Direction up, boolean flip) {
        return WORLD[(facing.ordinal() * 6 + up.ordinal()) * 2 + (flip ? 1 : 0)];
    }

    private static int[] basis(Direction facing, Direction up, boolean flip) {
        int[] basis = new int[9];
        unit(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT, facing, up, flip, basis, 0);
        unit(RelativeDirection.UP, RelativeDirection.LEFT, RelativeDirection.FRONT, facing, up, flip, basis, 3);
        unit(RelativeDirection.BACK, RelativeDirection.LEFT, RelativeDirection.UP, facing, up, flip, basis, 6);
        assert consistent(basis, facing, up, flip);
        return basis;
    }

    private static void unit(RelativeDirection dir, RelativeDirection second, RelativeDirection third, Direction facing, Direction up, boolean flip, int[] out, int offset) {
        int[] c1 = new int[3];
        BlockPattern.relativeToWorld(new RelativeDirection[] { dir, second, third }, 1, 0, 0, facing, up, flip, c1);
        System.arraycopy(c1, 0, out, offset, 3);
    }

    private static boolean consistent(int[] basis, Direction facing, Direction up, boolean flip) {
        int[] check = new int[9];
        unit(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT, facing, up, flip, check, 0);
        unit(RelativeDirection.DOWN, RelativeDirection.LEFT, RelativeDirection.FRONT, facing, up, flip, check, 3);
        unit(RelativeDirection.FRONT, RelativeDirection.LEFT, RelativeDirection.UP, facing, up, flip, check, 6);
        for (int i = 0; i < 9; i++) {
            if (check[i] != -basis[i]) return false;
        }
        return true;
    }
}
