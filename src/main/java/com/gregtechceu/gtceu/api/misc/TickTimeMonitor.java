package com.gregtechceu.gtceu.api.misc;

import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.datasynclib.datastream.DataComponentRegistry;

import java.util.function.IntSupplier;

/**
 * tick 开销统计：只在被查看（Jade 取数据）时采样，统计最近 {@link #tickTimeWindow} 次调用的平均耗时（微秒）。
 *
 * <p>
 * 环形缓冲第一次被查看时才分配，没被看过的机器不多占内存；没人看时整条路径只是一个 boolean 判断。
 *
 * <p>
 * 注意被监控的 tick 不一定每刻都跑（例如 {@code serverTick} 空闲时是隔几个游戏刻一次），所以窗口覆盖的时间不
 * 固定：一直跑的路径 100 次调用约等于 5 秒。
 */
public final class TickTimeMonitor implements Runnable {

    public static final int TICK_TIME_WINDOW = 40;

    public static final DataComponentRegistry REGISTRY = new DataComponentRegistry("gtceu_tick_time");

    public static final class Entry extends DataComponentKey<TickTimeMonitor> {

        public final int window;

        private Entry(String name, int window) {
            super(name, null);
            this.window = window;
        }
    }

    public static Entry create(String name, int window) {
        return REGISTRY.register(name, new Entry(name, window));
    }

    public static Entry create(String name) {
        return REGISTRY.register(name, new Entry(name, TICK_TIME_WINDOW));
    }

    /** 窗口大小：保留最近这么多次调用，同时也是「多久没被查看就停采」的刻数阈值。 */
    public final int tickTimeWindow;
    /** 游戏刻来源（{@code holder::getOffsetTimer}）。 */
    public final IntSupplier tickTimer;
    /** 被测量的任务（{@code serverTick} 之类）。注册之后不能再换，见 {@code monitorTick} 的说明。 */
    public final Runnable task;

    /** 单次调用耗时（微秒）的环形缓冲，第一次被查看时才分配，没被看过的机器是 {@code null}。 */
    public short[] tickTimes;
    /** 窗口内耗时总和（微秒）、环形缓冲下标、已采样次数。 */
    public int tickTimeSum;
    public int tickTimeIndex;
    public int tickTimeCount;
    /** 缓冲满了之后算出的平均耗时（微秒）。 */
    public double lastAverageTickTimeMicros;

    /** 是否在采样。 */
    public boolean tickTimeMonitored;
    /** 最后一次被查看的游戏刻。 */
    public int tickTimeLastViewed = Integer.MIN_VALUE;
    /** 上一次测量的游戏刻。 */
    public int tickTimeLastMeasured = Integer.MIN_VALUE;

    /** 同一次 Jade 取数据会问 7 遍（null 侧 + 6 个方向），同一刻里直接回答上次的结果。 */
    public int tickTimeAnswerTick = Integer.MIN_VALUE;
    public double tickTimeAnswer;

    public TickTimeMonitor(int tickTimeWindow, IntSupplier tickTimer, Runnable task) {
        // 上限是为了让 tickTimeSum 用 int 累加也不溢出：65536 × Short.MAX_VALUE < Integer.MAX_VALUE
        if (tickTimeWindow <= 0 || tickTimeWindow > 65536) {
            throw new IllegalArgumentException("tickTimeWindow must be in [1, 65536], got " + tickTimeWindow);
        }
        this.tickTimeWindow = tickTimeWindow;
        this.tickTimer = tickTimer;
        this.task = task;
    }

    /**
     * 最近一轮窗口的平均耗时（微秒），Jade 取数据时调用。
     *
     * <p>
     * 这里也承担「有人在看」的信号：每次调用都会续期 {@link #tickTimeLastViewed}。同一个游戏刻里重复调用
     * （Jade 一次取数据要问 7 遍）直接返回上次的结果，不再动状态。
     */
    public double getAverageTickTimeMicros() {
        int tick = tickTimer.getAsInt();
        if (tick == tickTimeAnswerTick) return tickTimeAnswer;
        tickTimeAnswerTick = tick;
        tickTimeAnswer = onViewed(tick);
        return tickTimeAnswer;
    }

    private double onViewed(int tick) {
        tickTimeLastViewed = tick;
        if (!tickTimeMonitored) {
            startMonitoring(tick);
            return 0;
        }
        if (tick - tickTimeLastMeasured >= tickTimeWindow) {
            // 机器这么多刻都没 tick 了，没什么可测的
            tickTimeMonitored = false;
            return 0;
        }
        return tickTimeCount == 0 ? lastAverageTickTimeMicros : tickTimeSum / (double) tickTimeCount;
    }

    /**
     * 开始采样：环形缓冲第一次用到才分配。
     *
     * <p>
     * 「上次测量刻」也要一起对齐到当下，否则 {@link #onViewed} 里的超时判断立刻成立（初值是
     * {@link Integer#MIN_VALUE}），采样会在开 / 停之间来回跳，每跳一次都把窗口清零。
     */
    private void startMonitoring(int tick) {
        if (tickTimes == null) tickTimes = new short[tickTimeWindow];
        tickTimeSum = 0;
        tickTimeIndex = 0;
        tickTimeCount = 0;
        lastAverageTickTimeMicros = 0.0D;
        tickTimeLastMeasured = tick;
        tickTimeMonitored = true;
    }

    public void tickAndMeasure() {
        tickTimeLastMeasured = tickTimer.getAsInt();
        long start = System.nanoTime();
        try {
            task.run();
        } finally {
            recordTickTime((System.nanoTime() - start) / 1000L);
            if (tickTimeLastMeasured - tickTimeLastViewed >= tickTimeWindow) {
                tickTimeMonitored = false;
            }
        }
    }

    @Override
    public void run() {
        if (tickTimeMonitored) {
            tickTimeLastMeasured = tickTimer.getAsInt();
            long start = System.nanoTime();
            try {
                task.run();
            } finally {
                recordTickTime((System.nanoTime() - start) / 1000L);
                if (tickTimeLastMeasured - tickTimeLastViewed >= tickTimeWindow) {
                    tickTimeMonitored = false;
                }
            }
        } else {
            task.run();
        }
    }

    /**
     * 记一次调用耗时：缓冲没满就往后放，满了就顶掉最旧的那个样本。
     *
     * @param elapsedMicros 本次调用耗时（微秒），封顶 {@link Short#MAX_VALUE} 保证 {@link #tickTimeSum} 不溢出
     */
    private void recordTickTime(long elapsedMicros) {
        short sample = (short) Math.min(elapsedMicros, Short.MAX_VALUE);
        if (tickTimeCount == tickTimeWindow) {
            tickTimeSum -= tickTimes[tickTimeIndex];
        } else {
            tickTimeCount++;
        }
        tickTimes[tickTimeIndex] = sample;
        tickTimeSum += sample;
        if (++tickTimeIndex >= tickTimeWindow) {
            tickTimeIndex = 0;
        }
        if (tickTimeCount == tickTimeWindow) {
            lastAverageTickTimeMicros = tickTimeSum / (double) tickTimeWindow;
        }
    }
}
