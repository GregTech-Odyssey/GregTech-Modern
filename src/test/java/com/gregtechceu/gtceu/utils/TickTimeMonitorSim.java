package com.gregtechceu.gtceu.utils;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

/**
 * {@link TickTimeMonitor} 的确定性验证（不依赖游戏、不用系统时间、不用 JUnit）。
 *
 * <p>
 * 被测对象是 {@link TickTimeMonitorInjected}——和 {@link TickTimeMonitor} 逐行一致、只是耗时来自注入的纳秒
 * 时钟。测试把时钟精确地往前拨，所以单次耗时就是确定的整数微秒，所有断言都是 {@code ==}（没有"±容差"）。
 *
 * <p>
 * 跑法（PowerShell 一行）：
 *
 * <pre>{@code
 * javac -encoding UTF-8 -d out src/main/java/com/gregtechceu/gtceu/utils/TickTimeMonitor.java src/test/java/com/gregtechceu/gtceu/utils/TickTimeMonitorInjected.java src/test/java/com/gregtechceu/gtceu/utils/TickTimeMonitorSim.java
 * java -Dfile.encoding=UTF-8 -cp out com.gregtechceu.gtceu.utils.TickTimeMonitorSim
 * }</pre>
 */
public final class TickTimeMonitorSim {

    /** Jade 的取数周期：每 5 刻一次（实际是 250ms）。 */
    private static final int JADE_PERIOD = 5;

    private static int checks;
    private static int failures;

    /** 可控时钟（纳秒）：work() 直接把它往后拨，不碰 System.nanoTime()。 */
    private static long clockNanos;
    /** 本次调用的模拟耗时（微秒）。 */
    private static long costMicros = 2_000;

    private static int window = 16;
    /** 模拟的游戏刻。 */
    private static int gameTick = 1_000_000;
    /** 模拟的逻辑 tick 间隔（游戏刻），对应 interval 5→80。 */
    private static int callInterval = 1;
    private static int lastCallTick;
    /** 玩家是否在看。 */
    private static boolean watching;
    /** 最近一次 Jade 取到的值。 */
    private static double lastJadeValue;

    private static TickTimeMonitorInjected monitor;

    /** 最近 window 次「真正被计时」的调用耗时，用来算期望值。 */
    private static final ArrayDeque<Long> injected = new ArrayDeque<>();

    public static void main(String[] args) {
        scenarioLazyAllocation();
        scenarioExactWindow();
        scenarioPartialFill();
        scenarioRollingEviction();
        scenarioSameTickCalls();
        scenarioConstantCost();
        scenarioSparseCalls();
        scenarioStopWhenPlayerLeaves();
        scenarioStopWhenMachineSilent();
        scenarioRearmResets();
        scenarioTickWrap();
        scenarioTwoMachines();
        scenarioZeroCost();
        scenarioClamp();
        scenarioNegativeClock();
        scenarioDegenerateWindowOne();
        scenarioIllegalWindow();
        scenarioFuzz();

        System.out.printf("%n=== %d checks, %d failures ===%n", checks, failures);
        if (failures != 0) System.exit(1);
    }

    // ===== 模拟世界 =====

    private static void reset(int newWindow) {
        window = newWindow;
        clockNanos = 0L;
        gameTick = 1_000_000;
        lastCallTick = gameTick;
        watching = false;
        callInterval = 1;
        costMicros = 2_000;
        lastJadeValue = 0.0D;
        injected.clear();
        monitor = newMonitor(newWindow);
    }

    private static TickTimeMonitorInjected newMonitor(int newWindow) {
        return new TickTimeMonitorInjected(newWindow, () -> gameTick, () -> clockNanos, TickTimeMonitorSim::work);
    }

    /** 模拟一次 serverTick 的开销：直接把可控时钟往后拨，不用系统时间。 */
    private static void work() {
        clockNanos += costMicros * 1_000L;
    }

    /** 一次被计时的调用：跑任务，并把这次耗时记进期望样本（按封顶后的值记）。 */
    private static void measure(long micros) {
        costMicros = micros;
        monitor.tickAndMeasure();
        injected.addLast(Math.min(micros, Short.MAX_VALUE));
        while (injected.size() > window) {
            injected.removeFirst();
        }
    }

    /** 模拟一次 Jade 取数据：null 侧 + 6 个方向，共 7 次；顺便处理「重新开窗」。 */
    private static double jadeRead() {
        boolean wasMonitored = monitor.tickTimeMonitored;
        double value = 0.0D;
        for (int i = 0; i < 7; i++) {
            value = monitor.getAverageTickTimeMicros();
        }
        if (!wasMonitored && monitor.tickTimeMonitored) {
            injected.clear(); // 重新开窗，旧样本不算
        }
        return value;
    }

    /** 推进一个游戏刻：按 callInterval 跑逻辑 tick，再按 JADE_PERIOD 让 Jade 取数据。 */
    private static void advance() {
        gameTick++;
        if (gameTick - lastCallTick >= callInterval) {
            lastCallTick = gameTick;
            // 等价于 RecipeLogic.tickAndMeasure()
            if (monitor.tickTimeMonitored) {
                measure(costMicros);
            } else {
                work();
            }
        }
        if (watching && gameTick % JADE_PERIOD == 0) {
            lastJadeValue = jadeRead();
        }
    }

    private static void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            advance();
        }
    }

    /**
     * 「每刻都在看」：先续期再测量。窗口小于 {@link #JADE_PERIOD} 时必须这样驱动，否则两次 Jade 取数之间就会
     * 超过 window 刻而被判成「玩家走了」。
     */
    private static void viewAndMeasure(long micros) {
        gameTick++;
        jadeRead();
        measure(micros);
    }

    private static String head(String name) {
        return "\n-- " + name + " --";
    }

    // ===== 断言 =====

    private static void check(String name, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        System.out.printf("  [%s] %s%n", ok ? "PASS" : "FAIL", name);
        if (detail != null) System.out.println("         " + detail);
    }

    /**
     * 精确校验：换一刻读一次取值，和「注入样本的平均」必须完全相等，同时核对内部状态。
     *
     * <p>
     * 期望值和实际值都是同一个整数和除以同样的个数，所以这里的 {@code ==} 是严格的。
     */
    private static void verifyExact(String name) {
        if (!monitor.tickTimeMonitored) {
            check(name, false, "监控已停，无法校验取值");
            return;
        }
        gameTick++; // 换一刻，避开同刻缓存
        long expectedSum = 0;
        for (long c : injected) {
            expectedSum += c;
        }
        double expected = injected.isEmpty() ? 0.0D : expectedSum / (double) injected.size();
        double actual = monitor.getAverageTickTimeMicros();
        int buffered = liveSum();
        boolean stateOk = buffered == monitor.tickTimeSum && monitor.tickTimeCount == injected.size() && monitor.tickTimeSum == (int) expectedSum;
        check(name, actual == expected && stateOk,
                String.format("actual=%.6f µs, expected=%.6f µs (sum=%d, count=%d, 注入和=%d)", actual, expected,
                        monitor.tickTimeSum, monitor.tickTimeCount, expectedSum));
    }

    /** @return 违例描述，没问题返回 {@code null}（纯状态校验，不改状态） */
    private static String checkInvariants() {
        if (monitor.tickTimes == null) {
            return monitor.tickTimeMonitored ? "没分配缓冲却在采样" : null;
        }
        int buffered = liveSum();
        if (buffered != monitor.tickTimeSum) {
            return "sum 与有效样本不一致：" + buffered + " != " + monitor.tickTimeSum;
        }
        if (monitor.tickTimeIndex < 0 || monitor.tickTimeIndex >= window) {
            return "index 越界：" + monitor.tickTimeIndex;
        }
        if (monitor.tickTimeCount != injected.size()) {
            return "count 与注入样本数不一致：" + monitor.tickTimeCount + " != " + injected.size();
        }
        long expectedSum = 0;
        for (long c : injected) {
            expectedSum += c;
        }
        if (monitor.tickTimeSum != (int) expectedSum) {
            return "sum 与注入样本不符：" + monitor.tickTimeSum + " != " + expectedSum;
        }
        return null;
    }

    /**
     * 环形缓冲里有效样本的和：没满就是前 {@code count} 个，满了就整块都有效。
     *
     * <p>
     * 重新开窗只重置 count/index/sum、不擦数组，所以已经满过一轮的缓冲里会留着上一轮的旧槽位，那些不算数
     * （monitor 自己也不会把它们算进 tickTimeSum）。
     */
    private static int liveSum() {
        if (monitor.tickTimes == null) return 0;
        int sum = 0;
        for (int i = 0, n = Math.min(monitor.tickTimeCount, window); i < n; i++) {
            sum += monitor.tickTimes[i];
        }
        return sum;
    }

    // ===== 场景 =====

    private static void scenarioLazyAllocation() {
        System.out.println(head("1. 环形缓冲惰性分配"));
        reset(16);
        check("没被查看过：没有缓冲、没在采样", monitor.tickTimes == null && !monitor.tickTimeMonitored,
                "tickTimes=null");
        watching = true;
        advance(JADE_PERIOD);
        short[] allocated = monitor.tickTimes;
        check("查看之后才分配缓冲", allocated != null && allocated.length == 16,
                "length=" + (allocated == null ? "null" : allocated.length));
        watching = false;
        advance(16 + JADE_PERIOD);
        watching = true;
        advance(JADE_PERIOD * 2);
        check("离开再回来复用同一块缓冲", monitor.tickTimes == allocated, null);
    }

    private static void scenarioExactWindow() {
        System.out.println(head("2. 精确平均（1..8 ms 装满，再顶掉最旧两个）"));
        reset(8);
        watching = true;
        jadeRead();
        for (int i = 1; i <= 8; i++) {
            costMicros = i * 1_000L;
            advance();
        }
        verifyExact("装满窗口：平均 = (1+…+8)/8 = 4500 µs");
        for (int i = 9; i <= 10; i++) {
            costMicros = i * 1_000L;
            advance();
        }
        verifyExact("顶掉最旧两个：平均 = (3+…+10)/8 = 6500 µs");
    }

    private static void scenarioPartialFill() {
        System.out.println(head("3. 窗口没满时按实际个数平均"));
        reset(8);
        watching = true;
        jadeRead();
        for (int i = 1; i <= 3; i++) {
            costMicros = i * 1_000L;
            advance();
        }
        verifyExact("3 个样本：平均 = 6000/3 = 2000 µs");
    }

    private static void scenarioRollingEviction() {
        System.out.println(head("4. 环形顶替（window=3，逐个样本核对）"));
        reset(3);
        watching = false; // 逐刻手动查看，window=3 比 Jade 周期还短
        long[] costs = { 1_000L, 7_000L, 4_000L, 10_000L, 2_000L };
        String[] expects = { "1 个样本 = 1000", "2 个样本 = 4000", "3 个样本 = 4000", "顶掉最旧 = 7000",
                "再顶一个 = 5333.333…" };
        for (int i = 0; i < costs.length; i++) {
            viewAndMeasure(costs[i]);
            long sum = 0;
            int n = 0;
            for (long c : injected) {
                sum += c;
                n++;
            }
            gameTick++; // 换一刻读，避开同刻缓存
            double actual = monitor.getAverageTickTimeMicros();
            double expected = sum / (double) n;
            String bad = checkInvariants();
            check("第 " + (i + 1) + " 次后 " + expects[i], actual == expected && bad == null,
                    String.format("actual=%.6f, expected=%.6f, count=%d%s", actual, expected, n,
                            bad == null ? "" : ", " + bad));
        }
    }

    private static void scenarioSameTickCalls() {
        System.out.println(head("5. 同一刻被问 7 遍只算一次"));
        reset(16);
        watching = true;
        advance(JADE_PERIOD * 2);
        double[] values = new double[7];
        values[0] = monitor.getAverageTickTimeMicros();
        int viewed = monitor.tickTimeLastViewed;
        int answered = monitor.tickTimeAnswerTick;
        for (int i = 1; i < 7; i++) {
            values[i] = monitor.getAverageTickTimeMicros();
        }
        boolean same = true;
        for (double v : values) {
            if (v != values[0]) same = false;
        }
        check("7 次返回同一个值", same, Arrays.toString(values));
        check("第一次之后没再动状态",
                monitor.tickTimeLastViewed == viewed && monitor.tickTimeAnswerTick == answered,
                "lastViewed=" + monitor.tickTimeLastViewed + ", answerTick=" + monitor.tickTimeAnswerTick);
    }

    private static void scenarioConstantCost() {
        System.out.println(head("6. 固定 3000 µs 滚 4 轮：每一步都必须严格等于 3000"));
        reset(8);
        watching = true;
        jadeRead();
        boolean allExact = true;
        double min = Double.MAX_VALUE;
        double max = 0.0D;
        for (int i = 0; i < 32; i++) {
            costMicros = 3_000;
            advance();
            gameTick++;
            double v = monitor.getAverageTickTimeMicros();
            min = Math.min(min, v);
            max = Math.max(max, v);
            if (v != 3_000.0D || checkInvariants() != null) allExact = false;
        }
        check("32 步每一步都严格等于 3000 µs（没有清零、没有漂移）", allExact,
                String.format("min=%.6f, max=%.6f", min, max));
    }

    private static void scenarioSparseCalls() {
        System.out.println(head("7. 每 5 刻调用一次：样本数是调用次数，不是游戏刻数"));
        reset(16);
        watching = true;
        callInterval = 5;
        costMicros = 5_000;
        jadeRead();
        advance(16 * 5);
        verifyExact("80 刻 = 16 次调用：平均 5000 µs，count=16");
        check("count == 16", monitor.tickTimeCount == 16, "count=" + monitor.tickTimeCount);
    }

    private static void scenarioStopWhenPlayerLeaves() {
        System.out.println(head("8. 玩家离开的边界（window-1 还在采，window 停采）"));
        reset(16);
        watching = true;
        advance(32);
        check("看着的时候在采样", monitor.tickTimeMonitored, null);
        watching = false;
        jadeRead();
        advance(15);
        check("离开 15 刻（window-1）：还在采样", monitor.tickTimeMonitored, null);
        advance(1);
        check("离开 16 刻（window）：停采", !monitor.tickTimeMonitored, null);
        watching = true;
        check("重新查看那一刻返回 0", jadeRead() == 0.0D, null);
        advance(JADE_PERIOD * 2);
        verifyExact("重新查看后恢复：平均 = 2000 µs");
    }

    private static void scenarioStopWhenMachineSilent() {
        System.out.println(head("9. 机器不 tick 的边界（window-1 还能取到旧值，window 返回 0）"));
        reset(16);
        watching = true;
        advance(JADE_PERIOD * 2);
        callInterval = Integer.MAX_VALUE;
        advance(15);
        double before = jadeRead();
        check("机器停 15 刻（window-1）：还能取到旧值", before == 2_000.0D, "value=" + before);
        advance(1);
        check("机器停 16 刻（window）：返回 0", jadeRead() == 0.0D, null);
        callInterval = 1;
        advance(JADE_PERIOD * 3);
        verifyExact("机器恢复 tick 后再开窗：平均 = 2000 µs");
    }

    private static void scenarioRearmResets() {
        System.out.println(head("10. 重新开窗会清掉旧样本"));
        reset(8);
        watching = true;
        advance(16);
        int countBefore = monitor.tickTimeCount;
        watching = false;
        advance(8 + JADE_PERIOD);
        watching = true;
        jadeRead();
        check("重新开窗后样本清零", monitor.tickTimeCount == 0 && monitor.tickTimeSum == 0,
                "count=" + monitor.tickTimeCount + "（开窗前 " + countBefore + "）");
        advance(JADE_PERIOD * 2);
        verifyExact("重新开窗后的平均只含新样本");
    }

    private static void scenarioTickWrap() {
        System.out.println(head("11. 游戏刻溢出（int 回绕）"));
        reset(8);
        gameTick = Integer.MAX_VALUE - 5;
        lastCallTick = gameTick;
        watching = true;
        jadeRead();
        advance(6); // 从 MAX-5 出发，跨过回绕
        check("确实跨过了 Integer.MAX_VALUE", gameTick < 0, "tick=" + gameTick);
        jadeRead(); // 续期，免得因为回绕期间没轮到取数而停采
        verifyExact("跨过回绕后照样精确");
        check("回绕后仍在采样", monitor.tickTimeMonitored, "tick=" + gameTick);
    }

    private static void scenarioTwoMachines() {
        System.out.println(head("12. 两个机器各自的窗口"));
        reset(16);
        watching = false;
        TickTimeMonitorInjected a = newMonitor(16);
        TickTimeMonitorInjected b = newMonitor(16);
        a.getAverageTickTimeMicros();
        b.getAverageTickTimeMicros();
        for (int i = 0; i < 8; i++) {
            gameTick++;
            costMicros = 2_000;
            a.tickAndMeasure();
            costMicros = 6_000;
            b.tickAndMeasure();
        }
        gameTick++;
        double aValue = a.getAverageTickTimeMicros();
        double bValue = b.getAverageTickTimeMicros();
        check("机器 A 精确等于 2000 µs", aValue == 2_000.0D, "a=" + aValue);
        check("机器 B 精确等于 6000 µs", bValue == 6_000.0D, "b=" + bValue);
        check("两者互不影响", a.tickTimeSum == 16_000 && b.tickTimeSum == 48_000,
                "a.sum=" + a.tickTimeSum + ", b.sum=" + b.tickTimeSum);
    }

    private static void scenarioZeroCost() {
        System.out.println(head("13. 零耗时"));
        reset(8);
        watching = true;
        jadeRead();
        for (int i = 0; i < 8; i++) {
            costMicros = 0;
            advance();
        }
        verifyExact("全 0 样本：平均严格等于 0（不是 NaN）");
        check("sum == 0", monitor.tickTimeSum == 0, "sum=" + monitor.tickTimeSum);
    }

    private static void scenarioClamp() {
        System.out.println(head("14. 单次耗时封顶（32767 µs）"));
        reset(8);
        watching = true;
        jadeRead();
        gameTick++;
        measure(40_000);
        check("40 ms 被夹成 32767", monitor.tickTimes[(monitor.tickTimeIndex - 1 + 8) % 8] == Short.MAX_VALUE,
                "sample=" + monitor.tickTimes[(monitor.tickTimeIndex - 1 + 8) % 8]);
        verifyExact("夹取后平均 = 32767");
        gameTick++;
        measure(32_000);
        check("32000 µs 不夹", monitor.tickTimes[(monitor.tickTimeIndex - 1 + 8) % 8] == 32_000,
                "sample=" + monitor.tickTimes[(monitor.tickTimeIndex - 1 + 8) % 8]);
        verifyExact("32000 与 32767 的平均 = 32383.5");
    }

    private static void scenarioNegativeClock() {
        System.out.println(head("15. 时钟倒退（现状：低端不夹取）"));
        reset(8);
        watching = true;
        jadeRead();
        gameTick++;
        measure(-5_000);
        double sample = monitor.tickTimes[(monitor.tickTimeIndex - 1 + 8) % 8];
        check("时钟倒退会记成负数（nanoTime 正常不会倒退；要保险可以加 Math.max(0, …)）", sample == -5_000.0D,
                "sample=" + sample);
        verifyExact("平均也跟着是负数");
    }

    private static void scenarioDegenerateWindowOne() {
        System.out.println(head("16. 退化配置 window = 1"));
        reset(1);
        costMicros = 3_000;
        monitor.getAverageTickTimeMicros();
        double maxValue = 0.0D;
        for (int i = 0; i < 8; i++) {
            gameTick++;
            monitor.getAverageTickTimeMicros();
            monitor.tickAndMeasure();
            maxValue = Math.max(maxValue, monitor.getAverageTickTimeMicros());
        }
        check("window=1 取值恒为 0（窗口大小同时是「多久没测量就停采」的阈值，1 刻太短）",
                maxValue == 0.0D, "max=" + maxValue);
    }

    private static void scenarioIllegalWindow() {
        System.out.println(head("17. 非法窗口参数"));
        for (int bad : new int[] { 0, -1, 65_537 }) {
            boolean threw = false;
            try {
                newMonitor(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check("window=" + bad + " 被拒绝", threw, null);
        }
    }

    private static void scenarioFuzz() {
        System.out.println(head("18. 确定性模糊测试（每步精确校验）"));
        reset(16);
        Random random = new Random(20260927L);
        watching = true;
        jadeRead();
        int violations = 0;
        String firstBad = null;
        for (int i = 0; i < 300; i++) {
            costMicros = 1_000L * (1 + random.nextInt(30));
            callInterval = 1 + random.nextInt(3);
            if (random.nextInt(4) == 0) {
                gameTick += 1 + random.nextInt(3);
            } else {
                advance();
            }
            if (random.nextInt(3) == 0) {
                jadeRead();
            }
            String bad = checkInvariants();
            if (bad != null) {
                violations++;
                if (firstBad == null) firstBad = bad;
            }
        }
        check("300 轮随机驱动后不变式全部成立", violations == 0, firstBad);
        verifyExact("模糊测试结束时的取值 == 注入样本平均值");
    }
}
