package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.datasream.DataComponentKey;
import com.gto.datasynclib.datasream.DataComponentRegistry;

public final class GTRecipeDataKeys {

    public static final DataComponentRegistry REGISTRY = new DataComponentRegistry("recipe");

    static {
        REGISTRY.unfreeze();
    }

    public static final DataComponentKey<Integer> EBF_TEMP = REGISTRY.register("ebf_temp", CombinationCodec.INT_CODEC);
    public static final DataComponentKey<Integer> SOLDER_MULTIPLIER = REGISTRY.register("solder_multiplier", CombinationCodec.INT_CODEC);
    public static final DataComponentKey<Boolean> DISABLE_DISTILLERY = REGISTRY.register("disable_distillery", CombinationCodec.BOOLEAN_CODEC);
    public static final DataComponentKey<Long> EU_TO_START = REGISTRY.register("eu_to_start", CombinationCodec.LONG_CODEC);
    public static final DataComponentKey<Boolean> SCAN_FOR_RESEARCH = REGISTRY.register("scan_for_research", CombinationCodec.BOOLEAN_CODEC);
    public static final DataComponentKey<Boolean> DURATION_IS_TOTAL_CWU = REGISTRY.register("duration_is_total_cwu", CombinationCodec.BOOLEAN_CODEC);
    public static final DataComponentKey<Boolean> HIDE_DURATION = REGISTRY.register("hide_duration", CombinationCodec.BOOLEAN_CODEC);

    public static void init() {
        AddonFinder.getAddons().forEach(IGTAddon::registerRecipeDataKey);
        REGISTRY.freeze();
    }
}
