package com.gregtechceu.gtceu.api.misc;

/**
 * tick 耗时采样器：和 {@link TickTimeMonitor} 同一套算法、同一套字段名，区别只有「怎么开始 / 结束一次测量」。
 *
 * <p>
 * 那边是订阅任务（挂成 {@link Runnable}，由订阅自己兜住开始 / 结束），这边是外部代码显式插点：
 * {@link #insertStart(int)} / {@link #insertEnd()}，所以不需要方块实体、也不需要按名字缓存（AE 网络、多方块
 * 结构检查这类不是订阅任务、甚至不是方块实体的路径用它）。
 *
 * <p>
 * 平均的分母不是样本条数，而是这些样本跨越的游戏刻数（本次调用刻 - 上次调用刻），也就是「按刻摊销」；只有被查看
 * （{@link #getAverageTickTimeMicros()}）时才采样。
 *
 * <p>
 * {@code tickTimeSum / tickTimeTicks / lastAverageTickTimeMicros / viewed} 是 {@code volatile} 的：写入方可能
 * 在异步线程上（多方块结构检查就是这样），读取方（Jade）在服务端线程上。环形缓冲本身只由写入方碰。
 */
public final class TickTimeSampler {

    /** 采样窗口：保留最近这么多次调用，同时也是「多久没被查看就停采」的刻数阈值。 */
    private final short tickTimeWindow;

    /** 每次调用耗时（微秒）的环形缓冲，第一次被查看时才分配，没被看过的对象是 {@code null}。 */
    private short[] tickTimes;
    /** 每条样本算进去的刻数（本次调用刻 - 上次调用刻），把「每次调用」摊成「每刻」。 */
    private short[] tickSpans;
    /** 窗口内耗时总和（微秒）、样本条数、环形缓冲下标。 */
    private volatile int tickTimeSum;
    private short tickTimeCount;
    private short tickTimeIndex;
    /**
     * 平均的分母：样本跨越的刻数。一次采样可能跨很多刻（条件订阅的路径可能几百刻才跑一次），用 short 会溢出，
     * 所以这里必须是 int。
     */
    private volatile int tickTimeTicks;
    /** 缓冲满了之后算出的平均每刻耗时（微秒）。 */
    private volatile float lastAverageTickTimeMicros;

    /** 是否在采样。 */
    private volatile boolean tickTimeMonitored;
    /** 最后一次被查看的游戏刻。 */
    private int tickTimeLastViewed = Integer.MIN_VALUE;
    /** 上一次测量的游戏刻。 */
    private int tickTimeLastMeasured = Integer.MIN_VALUE;
    /** 上一次记样本的游戏刻；开窗时对齐到 {@code tick - 1}，让开窗那一刻也算一刻。 */
    private int tickTimeLastSampleTick = Integer.MIN_VALUE;

    /** 采样器特有的：{@link #getAverageTickTimeMicros()} 拿不到游戏刻，先把「有人在看」记下来，插点时再续期。 */
    private volatile boolean viewed;
    /** 采样器特有的：这次测量的刻与开始时刻（纳秒）。 */
    private int pendingTick = Integer.MIN_VALUE;
    private long startNanos;

    public TickTimeSampler() {
        this(40);
    }

    public TickTimeSampler(int window) {
        // 上限取 Short.MAX_VALUE：窗口用 short 存；tickTimeSum / tickTimeTicks 用 int 累加也不溢出（32767 × 32767 < 2^31）
        if (window <= 0 || window > Short.MAX_VALUE) {
            throw new IllegalArgumentException("window must be in [1, " + Short.MAX_VALUE + "], got " + window);
        }
        this.tickTimeWindow = (short) window;
    }

    /**
     * 最近一轮窗口的平均每刻耗时（微秒）。
     *
     * <p>
     * 调用即代表「现在有人在看」：下一次 {@link #insertStart(int)} 会续期观察时间，没人看超过
     * {@link #tickTimeWindow} 刻后自动停采。还没采到样本时返回 0。
     */
    public long getAverageTickTimeMicros() {
        viewed = true;
        return (long) averageTickTimeMicros();
    }

    /**
     * 插入一个开始点，可以多次调用（例如一个游戏刻里分几段测）。
     *
     * <p>
     * 同一个刻里重复插开始只按第一次算；距上次被查看超过 {@link #tickTimeWindow} 刻就当作没人看，直接不测。
     *
     * @param tick 当前游戏刻
     */
    public void insertStart(int tick) {
        if (viewed) {
            viewed = false;
            tickTimeLastViewed = tick;
        }
        if (!tickTimeMonitored) {
            if (tickTimeLastViewed == Integer.MIN_VALUE || tick - tickTimeLastViewed >= tickTimeWindow) return;
            startMonitoring(tick);
        }
        if (pendingTick == tick) return;
        pendingTick = tick;
        tickTimeLastMeasured = tick;
        startNanos = System.nanoTime();
    }

    /** 插入一个结束点：记一次调用耗时，没有配对的开始点就忽略。 */
    public void insertEnd() {
        if (pendingTick == Integer.MIN_VALUE) return;
        int tick = pendingTick;
        pendingTick = Integer.MIN_VALUE;
        recordTickTime(tick, (System.nanoTime() - startNanos) / 1000L);
        if (tickTimeLastMeasured - tickTimeLastViewed >= tickTimeWindow) {
            // 玩家已经这么多刻没看了
            tickTimeMonitored = false;
        }
    }

    // ↓↓↓ 以下四个方法和 TickTimeMonitor 逐行一致 ↓↓↓

    /**
     * 开始采样：环形缓冲第一次用到才分配。
     *
     * <p>
     * 「上次测量刻」也要一起对齐到当下，否则超时判断立刻成立（初值是 {@link Integer#MIN_VALUE}），采样会在开 /
     * 停之间来回跳，每跳一次都把窗口清零。
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

    /**
     * 记一次调用耗时（微秒）。
     *
     * <p>
     * 分母记的是「本次调用刻 - 上次调用刻」的间隔，同一刻里的额外调用间隔算 0（不重复占刻数）。
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
