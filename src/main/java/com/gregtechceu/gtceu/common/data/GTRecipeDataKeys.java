package com.gregtechceu.gtceu.common.data;

import com.gto.datasynclib.datasream.DataComponentKey;
import com.gto.datasynclib.datasream.DataComponentRegistry;
import com.gto.datasynclib.datasream.codec.DataCodec;

public final class GTRecipeDataKeys {

    public static final DataComponentRegistry REGISTRY = new DataComponentRegistry();

    public static final DataComponentKey<Integer> EBF_TEMP = REGISTRY.register("ebf_temp", DataCodec.INT_CODEC);
    public static final DataComponentKey<Integer> SOLDER_MULTIPLIER = REGISTRY.register("solder_multiplier", DataCodec.INT_CODEC);
    public static final DataComponentKey<Boolean> DISABLE_DISTILLERY = REGISTRY.register("disable_distillery", DataCodec.BOOLEAN_CODEC);
    public static final DataComponentKey<Long> EU_TO_START = REGISTRY.register("eu_to_start", DataCodec.LONG_CODEC);
    public static final DataComponentKey<Boolean> SCAN_FOR_RESEARCH = REGISTRY.register("scan_for_research", DataCodec.BOOLEAN_CODEC);
    public static final DataComponentKey<Boolean> DURATION_IS_TOTAL_CWU = REGISTRY.register("duration_is_total_cwu", DataCodec.BOOLEAN_CODEC);
    public static final DataComponentKey<Boolean> HIDE_DURATION = REGISTRY.register("hide_duration", DataCodec.BOOLEAN_CODEC);

    static {
        REGISTRY.freeze();
    }
}
