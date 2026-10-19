package com.gregtechceu.gtceu.uiwidgets.vein;

import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinEntry;
import com.gregtechceu.gtceu.api.machine.trait.BedrockVeinDrillLogic;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeIOList;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Screen readouts of a bedrock vein drill: the vein below, its remaining reserve, the drilling rate and the expected
 * outputs of one operation.
 */
public final class VeinDrillReadouts {

    public static final String VEIN = "gtceu.gui.vein_drill.vein";
    public static final String NO_VEIN = "gtceu.gui.vein_drill.no_vein";
    public static final String RESERVE = "gtceu.jei.vein.reserve";
    public static final String ORE_RATE = "gtceu.multiblock.ore_rig.ore_amount";
    public static final String FLUID_RATE = "gtceu.multiblock.fluid_rig.fluid_amount";

    private static final Component NONE = Component.translatable(NO_VEIN).withStyle(ChatFormatting.RED);

    private VeinDrillReadouts() {}

    public static void add(MultiblockPage page, BedrockVeinDrillLogic<?> logic, boolean fluid) {
        page.addReading(VEIN, MultiblockPage.cachedRef(logic::getVein, vein -> vein == null ? NONE : vein.getName()));
        var reserve = new ProgressBar.Progress[] { ProgressBar.Progress.EMPTY };
        page.addBar(RESERVE, UITheme::barProgress, () -> {
            long total = logic.getVein() == null ? 0 : BedrockVeinEntry.MAX_OPERATIONS;
            int current = logic.getOperationsRemaining();
            var progress = reserve[0];
            if (progress.current() != current || progress.total() != total) reserve[0] = progress = new ProgressBar.Progress(current, total, 0);
            return progress;
        }).percent().bindDetail(MultiblockPage.fractionText(logic::getOperationsRemaining, () -> logic.getVein() == null ? 0 : BedrockVeinEntry.MAX_OPERATIONS, ""));
        page.addReading(fluid ? FLUID_RATE : ORE_RATE, MultiblockPage.cached(logic::producedPerSecond, perSecond -> Component.translatable(RecipeIOList.PER_SECOND,
                FormattingUtil.formatNumberReadable(perSecond, fluid, FormattingUtil.DECIMAL_FORMAT_1F, fluid ? "B" : null))));
        page.setRecipeList(RecipeIOList.of(logic::getExpectedOutputs));
    }
}
