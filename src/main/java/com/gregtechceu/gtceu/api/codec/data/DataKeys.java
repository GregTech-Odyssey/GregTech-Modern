package com.gregtechceu.gtceu.api.codec.data;

import com.gregtechceu.gtceu.api.codec.ByteStreamCodec;
import com.gregtechceu.gtceu.api.recipe.content.CWUTContent;
import com.gregtechceu.gtceu.api.recipe.content.EUTContent;
import com.gregtechceu.gtceu.api.recipe.content.TickContent;

import com.fast.fastcollection.O2OOpenCacheHashMap;

public class DataKeys {

    public static final O2OOpenCacheHashMap<String, DataKey<?>> MAP = new O2OOpenCacheHashMap<>();

    public static synchronized <T> void register(DataKey<T> key) {
        MAP.put(key.name, key);
    }

    public static final TickContent EUT = EUTContent.INSTANCE;
    public static final TickContent CWUT = CWUTContent.INSTANCE;

    public static final DataKey<Integer> EBF_TEMP = DataKey.register("ebf_temp", ByteStreamCodec.INT_CODEC);

    public static final DataKey<Integer> SOLDER_MULTIPLIER = DataKey.register("solder_multiplier", ByteStreamCodec.INT_CODEC);

    public static final DataKey<Boolean> DISABLE_DISTILLERY = DataKey.register("disable_distillery", ByteStreamCodec.BOOLEAN_CODEC);

    public static final DataKey<Long> EU_TO_START = DataKey.register("eu_to_start", ByteStreamCodec.LONG_CODEC);

    public static final DataKey<Boolean> DURATION_IS_TOTAL_CWU = DataKey.register("duration_is_total_cwu", ByteStreamCodec.BOOLEAN_CODEC);

    public static final DataKey<Boolean> HIDE_DURATION = DataKey.register("hide_duration", ByteStreamCodec.BOOLEAN_CODEC);
}
