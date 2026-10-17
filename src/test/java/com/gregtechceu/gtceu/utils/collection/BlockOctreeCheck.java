package com.gregtechceu.gtceu.utils.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * {@link BlockOctree} 的确定性自检（不依赖游戏、不依赖 JUnit，跑法与 {@code OverlapBoundCheck} 一致）。
 *
 * <p>
 * 分两部分：
 * <ol>
 * <li><b>定点回归</b>：按上一版实现的四类历史缺陷各放一条用例——分裂后条目丢失、y 字段符号扩展、
 * 单层 y（yRange=1）与超高 y（yRange&gt;4096）的解码、负坐标细分退化。<b>这些用例在旧实现上全部失败。</b></li>
 * <li><b>随机对拍</b>：把增/删/查、球形查询、findAny、forEach 与一个 {@link HashMap} 暴力参照模型逐条对齐，
 * 覆盖多种区域形状（含负坐标、单格、扁平单层、超高）与多种叶子容量（1/2/4/8/16）。</li>
 * </ol>
 *
 * <p>
 * 跑法：
 *
 * <pre>{@code
 * ./gradlew blockOctreeCheck
 * // 或手工：
 * javac -encoding UTF-8 -d out src/main/java/com/gregtechceu/gtceu/utils/collection/BlockOctree.java \
 *       src/test/java/com/gregtechceu/gtceu/utils/collection/BlockOctreeCheck.java
 * java -Dfile.encoding=UTF-8 -cp out com.gregtechceu.gtceu.utils.collection.BlockOctreeCheck
 * }</pre>
 */
public final class BlockOctreeCheck {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        regressionCases();
        boundsAndDuplicates();
        filterAndFindAny();
        randomizedAgainstReference();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.printf("ALL OK - %d checks passed%n", passed);
            return;
        }
        System.out.printf("%d passed, %d FAILED%n", passed, failures.size());
        for (String f : failures) System.out.println("  FAIL  " + f);
        System.exit(1);
    }

    // ------------------------------------------------------------------ assertions

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failures.add(what);
        }
    }

    private static void checkEq(String what, Object expected, Object actual) {
        check(what + " (expected " + expected + ", got " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }

    private static void fail(String message) {
        if (failures.size() < 40) failures.add(message);
    }

    // ------------------------------------------------------------------ 1. regressions

    private static void regressionCases() {
        System.out.println("== regression cases ==");

        // 1) 分裂后所有条目仍可查到（旧实现第 9 条插入即开始丢数据）
        BlockOctree<Integer> t = new BlockOctree<>(0, 15, 0, 15);
        List<int[]> pts = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    if (!t.insert(x, y, z, pts.size())) fail("insert (" + x + "," + y + "," + z + ") rejected");
                    pts.add(new int[] { x, y, z });
                }
            }
        }
        int found = 0;
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            Integer v = t.get(p[0], p[1], p[2]);
            if (v != null && v == i) found++;
        }
        checkEq("all 64 entries survive splitting", 64, found);
        checkEq("size() after splitting", 64, t.size());

        // 2) y 落在 yBits 字段高半区（旧实现符号扩展，查询全错）
        BlockOctree<Integer> ty = new BlockOctree<>(0, 15, 0, 15);
        for (int y = 0; y < 16; y++) ty.insert(0, y, 0, y);
        int hits = 0;
        for (int y = 0; y < 16; y++) hits += ty.querySphere(0, y, 0, 0, null, new ArrayList<Integer>());
        checkEq("querySphere finds every y layer in a 16-tall box", 16, hits);

        // 3) 单层 y（yRange = 1）与超高 y（yRange = 8193）：旧实现在这两处移位越界
        BlockOctree<Integer> flat = new BlockOctree<>(0, 15, 0, 0);
        for (int i = 0; i < 8; i++) flat.insert(i, 0, i, i);
        int flatHits = 0;
        for (int i = 0; i < 8; i++) flatHits += flat.querySphere(i, 0, i, 0, null, new ArrayList<Integer>());
        checkEq("single-layer (yRange=1) sphere query", 8, flatHits);

        BlockOctree<Integer> tall = new BlockOctree<>(0, 1 << 20, 0, 8192);
        int[][] tallPts = { { 0, 0, 0 }, { 1000, 4000, 1000 }, { 5, 8192, 5 }, { 7, 1, 9 } };
        for (int i = 0; i < tallPts.length; i++) {
            tall.insert(tallPts[i][0], tallPts[i][1], tallPts[i][2], i);
        }
        int tallHits = 0;
        for (int[] p : tallPts) {
            tallHits += tall.querySphere(p[0], p[1], p[2], 0, null, new ArrayList<Integer>());
        }
        checkEq("tall region (yRange=8193) sphere query", tallPts.length, tallHits);

        // 4) 负坐标细分（旧实现 mid 用无符号右移，整棵树退化）
        BlockOctree<Integer> neg = new BlockOctree<>(-8, 7, -8, 7);
        for (int x = -8; x <= 7; x += 3) {
            for (int z = -8; z <= 7; z += 3) neg.insert(x, 0, z, x * 100 + z);
        }
        int negFound = 0;
        for (int x = -8; x <= 7; x += 3) {
            for (int z = -8; z <= 7; z += 3) {
                Integer v = neg.get(x, 0, z);
                if (v != null && v == x * 100 + z) negFound++;
            }
        }
        checkEq("negative-coordinate subtree keeps every entry", 36, negFound);
    }

    // ------------------------------------------------------------------ 2. API contract

    private static void boundsAndDuplicates() {
        System.out.println("== bounds / duplicates / clear ==");

        BlockOctree<Integer> t = new BlockOctree<>(0, 15, 0, 15);
        check("first insert accepted", t.insert(1, 2, 3, 10));
        check("duplicate insert rejected", !t.insert(1, 2, 3, 11));
        checkEq("duplicate does not overwrite", 10, t.get(1, 2, 3));
        checkEq("size after duplicate attempt", 1, t.size());
        check("out-of-bounds insert rejected", !t.insert(99, 0, 0, 1));
        checkEq("out-of-bounds get is null", null, t.get(99, 0, 0));
        checkEq("out-of-bounds remove is null", null, t.remove(99, 0, 0));
        checkEq("absent position", null, t.get(5, 5, 5));
        check("contains on absent position", !t.contains(5, 5, 5));
        checkEq("remove returns the value", 10, t.remove(1, 2, 3));
        checkEq("size after remove", 0, t.size());
        check("re-insert after remove", t.insert(1, 2, 3, 12));

        // null 值不允许：null 表示「不存在」
        boolean threw = false;
        try {
            t.insert(4, 4, 4, null);
        } catch (NullPointerException expected) {
            threw = true;
        }
        check("insert(null) is rejected", threw);

        // 单格区域（shift == 0）
        BlockOctree<Integer> one = new BlockOctree<>(7, 7, 7, 7, 7, 7);
        check("single-cell insert", one.insert(7, 7, 7, 1));
        checkEq("single-cell size", 1, one.size());
        check("single-cell duplicate rejected", !one.insert(7, 7, 7, 2));
        checkEq("single-cell querySphere", 1, one.querySphere(7, 7, 7, 0, null, new ArrayList<Integer>()));

        t.clear();
        checkEq("size after clear", 0, t.size());
        checkEq("get after clear", null, t.get(1, 2, 3));
        check("reuse after clear", t.insert(1, 2, 3, 13));
        checkEq("negative radius yields nothing", 0, t.querySphere(0, 0, 0, -1, null, new ArrayList<Integer>()));
        checkEq("findAny on empty tree", null, new BlockOctree<Integer>(0, 0, 0, 0, 0, 0).findAny(v -> true));
        checkEq("compact() on empty tree", 0, new BlockOctree<Integer>(0, 15, 0, 15).compact());

        // compact() must be semantically invisible, and the tree must stay usable afterwards
        BlockOctree<Integer> packed = new BlockOctree<>(0, 15, 0, 15);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) packed.insert(x, 7, z, x * 100 + z);
        }
        int packedBefore = packed.size();
        checkEq("compact() returns the entry count", packedBefore, packed.compact());
        checkEq("size unchanged by compact()", packedBefore, packed.size());
        int survived = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Integer v = packed.get(x, 7, z);
                if (v != null && v == x * 100 + z) survived++;
            }
        }
        checkEq("every entry survives compact()", 256, survived);
        checkEq("query after compact()", 256, packed.querySphere(8, 7, 8, 64, null, new ArrayList<Integer>()));
        check("duplicate rejected after compact()", !packed.insert(3, 7, 3, 4242));
        check("insert new position after compact()", packed.insert(3, 8, 3, 4242));
        checkEq("remove after compact()", 4242, packed.remove(3, 8, 3));
        checkEq("size after post-compact edits", 256, packed.size());
        checkEq("compact() twice is stable", 256, packed.compact());
    }

    // ------------------------------------------------------------------ 3. filter / findAny

    private static void filterAndFindAny() {
        System.out.println("== Predicate filter / findAny ==");

        BlockOctree<String> t = new BlockOctree<>(0, 31, 0, 31);
        String[] names = { "stone", "dirt", "grass", "ore_iron", "ore_gold", "water", "lava", "air" };
        int[][] pos = { { 0, 0, 0 }, { 1, 0, 0 }, { 2, 0, 0 }, { 5, 5, 5 }, { 6, 5, 5 }, { 10, 0, 0 }, { 11, 0, 0 },
                { 20, 20, 20 } };
        for (int i = 0; i < names.length; i++) t.insert(pos[i][0], pos[i][1], pos[i][2], names[i]);

        Predicate<String> isOre = v -> v.startsWith("ore_");
        List<String> out = new ArrayList<>();
        checkEq("filtered query returns only ores", 2, t.querySphere(5, 5, 5, 3, isOre, out));
        check("filtered query content",
                out.contains("ore_iron") && out.contains("ore_gold") && !out.contains("water"));

        List<String> all = new ArrayList<>();
        int n = t.querySphere(5, 5, 5, 3, null, all);
        checkEq("null filter accepts everything", 2, n);
        checkEq("null filter collects the same count", all.size(), n);

        String anyOre = t.findAny(5, 5, 5, 3, isOre);
        check("findAny with filter returns an ore",
                "ore_iron".equals(anyOre) || "ore_gold".equals(anyOre));
        check("findAny without filter finds something", t.findAny(0, 0, 0, 0) != null);
        checkEq("findAny miss returns null", null, t.findAny(5, 5, 5, 3, v -> v.equals("bedrock")));
        checkEq("findAny ignores entries outside the sphere", null, t.findAny(20, 20, 20, 2, isOre));
        checkEq("whole-tree findAny", "water", t.findAny(v -> v.equals("water")));
        checkEq("whole-tree findAny miss", null, t.findAny(v -> v.equals("bedrock")));

        // 找到即返回：谓词只应被调用一次
        int[] calls = { 0 };
        t.findAny(5, 5, 5, 3, v -> {
            calls[0]++;
            return true;
        });
        check("findAny stops at the first match (" + calls[0] + " predicate calls)", calls[0] == 1);

        Set<String> seen = new HashSet<>();
        t.forEach(seen::add);
        checkEq("forEach visits every value", names.length, seen.size());
    }

    // ------------------------------------------------------------------ 4. randomized

    private static void randomizedAgainstReference() {
        System.out.println("== randomized cross-check against HashMap reference ==");

        // {minX, minY, minZ, maxX, maxY, maxZ}
        int[][] regions = {
                { 0, 0, 0, 7, 7, 7 },
                { 0, 0, 0, 31, 31, 31 },
                { -16, -16, -16, 15, 15, 15 },
                { -512, 0, -512, 511, 63, 511 },
                { 0, 12, 0, 15, 12, 15 },
                { -4, -1000, -4, 3, 1000, 3 },
                { 1000, 2000, 1000, 1015, 2015, 1015 },
        };
        int[] capacities = { 1, 2, 4, 8, 16, 32 };

        int round = 0;
        for (int[] r : regions) {
            for (int cap : capacities) {
                round++;
                randomizedRound("region" + round + "(" + r[0] + ".." + r[3] + "/" + r[1] + ".." + r[4] + ",cap=" + cap + ")",
                        r, cap, 600);
            }
        }
    }

    private static void randomizedRound(String tag, int[] r, int capacity, int ops) {
        int minX = r[0], minY = r[1], minZ = r[2], maxX = r[3], maxY = r[4], maxZ = r[5];
        BlockOctree<Integer> tree = new BlockOctree<>(minX, minY, minZ, maxX, maxY, maxZ, capacity);
        Map<Long, Integer> ref = new HashMap<>();
        SplittableRandom rnd = new SplittableRandom(0xBEAD + capacity * 31L + minX * 7L + minZ);

        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;

        for (int step = 0; step < ops; step++) {
            int x = minX + rnd.nextInt(sx);
            int y = minY + rnd.nextInt(sy);
            int z = minZ + rnd.nextInt(sz);
            long key = key(x, y, z, minX, minY, minZ);

            switch (rnd.nextInt(10)) {
                case 0, 1, 2, 3 -> {
                    Integer previous = ref.putIfAbsent(key, step);
                    boolean ok = tree.insert(x, y, z, step);
                    if (previous == null && !ok) fail(tag + " insert(" + x + "," + y + "," + z + ") rejected");
                    if (previous != null && ok) fail(tag + " duplicate insert(" + x + "," + y + "," + z + ") accepted");
                }
                case 4 -> {
                    Integer expect = ref.remove(key);
                    Integer got = tree.remove(x, y, z);
                    if (expect == null ? got != null : !expect.equals(got)) {
                        fail(tag + " remove(" + x + "," + y + "," + z + ") expected " + expect + " got " + got);
                    }
                }
                case 5, 6 -> {
                    Integer expect = ref.get(key);
                    Integer got = tree.get(x, y, z);
                    if (expect == null ? got != null : !expect.equals(got)) {
                        fail(tag + " get(" + x + "," + y + "," + z + ") expected " + expect + " got " + got);
                    }
                }
                case 7, 8 -> {
                    int radius = rnd.nextInt(6);
                    boolean filtered = rnd.nextBoolean();
                    Predicate<Integer> filter = filtered ? v -> (v & 1) == 0 : null;
                    List<Integer> got = new ArrayList<>();
                    tree.querySphere(x, y, z, radius, filter, got);
                    Set<Integer> expected = referenceSphere(ref, minX, minY, minZ, x, y, z, radius, filter);
                    if (!new HashSet<>(got).equals(expected) || got.size() != new HashSet<>(got).size()) {
                        fail(tag + " querySphere at (" + x + "," + y + "," + z + ") r=" + radius + " filtered=" +
                                filtered + " expected " + expected.size() + " got " + got.size());
                    }
                }
                default -> {
                    int radius = rnd.nextInt(6);
                    Predicate<Integer> filter = rnd.nextBoolean() ? v -> (v & 3) == 0 : null;
                    Integer got = tree.findAny(x, y, z, radius, filter);
                    Set<Integer> expected = referenceSphere(ref, minX, minY, minZ, x, y, z, radius, filter);
                    if (expected.isEmpty()) {
                        if (got != null) fail(tag + " findAny should be null at (" + x + "," + y + "," + z + ")");
                    } else if (got == null || !expected.contains(got)) {
                        fail(tag + " findAny returned " + got + " not in the expected set at (" + x + "," + y + "," +
                                z + ")");
                    }
                }
            }

            if (tree.size() != ref.size()) {
                fail(tag + " size() " + tree.size() + " != reference " + ref.size() + " at step " + step);
            }

            // repack in the middle of the workload: must not lose, duplicate or misplace anything
            if (step % 150 == 149) {
                int packedCount = tree.compact();
                if (packedCount != ref.size()) {
                    fail(tag + " compact() returned " + packedCount + " but reference has " + ref.size());
                }
                for (int k = 0; k < 64; k++) {
                    int px = minX + rnd.nextInt(sx), py = minY + rnd.nextInt(sy), pz = minZ + rnd.nextInt(sz);
                    if (!matches(tree, ref, key(px, py, pz, minX, minY, minZ), px, py, pz)) return;
                }
            }
        }

        // 收尾核对：小区域全量、大区域抽样（大区域全量会跑几十亿次 get）
        long volume = (long) sx * sy * sz;
        if (volume <= 40000) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!matches(tree, ref, key(x, y, z, minX, minY, minZ), x, y, z)) return;
                    }
                }
            }
        } else {
            for (int i = 0; i < 5000; i++) {
                int x = minX + rnd.nextInt(sx), y = minY + rnd.nextInt(sy), z = minZ + rnd.nextInt(sz);
                if (!matches(tree, ref, key(x, y, z, minX, minY, minZ), x, y, z)) return;
            }
        }

        Set<Integer> visited = new HashSet<>();
        tree.forEach(visited::add);
        if (!visited.equals(new HashSet<>(ref.values())) || visited.size() != ref.size()) {
            fail(tag + " forEach visited " + visited.size() + " values, reference has " + ref.size());
            return;
        }
        passed++;
    }

    private static boolean matches(BlockOctree<Integer> tree, Map<Long, Integer> ref, long key, int x, int y, int z) {
        Integer expect = ref.get(key);
        Integer got = tree.get(x, y, z);
        if (expect == null ? got != null : !expect.equals(got)) {
            fail("final get(" + x + "," + y + "," + z + ") expected " + expect + " got " + got);
            return false;
        }
        return true;
    }

    /** 参照模型用「区域内偏移量」编码，天然无碰撞。 */
    private static long key(int x, int y, int z, int minX, int minY, int minZ) {
        return (((long) (x - minX)) << 42) | (((long) (z - minZ)) << 21) | (y - minY);
    }

    private static Set<Integer> referenceSphere(Map<Long, Integer> ref, int minX, int minY, int minZ,
                                                int cx, int cy, int cz, int radius, Predicate<Integer> filter) {
        Set<Integer> out = new HashSet<>();
        if (radius < 0) return out;
        long r2 = (long) radius * radius;
        for (Map.Entry<Long, Integer> e : ref.entrySet()) {
            long key = e.getKey();
            int x = (int) ((key >>> 42) & 0x1FFFFF) + minX;
            int z = (int) ((key >>> 21) & 0x1FFFFF) + minZ;
            int y = (int) (key & 0x1FFFFF) + minY;
            long dx = (long) x - cx, dy = (long) y - cy, dz = (long) z - cz;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            if (filter == null || filter.test(e.getValue())) out.add(e.getValue());
        }
        return out;
    }
}
