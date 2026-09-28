package com.gregtechceu.gtceu.utils;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * {@link TickTimeMonitor} 的确定性验证（不依赖游戏、不用系统时间、不用 JUnit）。
 *
 * <p>
 * 被测对象是 {@link TickTimeMonitorInjected}——和 {@link TickTimeMonitor} 逐行一致、只是耗时来自注入的纳秒
 * 时钟。测试把时钟精确地往前拨，所以一次调用的耗时就是确定的整数微秒，所有断言都是 {@code ==}（没有"±容差"）。
 *
 * <p>
 * 模型侧（{@link #sampleCosts} / {@link #sampleSpans}）独立按同样规则算一遍期望的耗时和刻数，用来和监控内部的
 * sum / count / ticks 逐条对齐；另有一组只看语义的断言——重点是「按刻摊销」：「每 4 刻 4000 µs」必须摊成
 * 1000 µs，而不是显示成 4000。
 *
 * <p>
 * 跑法（PowerShell 一行）：
 *
 * <pre>{@code
 * javac -encoding UTF-8 -d out src/test/java/com/gregtechceu/gtceu/utils/TickTimeMonitorInjected.java src/test/java/com/gregtechceu/gtceu/utils/TickTimeMonitorSim.java
 * java -Dfile.encoding=UTF-8 -cp out com.gregtechceu.gtceu.utils.TickTimeMonitorSim
 * }</pre>
 */
public final class TickTimeMonitorSim {

    /** Jade 的取数周期：每 5 刻一次（实际约 250ms）。 */
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

    private static TickTimeMonitorInjected monitor;

    /** 模型：窗口内每条样本的耗时（微秒）。 */
    private static final ArrayDeque<Long> sampleCosts = new ArrayDeque<>();
    /** 模型：窗口内每条样本占的刻数。 */
    private static final ArrayDeque<Integer> sampleSpans = new ArrayDeque<>();
    /** 模型：上一次记样本的刻。 */
    private static int modelLastSampleTick = Integer.MIN_VALUE;

    public static void main(String[] args) {
        scenarioLazyAllocation();
        scenarioPerTickAmortization();
        scenarioEveryTickConstant();
        scenarioSameTickCalls();
        scenarioSparseBeyondWindow();
        scenarioClamp();
        scenarioZeroCost();
        scenarioNegativeClock();
        scenarioStopWhenPlayerLeaves();
        scenarioRearmResets();
        scenarioDegenerateWindowOne();
        scenarioIllegalWindow();
        scenarioTwoMachines();
        scenarioTickWrap();
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
        sampleCosts.clear();
        sampleSpans.clear();
        modelLastSampleTick = Integer.MIN_VALUE;
        monitor = newMonitor(newWindow);
    }

    private static TickTimeMonitorInjected newMonitor(int newWindow) {
        return new TickTimeMonitorInjected(newWindow, () -> gameTick, () -> clockNanos, TickTimeMonitorSim::work);
    }

    /** 模拟一次 serverTick 的开销：直接把可控时钟往后拨，不用系统时间。 */
    private static void work() {
        clockNanos += costMicros * 1_000L;
    }

    /** 一次被计时的调用：跑任务，并按同样规则更新模型。 */
    private static void measure(long micros) {
        costMicros = micros;
        monitor.tickAndMeasure();
        modelRecord(gameTick, micros);
    }

    private static void modelRecord(int tick, long micros) {
        int span = tick - modelLastSampleTick;
        if (span < 0) span = 0;
        modelLastSampleTick = tick;
        sampleCosts.addLast(Math.min(micros, Short.MAX_VALUE));
        sampleSpans.addLast(Math.min(span, Short.MAX_VALUE));
        while (sampleCosts.size() > window) {
            sampleCosts.removeFirst();
            sampleSpans.removeFirst();
        }
    }

    private static long modelSum() {
        long sum = 0L;
        for (long c : sampleCosts) {
            sum += c;
        }
        return sum;
    }

    private static long modelTicks() {
        long ticks = 0L;
        for (int s : sampleSpans) {
            ticks += s;
        }
        return ticks;
    }

    /** 模拟一次 Jade 取数据：null 侧 + 6 个方向，共 7 次；顺便处理「重新开窗」。 */
    private static double jadeRead() {
        boolean wasMonitored = monitor.tickTimeMonitored;
        double value = 0.0D;
        for (int i = 0; i < 7; i++) {
            value = monitor.getAverageTickTimeMicros();
        }
        if (!wasMonitored && monitor.tickTimeMonitored) {
            // 重新开窗：旧样本不算，「上次采样刻」对齐到 tick-1
            sampleCosts.clear();
            sampleSpans.clear();
            modelLastSampleTick = gameTick - 1;
        }
        return value;
    }

    /** 推进一个游戏刻：按 callInterval 跑逻辑 tick，再按 JADE_PERIOD 让 Jade 取数据。 */
    private static void advance() {
        gameTick++;
        if (gameTick - lastCallTick >= callInterval) {
            lastCallTick = gameTick;
            if (monitor.tickTimeMonitored) {
                measure(costMicros);
            } else {
                work();
            }
        }
        if (watching && gameTick % JADE_PERIOD == 0) {
            jadeRead();
        }
    }

    private static void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            advance();
        }
    }

    /** 换一刻读一次取值，避开同刻缓存。 */
    private static double readNewTick() {
        gameTick++;
        return monitor.getAverageTickTimeMicros();
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

    /** 期望取值：和实现一样用 float 算；还没采到样本时是 0。 */
    private static float expectedValue() {
        long ticks = modelTicks();
        return ticks == 0L ? 0.0F : (float) modelSum() / (float) ticks;
    }

    /**
     * 精确校验：换一刻读一次取值，和模型必须完全相等，同时核对内部状态。
     *
     * <p>
     * 期望值和实际值都是同一个整数和除以同样的个数，所以这里的 {@code ==} 是严格的。
     */
    private static void verifyExact(String name) {
        if (!monitor.tickTimeMonitored) {
            check(name, false, "监控已停，无法校验取值");
            return;
        }
        double actual = readNewTick();
        boolean stateOk = monitor.tickTimeCount == sampleCosts.size() && monitor.tickTimeSum == (int) modelSum() &&
                monitor.tickTimeTicks == (int) modelTicks();
        check(name, actual == expectedValue() && stateOk,
                String.format("actual=%.6f µs, expected=%.6f µs (sum=%d/%d, count=%d/%d, ticks=%d/%d)", actual,
                        expectedValue(), monitor.tickTimeSum, modelSum(), monitor.tickTimeCount, sampleCosts.size(),
                        monitor.tickTimeTicks, modelTicks()));
    }

    /** @return 违例描述，没问题返回 {@code null}（纯状态校验，不改状态） */
    private static String checkInvariants() {
        if (monitor.tickTimes == null) {
            return monitor.tickTimeMonitored ? "没分配缓冲却在采样" : null;
        }
        if (monitor.tickTimeIndex < 0 || monitor.tickTimeIndex >= window) {
            return "index 越界：" + monitor.tickTimeIndex;
        }
        if (monitor.tickTimeCount > window) {
            return "count 超出窗口：" + monitor.tickTimeCount;
        }
        if (monitor.tickTimeCount != sampleCosts.size()) {
            return "count 与模型样本数不一致：" + monitor.tickTimeCount + " != " + sampleCosts.size();
        }
        if (monitor.tickTimeSum != (int) modelSum()) {
            return "sum 与模型不符：" + monitor.tickTimeSum + " != " + modelSum();
        }
        if (monitor.tickTimeTicks != (int) modelTicks()) {
            return "刻数 与模型不符：" + monitor.tickTimeTicks + " != " + modelTicks();
        }
        return null;
    }

    // ===== 场景 =====

    private static void scenarioLazyAllocation() {
        System.out.println(head("1. 环形缓冲惰性分配"));
        reset(16);
        check("没被查看过：没有缓冲、没在采样", monitor.tickTimes == null && !monitor.tickTimeMonitored, "tickTimes=null");
        watching = true;
        advance(JADE_PERIOD);
        short[] allocated = monitor.tickTimes;
        check("查看之后才分配缓冲", allocated != null && allocated.length == 16,
                "length=" + (allocated == null ? "null" : allocated.length));
        watching = false;
        advance(16 + JADE_PERIOD);
        check("离开超过窗口：停采（缓冲留着，回来接着用）", !monitor.tickTimeMonitored,
                "monitored=" + monitor.tickTimeMonitored);
        watching = true;
        advance(JADE_PERIOD * 2);
        check("离开再回来复用同一块缓冲", monitor.tickTimes == allocated, null);
    }

    private static void scenarioPerTickAmortization() {
        System.out.println(head("2. 每 4 刻跑一次、每次 4000 µs → 每刻恰好 1000 µs（不是 4000）"));
        reset(16);
        watching = true;
        callInterval = 4;
        costMicros = 4_000;
        jadeRead();
        advance(16 * 4 * 3); // 跑满若干轮，开窗那条样本早就被顶掉了
        double v = readNewTick();
        check("取值恰好 = 4000 / 4 = 1000 µs", v == 1_000.0D, String.format("value=%.6f µs（单次调用是 4000）", v));
        check("窗口里是最近 16 次调用，每条占 4 刻", monitor.tickTimeCount == 16 && monitor.tickTimeTicks == 64,
                "count=" + monitor.tickTimeCount + ", ticks=" + monitor.tickTimeTicks);
        verifyExact("和模型逐条一致");
    }

    private static void scenarioEveryTickConstant() {
        System.out.println(head("3. 每刻 3000 µs：取值恒为 3000"));
        reset(8);
        watching = true;
        costMicros = 3_000;
        jadeRead();
        advance(window + 2); // 先让开窗那一刻那条样本滑出窗口
        boolean allExact = true;
        double min = Double.MAX_VALUE;
        double max = 0.0D;
        for (int i = 0; i < 32; i++) {
            costMicros = 3_000;
            advance();
            // 同一刻直接读（advance 里的 Jade 取数已经算过这一刻了，这里拿的是同刻缓存值）
            double v = monitor.getAverageTickTimeMicros();
            min = Math.min(min, v);
            max = Math.max(max, v);
            if (v != 3_000.0D || checkInvariants() != null) allExact = false;
        }
        check("每刻固定耗时：取值恒为 3000 µs（没有清零、没有漂移）", allExact,
                String.format("min=%.6f, max=%.6f", min, max));
    }

    private static void scenarioSameTickCalls() {
        System.out.println(head("4. 同一刻的多次调用只占一次刻数"));
        reset(16);
        jadeRead();
        measure(1_000);
        measure(1_000);
        measure(1_000);
        check("3 次调用、刻数只 +1", monitor.tickTimeCount == 3 && monitor.tickTimeTicks == 1,
                "count=" + monitor.tickTimeCount + ", ticks=" + monitor.tickTimeTicks);
        double v = readNewTick();
        check("取值 = 3000 µs（这一刻的总开销）", v == 3_000.0D, "value=" + v);
        measure(0); // 同一刻（readNewTick 已经推进到下一刻）再补一条 0 耗时
        check("再一条样本：刻数 +1", monitor.tickTimeTicks == 2, "ticks=" + monitor.tickTimeTicks);
        double v2 = readNewTick();
        check("平均被摊薄成 1500 µs", v2 == 1_500.0D, "value=" + v2);
    }

    private static void scenarioSparseBeyondWindow() {
        System.out.println(head("5. 周期比窗口还长：跑一次就停采，重新看返回 0"));
        reset(8);
        jadeRead();
        callInterval = 40;
        costMicros = 5_000;
        advance(40);
        check("周期 40 > 窗口 8：这次跑完就停采", !monitor.tickTimeMonitored, null);
        check("重新查看那一刻返回 0", jadeRead() == 0.0D, null);
        callInterval = 1;
        costMicros = 2_000;
        advance(4);
        verifyExact("恢复每刻跑之后按刻平均 = 2000 µs");
    }

    private static void scenarioClamp() {
        System.out.println(head("6. 单次耗时封顶（32767 µs）"));
        reset(8);
        jadeRead();
        measure(40_000);
        check("40 ms 被夹成 32767", monitor.tickTimeSum == Short.MAX_VALUE, "sum=" + monitor.tickTimeSum);
        gameTick++;
        measure(32_000);
        check("32000 不夹，两条之和 = 64767", monitor.tickTimeSum == 32_000 + Short.MAX_VALUE,
                "sum=" + monitor.tickTimeSum);
        check("平均 = 64767 / 2 = 32383.5", monitor.getAverageTickTimeMicros() == 32_383.5D, null);
    }

    private static void scenarioZeroCost() {
        System.out.println(head("7. 零耗时"));
        reset(8);
        jadeRead();
        for (int i = 0; i < 8; i++) {
            measure(0);
            gameTick++;
        }
        check("sum == 0", monitor.tickTimeSum == 0, "sum=" + monitor.tickTimeSum);
        check("取值严格等于 0（不是 NaN）", monitor.getAverageTickTimeMicros() == 0.0D, null);
    }

    private static void scenarioNegativeClock() {
        System.out.println(head("8. 时钟倒退（低端不夹取）"));
        reset(8);
        jadeRead();
        measure(-5_000);
        check("时钟倒退会记成负数（nanoTime 正常不会倒退；要保险可以加 Math.max(0, …)）",
                monitor.tickTimeSum == -5_000, "sum=" + monitor.tickTimeSum);
    }

    private static void scenarioStopWhenPlayerLeaves() {
        System.out.println(head("9. 玩家离开的边界（window-1 还在采，window 停采）"));
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
        verifyExact("重新查看后恢复：每刻平均 = 2000 µs");
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
        check("重新开窗后样本清零", monitor.tickTimeCount == 0 && monitor.tickTimeSum == 0 && monitor.tickTimeTicks == 0,
                "count=" + monitor.tickTimeCount + "（开窗前 " + countBefore + "）");
        advance(JADE_PERIOD * 2);
        verifyExact("重新开窗后的平均只含新样本");
    }

    private static void scenarioDegenerateWindowOne() {
        System.out.println(head("11. 退化配置 window = 1"));
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
        System.out.println(head("12. 非法窗口参数"));
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

    private static void scenarioTwoMachines() {
        System.out.println(head("13. 两个机器各自的窗口"));
        reset(16);
        TickTimeMonitorInjected a = newMonitor(16);
        TickTimeMonitorInjected b = newMonitor(16);
        a.getAverageTickTimeMicros();
        b.getAverageTickTimeMicros();
        for (int i = 0; i < 8; i++) {
            costMicros = 2_000;
            a.tickAndMeasure();
            costMicros = 6_000;
            b.tickAndMeasure();
            gameTick++;
        }
        double aValue = a.getAverageTickTimeMicros();
        double bValue = b.getAverageTickTimeMicros();
        check("机器 A 精确等于 2000 µs", aValue == 2_000.0D, "a=" + aValue);
        check("机器 B 精确等于 6000 µs", bValue == 6_000.0D, "b=" + bValue);
        check("两者互不影响（sum / ticks 各自独立）",
                a.tickTimeSum == 16_000 && a.tickTimeTicks == 8 && b.tickTimeSum == 48_000 && b.tickTimeTicks == 8,
                "a: sum=" + a.tickTimeSum + ", ticks=" + a.tickTimeTicks + "; b: sum=" + b.tickTimeSum + ", ticks=" +
                        b.tickTimeTicks);
    }

    private static void scenarioTickWrap() {
        System.out.println(head("14. 游戏刻溢出（int 回绕）"));
        reset(8);
        gameTick = Integer.MAX_VALUE - 5;
        lastCallTick = gameTick;
        jadeRead();
        advance(6); // 从 MAX-5 出发，跨过回绕
        check("确实跨过了 Integer.MAX_VALUE", gameTick < 0, "tick=" + gameTick);
        check("回绕后仍在采样（间隔变成负数按 0 算）", monitor.tickTimeMonitored, "tick=" + gameTick);
        String bad = checkInvariants();
        check("回绕后不变式成立", bad == null, bad);
    }

    private static void scenarioFuzz() {
        System.out.println(head("15. 确定性模糊测试（每步核对模型）"));
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
        verifyExact("模糊测试结束时的取值 == 模型");
    }
}
