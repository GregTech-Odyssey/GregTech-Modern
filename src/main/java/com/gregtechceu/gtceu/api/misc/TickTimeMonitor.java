package com.gregtechceu.gtceu.api.misc;

import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.datasynclib.datastream.DataComponentRegistry;

import java.util.function.IntSupplier;

/**
 * tick 耗时统计：只在被查看（Jade 取数据）时采样，统计最近 {@link #tickTimeWindow} 次调用的平均<b>每刻</b>耗时
 * （微秒）。
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
    public final short tickTimeWindow;
    /** 游戏刻来源（{@code holder::getOffsetTimer}）。 */
    public final IntSupplier tickTimer;
    /** 被测量的任务（{@code serverTick} 之类）。注册之后不能再换，见 {@code monitorTick} 的说明。 */
    public final Runnable task;

    /** 每次调用耗时（微秒）的环形缓冲，第一次被查看时才分配，没被看过的机器是 {@code null}。 */
    public short[] tickTimes;
    /** 每条样本算进去的刻数（本次调用刻 - 上次调用刻），把「每次调用」摊成「每刻」。 */
    public short[] tickSpans;
    /** 窗口内耗时总和（微秒）、样本条数、样本跨越的刻数（平均的分母）。 */
    public int tickTimeSum;
    public short tickTimeCount;
    /**
     * 平均的分母：样本跨越的刻数。一次采样可能跨很多刻（条件订阅的路径可能几百刻才跑一次），用 short 会溢出，
     * 所以这里必须是 int。
     */
    public int tickTimeTicks;
    public short tickTimeIndex;
    /** 缓冲满了之后算出的平均每刻耗时（微秒）。 */
    public float lastAverageTickTimeMicros;

    /** 上一次记样本的游戏刻；开窗时对齐到 {@code tick - 1}，让开窗那一刻也算一刻。 */
    public int tickTimeLastSampleTick = Integer.MIN_VALUE;

    /** 是否在采样。 */
    public boolean tickTimeMonitored;
    /** 最后一次被查看的游戏刻。 */
    public int tickTimeLastViewed = Integer.MIN_VALUE;
    /** 上一次测量的游戏刻。 */
    public int tickTimeLastMeasured = Integer.MIN_VALUE;

    /** 同一次 Jade 取数据会问 7 遍（null 侧 + 6 个方向），同一刻里直接回答上次的结果。 */
    public int tickTimeAnswerTick = Integer.MIN_VALUE;
    public float tickTimeAnswer;

    public TickTimeMonitor(int tickTimeWindow, IntSupplier tickTimer, Runnable task) {
        // 窗口用 short 存，所以上限短路到 Short.MAX_VALUE；tickTimeSum 用 int 累加也不溢出（32767 × 32767 < 2^31）
        if (tickTimeWindow <= 0 || tickTimeWindow > Short.MAX_VALUE) {
            throw new IllegalArgumentException("tickTimeWindow must be in [1, " + Short.MAX_VALUE + "], got " +
                    tickTimeWindow);
        }
        this.tickTimeWindow = (short) tickTimeWindow;
        this.tickTimer = tickTimer;
        this.task = task;
    }

    /**
     * 最近一轮窗口的平均每刻耗时（微秒），Jade 取数据时调用。
     *
     * <p>
     * 这里也承担「有人在看」的信号：每次调用都会续期 {@link #tickTimeLastViewed}。同一个游戏刻里重复调用
     * （Jade 一次取数据要问 7 遍）直接返回上次的结果，不再动状态。
     */
    public float getAverageTickTimeMicros() {
        int tick = tickTimer.getAsInt();
        if (tick == tickTimeAnswerTick) return tickTimeAnswer;
        tickTimeAnswerTick = tick;
        tickTimeAnswer = onViewed(tick);
        return tickTimeAnswer;
    }

    private float onViewed(int tick) {
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
        return averageTickTimeMicros();
    }

    /**
     * 开始采样：环形缓冲第一次用到才分配。
     *
     * <p>
     * 「上次测量刻」也要一起对齐到当下，否则 {@link #onViewed} 里的超时判断立刻成立（初值是
     * {@link Integer#MIN_VALUE}），采样会在开 / 停之间来回跳，每跳一次都把窗口清零。
     */
    private void startMonitoring(int tick) {
        if (tickTimes == null) {
            tickTimes = new short[tickTimeWindow];
            tickSpans = new short[tickTimeWindow];
        }
        tickTimeSum = 0;
        tickTimeCount = 0;
        tickTimeTicks = 0;
        tickTimeIndex = 0;
        lastAverageTickTimeMicros = 0.0F;
        tickTimeLastMeasured = tick;
        tickTimeLastSampleTick = tick - 1;
        tickTimeMonitored = true;
    }

    /** 最近一轮窗口的平均每刻耗时（微秒）；还没采到样本时按 0 算。 */
    private float averageTickTimeMicros() {
        return tickTimeTicks == 0 ? lastAverageTickTimeMicros : tickTimeSum / (float) tickTimeTicks;
    }

    /** 外部直调用它来省一次 {@link #run()} 的调用开销；不做「有没有人在看」的判断。 */
    public void tickAndMeasure() {
        int tick = tickTimer.getAsInt();
        tickTimeLastMeasured = tick;
        long start = System.nanoTime();
        try {
            task.run();
        } finally {
            recordTickTime(tick, (System.nanoTime() - start) / 1000L);
            if (tickTimeLastMeasured - tickTimeLastViewed >= tickTimeWindow) {
                // 玩家已经这么多刻没看了
                tickTimeMonitored = false;
            }
        }
    }

    @Override
    public void run() {
        // 订阅的热路径：这里内联，不转调 tickAndMeasure()
        if (!tickTimeMonitored) {
            task.run();
            return;
        }
        int tick = tickTimer.getAsInt();
        tickTimeLastMeasured = tick;
        long start = System.nanoTime();
        try {
            task.run();
        } finally {
            recordTickTime(tick, (System.nanoTime() - start) / 1000L);
            if (tickTimeLastMeasured - tickTimeLastViewed >= tickTimeWindow) {
                // 玩家已经这么多刻没看了
                tickTimeMonitored = false;
            }
        }
    }

    /**
     * 记一次调用耗时（微秒）。
     *
     * <p>
     * 分母记的是「本次调用刻 - 上次调用刻」的间隔，同一刻里的额外调用间隔算 0（不重复占刻数）。比起给空过的刻补
     * 0 样本，这样每次调用只是多写一个 {@code short}。
     *
     * @param tick          本次调用所在的游戏刻
     * @param elapsedMicros 本次调用耗时（微秒），封顶 {@link Short#MAX_VALUE} 保证 {@link #tickTimeSum} 不溢出
     */
    private void recordTickTime(int tick, long elapsedMicros) {
        int span = tick - tickTimeLastSampleTick;
        if (span < 0) span = 0; // 刻回绕
        tickTimeLastSampleTick = tick;
        pushSample((int) Math.min(elapsedMicros, Short.MAX_VALUE), Math.min(span, Short.MAX_VALUE));
    }

    /** 缓冲没满就往后放，满了就顶掉最旧的那个样本（耗时和它占的刻数一起顶掉）。 */
    private void pushSample(int micros, int span) {
        short sample = (short) micros;
        short ticks = (short) span;
        if (tickTimeCount == tickTimeWindow) {
            tickTimeSum -= tickTimes[tickTimeIndex];
            tickTimeTicks -= tickSpans[tickTimeIndex];
        } else {
            tickTimeCount++;
        }
        tickTimes[tickTimeIndex] = sample;
        tickSpans[tickTimeIndex] = ticks;
        tickTimeSum += sample;
        tickTimeTicks += ticks;
        if (++tickTimeIndex >= tickTimeWindow) {
            tickTimeIndex = 0;
        }
        if (tickTimeCount == tickTimeWindow) {
            lastAverageTickTimeMicros = averageTickTimeMicros();
        }
    }
}
