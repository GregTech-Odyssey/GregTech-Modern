package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.GTValues;

public interface IOverclockMachine extends IMachineFeature {

    /** 超频次数限制的上限。 */
    int MAX_OVERCLOCK_COUNT = 30;

    int getOverclockTier();

    void setOverclockTier(int tier);

    int getMaxOverclockTier();

    int getMinOverclockTier();

    default long getOverclockVoltage() {
        return GTValues.V[getOverclockTier()];
    }

    /** 配方时长下限：时长压到这么多 tick 之后不再缩短，改为折算内容倍率。 */
    default int getOverclockLimit() {
        return 5;
    }

    /**
     * 超频次数限制：这台机器最多超频几次，0 表示禁止超频。
     * <p>
     * 与时长下限 {@link #getOverclockLimit()} 同时生效，先到哪个按哪个停。
     */
    default int getOverclockCountLimit() {
        return MAX_OVERCLOCK_COUNT;
    }

    default void setOverclockCountLimit(int count) {}

    /** 是否在界面里给出超频相关的配置项。 */
    default boolean hasOverclockConfig() {
        return true;
    }
}
