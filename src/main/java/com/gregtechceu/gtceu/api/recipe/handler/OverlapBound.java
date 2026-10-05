package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.transfer.key.Keys;

import java.util.Arrays;

/**
 * 重叠条目的并行上限：只被一个条目接受的库存直接计入该条目，被多个条目接受的库存按掩码汇总，按实际重叠的连通分量逐子集套用 Hall 定理；
 * 分量不超过 EXACT_LIMIT 个条目时精确，更大的分量只取单条目约束与整体并集约束。
 */
class OverlapBound {

    static final int EXACT_LIMIT = 6;

    final long[] need = new long[64];
    final long[] singles = new long[64];
    long[] masks = new long[16];
    long[] amounts = new long[16];
    int size;
    private final long[] segmentSingles = new long[64];
    private int segment;
    private final long[] comps = new long[64];
    private final long[] localNeed = new long[EXACT_LIMIT];
    private final long[] hist = new long[1 << EXACT_LIMIT];
    private final long[] subsetNeed = new long[1 << EXACT_LIMIT];
    private final int[] present = new int[1 << EXACT_LIMIT];

    final void reset(long active) {
        size = 0;
        long[] ss = singles;
        for (long r = active; r != 0; r &= r - 1) ss[Long.numberOfTrailingZeros(r)] = 0;
    }

    final void put(long mask, long amount) {
        if ((mask & (mask - 1)) == 0) {
            int j = Long.numberOfTrailingZeros(mask);
            singles[j] = Keys.add(singles[j], amount);
        } else {
            addShared(mask, amount, 0);
        }
    }

    final void beginSegment(long active) {
        segment = size;
        long[] ss = segmentSingles;
        for (long r = active; r != 0; r &= r - 1) ss[Long.numberOfTrailingZeros(r)] = 0;
    }

    final void putSegment(long mask, long amount) {
        if ((mask & (mask - 1)) == 0) {
            int j = Long.numberOfTrailingZeros(mask);
            segmentSingles[j] = Keys.add(segmentSingles[j], amount);
        } else {
            addShared(mask, amount, segment);
        }
    }

    final void closeSegment(long active, long[] avail) {
        int from = segment;
        int end = size;
        long[] ms = masks;
        long[] as = amounts;
        long[] seg = segmentSingles;
        boolean consistent = true;
        for (long r = active; r != 0 && consistent; r &= r - 1) {
            int j = Long.numberOfTrailingZeros(r);
            long bit = r & -r;
            long sum = seg[j];
            for (int t = from; t < end; t++) {
                if ((ms[t] & bit) != 0) sum = Keys.add(sum, as[t]);
            }
            consistent = sum == avail[j];
        }
        size = from;
        long[] ss = singles;
        if (consistent) {
            for (long r = active; r != 0; r &= r - 1) {
                int j = Long.numberOfTrailingZeros(r);
                ss[j] = Keys.add(ss[j], seg[j]);
            }
            for (int t = from; t < end; t++) addShared(ms[t], as[t], 0);
            return;
        }
        for (long r = active; r != 0; r &= r - 1) {
            int j = Long.numberOfTrailingZeros(r);
            ss[j] = Keys.add(ss[j], avail[j]);
        }
    }

    private void addShared(long mask, long amount, int from) {
        long[] ms = masks;
        int n = size;
        for (int t = from; t < n; t++) {
            if (ms[t] == mask) {
                amounts[t] = Keys.add(amounts[t], amount);
                return;
            }
        }
        if (n == ms.length) {
            masks = ms = Arrays.copyOf(ms, n * 2);
            amounts = Arrays.copyOf(amounts, n * 2);
        }
        ms[n] = mask;
        amounts[n] = amount;
        size = n + 1;
    }

    final long solve(long active, long par) {
        int n = size;
        long[] ss = singles;
        long[] nd = need;
        if (n == 0) {
            for (long r = active; r != 0; r &= r - 1) {
                int j = Long.numberOfTrailingZeros(r);
                long q = ss[j] / nd[j];
                if (q < par) {
                    if (q == 0) return 0;
                    par = q;
                }
            }
            return par;
        }
        long[] ms = masks;
        long[] cs = comps;
        int cc = 0;
        for (int t = 0; t < n; t++) {
            long merged = ms[t];
            int w = 0;
            for (int c = 0; c < cc; c++) {
                long x = cs[c];
                if ((x & merged) != 0) merged |= x;
                else cs[w++] = x;
            }
            cs[w++] = merged;
            cc = w;
        }
        long covered = 0;
        for (int c = 0; c < cc; c++) covered |= cs[c];
        for (long r = active & ~covered; r != 0; r &= r - 1) {
            int j = Long.numberOfTrailingZeros(r);
            long q = ss[j] / nd[j];
            if (q < par) {
                if (q == 0) return 0;
                par = q;
            }
        }
        for (int c = 0; c < cc; c++) {
            long comp = cs[c];
            par = Long.bitCount(comp) <= EXACT_LIMIT ? exact(comp, par) : loose(comp, par);
            if (par == 0) return 0;
        }
        return par;
    }

    private long exact(long comp, long par) {
        int full = 1 << Long.bitCount(comp);
        long[] hs = hist;
        Arrays.fill(hs, 0, full, 0L);
        long[] ss = singles;
        long[] ln = localNeed;
        int b = 0;
        for (long r = comp; r != 0; r &= r - 1, b++) {
            int j = Long.numberOfTrailingZeros(r);
            ln[b] = need[j];
            hs[1 << b] = ss[j];
        }
        long[] ms = masks;
        long[] as = amounts;
        for (int t = 0, n = size; t < n; t++) {
            long m = ms[t] & comp;
            if (m != 0) {
                int l = (int) Long.compress(m, comp);
                hs[l] = Keys.add(hs[l], as[t]);
            }
        }
        int[] nz = present;
        int np = 0;
        for (int l = 1; l < full; l++) {
            if (hs[l] != 0) nz[np++] = l;
        }
        long[] sn = subsetNeed;
        sn[0] = 0;
        for (int s = 1; s < full; s++) {
            long nd = Keys.add(sn[s & (s - 1)], ln[Integer.numberOfTrailingZeros(s)]);
            sn[s] = nd;
            long u = 0;
            for (int x = 0; x < np; x++) {
                int l = nz[x];
                if ((l & s) != 0) u = Keys.add(u, hs[l]);
            }
            long q = u / nd;
            if (q < par) {
                if (q == 0) return 0;
                par = q;
            }
        }
        return par;
    }

    private long loose(long comp, long par) {
        long[] ms = masks;
        long[] as = amounts;
        long[] ss = singles;
        int n = size;
        long union = 0;
        long total = 0;
        for (long r = comp; r != 0; r &= r - 1) {
            int j = Long.numberOfTrailingZeros(r);
            long bit = r & -r;
            long sum = ss[j];
            union = Keys.add(union, sum);
            for (int t = 0; t < n; t++) {
                if ((ms[t] & bit) != 0) sum = Keys.add(sum, as[t]);
            }
            long q = sum / need[j];
            if (q < par) {
                if (q == 0) return 0;
                par = q;
            }
            total = Keys.add(total, need[j]);
        }
        for (int t = 0; t < n; t++) {
            if ((ms[t] & comp) != 0) union = Keys.add(union, as[t]);
        }
        long q = union / total;
        return q < par ? q : par;
    }
}
