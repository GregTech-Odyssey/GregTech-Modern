package com.gregtechceu.gtceu.uipro.animation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 按帧推进的动画调度器，对应 LDLib2 {@code utils.animation.AnimationEngine}（简化为单个 float 数值的过渡）。
 * <p>
 * 由界面外壳持有，在客户端每帧绘制时调用 {@link #updateFrame()}；动画只影响本端外观，不涉及同步。
 * 回调在 {@code updateFrame} 里执行，可以放心开始 / 取消其他动画（遍历的是快照）。
 */
public final class AnimationEngine {

    /** 一个正在播放的过渡，{@link #play} 返回，可提前取消。 */
    public static final class Playback {

        private final Animation animation;
        private final float from, to;
        private final ValueConsumer onValue;
        @Nullable
        private Runnable onFinished;
        private long startNanos = -1;
        private boolean done;

        private Playback(Animation animation, float from, float to, ValueConsumer onValue) {
            this.animation = animation;
            this.from = from;
            this.to = to;
            this.onValue = onValue;
        }

        /** 播放完毕（非取消）后回调。 */
        public Playback onFinished(Runnable onFinished) {
            this.onFinished = onFinished;
            return this;
        }

        /** 停在当前值，不再回调。 */
        public void cancel() {
            done = true;
        }

        public boolean isDone() {
            return done;
        }

        private void update(long now) {
            if (startNanos < 0) startNanos = now;
            float elapsed = (now - startNanos) / 1e9f - animation.delay();
            if (elapsed < 0) return;
            float t = animation.duration() <= 0 ? 1 : Math.min(1, elapsed / animation.duration());
            onValue.accept(from + (to - from) * animation.ease().interpolate(t));
            if (t >= 1) {
                done = true;
                if (onFinished != null) onFinished.run();
            }
        }
    }

    @FunctionalInterface
    public interface ValueConsumer {

        void accept(float value);
    }

    private final List<Playback> playing = new ArrayList<>();

    /** 从 {@code from} 过渡到 {@code to}，每帧把当前值交给 {@code onValue}；下一帧开始计时。 */
    public Playback play(Animation animation, float from, float to, ValueConsumer onValue) {
        var playback = new Playback(animation, from, to, onValue);
        playing.add(playback);
        return playback;
    }

    public boolean isPlaying() {
        return !playing.isEmpty();
    }

    /** 推进所有动画一帧（客户端每帧调用）。 */
    public void updateFrame() {
        if (playing.isEmpty()) return;
        long now = System.nanoTime();
        for (var playback : List.copyOf(playing)) {
            if (!playback.done) playback.update(now);
        }
        playing.removeIf(playback -> playback.done);
    }
}
