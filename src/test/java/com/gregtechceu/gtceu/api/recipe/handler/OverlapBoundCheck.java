package com.gregtechceu.gtceu.api.recipe.handler;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * {@link OverlapBound} 的随机对照：与最大流求出的最大可行并行逐例比对，并核对无重叠时等于逐条目计数、贪心规划在层状重叠下可实际分配。
 * 运行：gradlew overlapBoundCheck（失败时抛出 AssertionError，任务以非零退出）。
 */
public final class OverlapBoundCheck {

    private static final long LIMIT = 1L << 40;

    private final SplittableRandom rnd;
    private final OverlapBound bound = new OverlapBound();
    private long exactCases, looseCases, separateCases, segmentCases, laminarGreedy, crossGreedyFail, crossGreedyCases, broadFirstFail, broadFirstCases;

    private OverlapBoundCheck(long seed) {
        rnd = new SplittableRandom(seed);
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261004L;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        var check = new OverlapBoundCheck(seed);
        long t0 = System.nanoTime();
        for (int i = 0; i < rounds; i++) check.randomCase(i);
        for (int i = 0; i < rounds / 4; i++) check.segmentCase(i);
        for (int i = 0; i < rounds / 4; i++) check.laminarCase(i);
        for (int i = 0; i < rounds / 4; i++) check.crossCase(i);
        check.saturationCases();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("OverlapBoundCheck seed=" + seed + " rounds=" + rounds + " exact=" + check.exactCases + " loose=" + check.looseCases + " separate=" + check.separateCases + " segment=" + check.segmentCases +
                " laminarGreedyOk=" + check.laminarGreedy + " crossGreedyFail=" + check.crossGreedyFail + "/" + check.crossGreedyCases + " broadFirstGreedyFail=" + check.broadFirstFail + "/" + check.broadFirstCases + " ms=" + ms);
        System.out.println("OverlapBoundCheck PASS");
    }

    private void randomCase(int id) {
        int k = rnd.nextInt(10) < 8 ? 2 + rnd.nextInt(3) : 5 + rnd.nextInt(4);
        int slots = 1 + rnd.nextInt(k >= 5 ? 14 : 8);
        long[] need = new long[k];
        for (int j = 0; j < k; j++) need[j] = 1 + rnd.nextInt(5);
        long[] mask = new long[slots];
        long[] amount = new long[slots];
        int mode = rnd.nextInt(3);
        for (int s = 0; s < slots; s++) {
            amount[s] = 1 + rnd.nextInt(40);
            mask[s] = switch (mode) {
                case 0 -> 1L << rnd.nextInt(k);
                case 1 -> rnd.nextLong() & ((1L << k) - 1);
                default -> rnd.nextInt(3) == 0 ? rnd.nextLong() & ((1L << k) - 1) : 1L << rnd.nextInt(k);
            };
        }
        long limit = rnd.nextInt(4) == 0 ? 1 + rnd.nextInt(30) : LIMIT;
        verify(id, k, need, mask, amount, limit);
    }

    private void verify(int id, int k, long[] need, long[] mask, long[] amount, long limit) {
        long active = (1L << k) - 1;
        long got = solve(k, need, mask, amount, limit);
        long separate = separate(k, need, mask, amount, limit);
        boolean overlapping = false;
        for (long m : mask) overlapping |= Long.bitCount(m & active) > 1;
        if (got > separate) fail(id, "above separate count", k, need, mask, amount, got, separate);
        if (!overlapping) {
            separateCases++;
            if (got != separate) fail(id, "no-overlap result differs from separate count", k, need, mask, amount, got, separate);
        }
        if (largestComponent(k, mask) <= OverlapBound.EXACT_LIMIT) {
            exactCases++;
            if (got > 0 && !feasible(k, need, mask, amount, got)) fail(id, "exact result not allocatable (max flow)", k, need, mask, amount, got, separate);
        } else {
            looseCases++;
        }
        if (got < limit && feasible(k, need, mask, amount, got + 1)) fail(id, "max flow allocates more than result", k, need, mask, amount, got, separate);
    }

    private long solve(int k, long[] need, long[] mask, long[] amount, long limit) {
        long active = (1L << k) - 1;
        bound.reset(active);
        for (int j = 0; j < k; j++) bound.need[j] = need[j];
        for (int s = 0; s < mask.length; s++) {
            if (mask[s] != 0 && amount[s] > 0) bound.put(mask[s], amount[s]);
        }
        return bound.solve(active, limit);
    }

    private void segmentCase(int id) {
        int k = 2 + rnd.nextInt(3);
        long active = (1L << k) - 1;
        long[] need = new long[k];
        for (int j = 0; j < k; j++) need[j] = 1 + rnd.nextInt(4);
        int arraySlots = rnd.nextInt(4);
        int visited = 1 + rnd.nextInt(5);
        long[] mask = new long[arraySlots + visited];
        long[] amount = new long[arraySlots + visited];
        for (int s = 0; s < mask.length; s++) {
            mask[s] = 1 + (rnd.nextLong() >>> 1) % active;
            amount[s] = 1 + rnd.nextInt(30);
        }
        long[] avail = new long[64];
        for (int s = arraySlots; s < mask.length; s++) {
            for (int j = 0; j < k; j++) {
                if ((mask[s] >>> j & 1) != 0) avail[j] += amount[s];
            }
        }
        boolean consistent = rnd.nextInt(3) != 0;
        if (!consistent) {
            int j = rnd.nextInt(k);
            avail[j] += 1 + rnd.nextInt(10);
        }
        bound.reset(active);
        for (int j = 0; j < k; j++) bound.need[j] = need[j];
        for (int s = 0; s < arraySlots; s++) bound.put(mask[s], amount[s]);
        bound.beginSegment(active);
        for (int s = arraySlots; s < mask.length; s++) bound.putSegment(mask[s], amount[s]);
        bound.closeSegment(active, avail);
        long got = bound.solve(active, LIMIT);
        long expect;
        if (consistent) {
            expect = solve(k, need, mask, amount, LIMIT);
        } else {
            long[] m2 = new long[arraySlots + k];
            long[] a2 = new long[arraySlots + k];
            System.arraycopy(mask, 0, m2, 0, arraySlots);
            System.arraycopy(amount, 0, a2, 0, arraySlots);
            for (int j = 0; j < k; j++) {
                m2[arraySlots + j] = avail[j] > 0 ? 1L << j : 0;
                a2[arraySlots + j] = avail[j];
            }
            expect = solve(k, need, m2, a2, LIMIT);
            long separate = separate(k, need, mask, amount, LIMIT);
            long inflated = separate(k, need, m2, a2, LIMIT);
            if (got > inflated) throw new AssertionError("segment#" + id + " fallback above separate count " + got + " > " + inflated + " (" + separate + ")");
        }
        if (got != expect) throw new AssertionError("segment#" + id + " consistent=" + consistent + " got=" + got + " expect=" + expect + " masks=" + Arrays.toString(mask) + " amounts=" + Arrays.toString(amount) + " avail=" + Arrays.toString(Arrays.copyOf(avail, k)));
        segmentCases++;
    }

    private void laminarCase(int id) {
        int k = 2 + rnd.nextInt(4);
        int items = 2 + rnd.nextInt(6);
        long[] sets = new long[k];
        for (int j = 0; j < k; j++) {
            if (j > 0 && rnd.nextBoolean()) {
                int parent = rnd.nextInt(j);
                long p = sets[parent];
                long sub = p & rnd.nextLong();
                if (sub == 0) sub = Long.lowestOneBit(p);
                sets[j] = rnd.nextBoolean() ? sub : p;
            } else {
                int a = rnd.nextInt(items), b = rnd.nextInt(items);
                int lo = Math.min(a, b), hi = Math.max(a, b);
                sets[j] = ((1L << (hi - lo + 1)) - 1) << lo;
            }
        }
        if (!laminar(sets)) return;
        long[] need = new long[k];
        for (int j = 0; j < k; j++) need[j] = 1 + rnd.nextInt(4);
        int slots = 1 + rnd.nextInt(8);
        int[] item = new int[slots];
        long[] amount = new long[slots];
        long[] mask = new long[slots];
        for (int s = 0; s < slots; s++) {
            item[s] = rnd.nextInt(items);
            amount[s] = 1 + rnd.nextInt(30);
            for (int j = 0; j < k; j++) {
                if ((sets[j] >>> item[s] & 1) != 0) mask[s] |= 1L << j;
            }
        }
        verify(id, k, need, mask, amount, LIMIT);
        long par = solve(k, need, mask, amount, LIMIT);
        if (par == 0) return;
        Integer[] order = new Integer[k];
        for (int j = 0; j < k; j++) order[j] = j;
        Arrays.sort(order, (x, y) -> Integer.compare(Long.bitCount(sets[x]), Long.bitCount(sets[y])));
        if (!greedy(k, need, mask, amount, par, order)) throw new AssertionError("laminar#" + id + " subset-first greedy failed at par=" + par + " sets=" + Arrays.toString(sets) + " masks=" + Arrays.toString(mask) + " amounts=" + Arrays.toString(amount) + " need=" + Arrays.toString(need));
        laminarGreedy++;
        Arrays.sort(order, (x, y) -> Integer.compare(Long.bitCount(sets[y]), Long.bitCount(sets[x])));
        broadFirstCases++;
        if (!greedy(k, need, mask, amount, par, order)) broadFirstFail++;
    }

    private void crossCase(int id) {
        int k = 2 + rnd.nextInt(2);
        int slots = 2 + rnd.nextInt(6);
        long[] need = new long[k];
        for (int j = 0; j < k; j++) need[j] = 1 + rnd.nextInt(4);
        long[] mask = new long[slots];
        long[] amount = new long[slots];
        long active = (1L << k) - 1;
        for (int s = 0; s < slots; s++) {
            mask[s] = 1 + (rnd.nextLong() >>> 1) % active;
            amount[s] = 1 + rnd.nextInt(20);
        }
        verify(id, k, need, mask, amount, LIMIT);
        long par = solve(k, need, mask, amount, LIMIT);
        if (par == 0) return;
        Integer[] order = new Integer[k];
        for (int j = 0; j < k; j++) order[j] = j;
        crossGreedyCases++;
        if (!greedy(k, need, mask, amount, par, order)) crossGreedyFail++;
    }

    private void saturationCases() {
        long max = Long.MAX_VALUE;
        check("sat-shared", 2, new long[] { 1, 1 }, new long[] { 3 }, new long[] { max }, max, max / 2);
        check("sat-two", 2, new long[] { 1, 1 }, new long[] { 1, 3 }, new long[] { max, max }, max, max / 2);
        check("sat-need", 2, new long[] { max, 1 }, new long[] { 3 }, new long[] { max - 1 }, max, 0);
        check("sat-need-inf", 2, new long[] { max, max }, new long[] { 3 }, new long[] { max }, max, 1);
        check("sat-limit", 2, new long[] { 3, 5 }, new long[] { 3, 1 }, new long[] { max, 7 }, 11, 11);
        check("iron-7", 2, new long[] { 1, 1 }, new long[] { 3 }, new long[] { 7 }, LIMIT, 3);
        check("iron-10", 2, new long[] { 1, 1 }, new long[] { 3 }, new long[] { 10 }, LIMIT, 5);
        check("iron-1", 2, new long[] { 1, 1 }, new long[] { 3 }, new long[] { 1 }, LIMIT, 0);
        check("par2-a", 2, new long[] { 1, 2 }, new long[] { 3 }, new long[] { 3_000_000 }, LIMIT, 1_000_000);
        check("par2-b", 2, new long[] { 1, 3 }, new long[] { 3 }, new long[] { 4_000_000 }, LIMIT, 1_000_000);
        check("par2-c", 2, new long[] { 1, 2 }, new long[] { 3 }, new long[] { 3_000_000 }, LIMIT, 1_000_000);
        check("disjoint", 3, new long[] { 2, 3, 4 }, new long[] { 1, 2, 4 }, new long[] { 20, 30, 41 }, LIMIT, 10);
    }

    private void check(String name, int k, long[] need, long[] mask, long[] amount, long limit, long expect) {
        long got = solve(k, need, mask, amount, limit);
        if (got != expect) throw new AssertionError(name + " got=" + got + " expect=" + expect);
        long separate = separate(k, need, mask, amount, limit);
        if (got > separate) throw new AssertionError(name + " above separate count " + got + " > " + separate);
    }

    private static long separate(int k, long[] need, long[] mask, long[] amount, long par) {
        for (int j = 0; j < k; j++) {
            long avail = 0;
            for (int s = 0; s < mask.length; s++) {
                if ((mask[s] >>> j & 1) != 0) avail = sat(avail, amount[s]);
            }
            if (avail < need[j]) return 0;
            long q = avail / need[j];
            if (q < par) par = q;
        }
        return par;
    }

    private static long sat(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    private static int largestComponent(int k, long[] mask) {
        int[] root = new int[k];
        for (int j = 0; j < k; j++) root[j] = j;
        for (long m : mask) {
            int first = -1;
            for (int j = 0; j < k; j++) {
                if ((m >>> j & 1) == 0) continue;
                if (first < 0) first = j;
                else root[find(root, j)] = find(root, first);
            }
        }
        int[] size = new int[k];
        int best = 0;
        for (int j = 0; j < k; j++) best = Math.max(best, ++size[find(root, j)]);
        return best;
    }

    private static int find(int[] root, int j) {
        while (root[j] != j) j = root[j] = root[root[j]];
        return j;
    }

    private static boolean laminar(long[] sets) {
        for (int a = 0; a < sets.length; a++) {
            for (int b = a + 1; b < sets.length; b++) {
                long x = sets[a] & sets[b];
                if (x != 0 && x != sets[a] && x != sets[b]) return false;
            }
        }
        return true;
    }

    private static boolean greedy(int k, long[] need, long[] mask, long[] amount, long par, Integer[] order) {
        long[] left = amount.clone();
        for (int j : order) {
            long want = need[j] * par;
            for (int s = 0; s < mask.length && want > 0; s++) {
                if ((mask[s] >>> j & 1) == 0) continue;
                long t = Math.min(left[s], want);
                left[s] -= t;
                want -= t;
            }
            if (want > 0) return false;
        }
        return true;
    }

    private static boolean feasible(int k, long[] need, long[] mask, long[] amount, long par) {
        int slots = mask.length;
        int nodes = slots + k + 2;
        int src = slots + k, sink = src + 1;
        long[][] cap = new long[nodes][nodes];
        long demand = 0;
        for (int s = 0; s < slots; s++) {
            cap[src][s] = amount[s];
            for (int j = 0; j < k; j++) {
                if ((mask[s] >>> j & 1) != 0) cap[s][slots + j] = Long.MAX_VALUE / 4;
            }
        }
        for (int j = 0; j < k; j++) {
            long d = need[j] * par;
            cap[slots + j][sink] = d;
            demand += d;
        }
        long flow = 0;
        int[] prev = new int[nodes];
        int[] queue = new int[nodes];
        while (true) {
            Arrays.fill(prev, -1);
            prev[src] = src;
            int head = 0, tail = 0;
            queue[tail++] = src;
            while (head < tail && prev[sink] < 0) {
                int u = queue[head++];
                for (int v = 0; v < nodes; v++) {
                    if (prev[v] < 0 && cap[u][v] > 0) {
                        prev[v] = u;
                        queue[tail++] = v;
                    }
                }
            }
            if (prev[sink] < 0) break;
            long push = Long.MAX_VALUE;
            for (int v = sink; v != src; v = prev[v]) push = Math.min(push, cap[prev[v]][v]);
            for (int v = sink; v != src; v = prev[v]) {
                cap[prev[v]][v] -= push;
                cap[v][prev[v]] += push;
            }
            flow += push;
        }
        return flow == demand;
    }

    private static void fail(int id, String what, int k, long[] need, long[] mask, long[] amount, long got, long separate) {
        throw new AssertionError("case#" + id + " " + what + ": got=" + got + " separate=" + separate + " k=" + k + " need=" + Arrays.toString(need) + " masks=" + Arrays.toString(mask) + " amounts=" + Arrays.toString(amount));
    }
}
