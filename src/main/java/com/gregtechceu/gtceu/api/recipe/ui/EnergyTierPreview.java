package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.*;

public final class EnergyTierPreview implements RecipeTierPreview {

    private static final String TOTAL_EU = "gtceu.recipe.info.total_eu";
    private static final String MAX_EU = "gtceu.recipe.info.max_eu";
    private static final String EU_USAGE = "gtceu.recipe.info.eu_usage";
    private static final String EU_GENERATION = "gtceu.recipe.info.eu_generation";
    private static final String OVERCLOCK_INFO = "gtceu.recipe.info.overclock";
    private static final String OVERCLOCK_PERFECT = "gtceu.recipe.info.overclock_perfect";

    private final GTRecipeDefinition recipe;
    private final boolean input;

    private EnergyTierPreview(GTRecipeDefinition recipe, boolean input) {
        this.recipe = recipe;
        this.input = input;
    }

    @Nullable
    public static EnergyTierPreview of(GTRecipeDefinition recipe) {
        if (recipe.getInputEUt() > 0) return new EnergyTierPreview(recipe, true);
        if (recipe.getOutputEUt() > 0) return new EnergyTierPreview(recipe, false);
        return null;
    }

    @Override
    public int minTier() {
        return recipe.tier;
    }

    @Override
    public int maxTier() {
        return input ? GTValues.MAX : recipe.tier;
    }

    @Override
    public Component tierName(int tier) {
        return Component.literal(VN[tier]).withStyle(tierFormats(tier));
    }

    public static ChatFormatting[] tierFormats(int tier) {
        var codes = VCF[Math.min(tier, VCF.length - 1)];
        var formats = new ChatFormatting[codes.length() / 2];
        for (int i = 0; i < formats.length; i++) formats[i] = ChatFormatting.getByCode(codes.charAt(i * 2 + 1));
        return formats;
    }

    @Override
    public List<Component> tooltip() {
        return List.of(Component.translatable(OVERCLOCK_INFO, VNF[recipe.tier]),
                Component.translatable(OVERCLOCK_PERFECT).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean hasStepper() {
        return input;
    }

    @Override
    public boolean supportsPerfect() {
        return true;
    }

    @Override
    public int duration(int tier, boolean perfect) {
        int overclocks = input ? tier - recipe.tier : 0;
        if (overclocks <= 0) return recipe.duration;
        return Math.max(1, (int) (recipe.duration / Math.pow(perfect ? 4 : 2, overclocks)));
    }

    @Override
    public List<String> rowLabels() {
        return List.of(isTotalCwu(recipe) ? MAX_EU : TOTAL_EU, input ? EU_USAGE : EU_GENERATION);
    }

    @Override
    public Component rowValue(int row, int tier, boolean perfect) {
        long eut = input ? (long) (recipe.getInputEUt() * Math.pow(4, tier - recipe.tier)) : recipe.getOutputEUt();
        if (row == 0) {
            long energy = eut * duration(tier, perfect);
            if (isTotalCwu(recipe)) energy /= Math.max(recipe.data.getLong(GTRecipeDataKeys.CWUT), 1);
            return Component.literal(FormattingUtil.formatNumbers(energy) + " EU");
        }
        int voltageTier = GTUtil.getTierByVoltage(eut);
        var amperage = Component.translatable("gtceu.recipe.eu.tier", FormattingUtil.formatNumber2Places((float) eut / V[voltageTier]), VN[voltageTier]);
        return Component.literal(FormattingUtil.formatNumbers(eut) + " EU/t")
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, amperage)));
    }

    private static boolean isTotalCwu(GTRecipeDefinition recipe) {
        return recipe.data.getBoolean(GTRecipeDataKeys.DURATION_IS_TOTAL_CWU) && recipe.data.containsKey(GTRecipeDataKeys.CWUT);
    }
}
