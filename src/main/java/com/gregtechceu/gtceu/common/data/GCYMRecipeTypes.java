package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeDisplaySlots;
import com.gregtechceu.gtceu.utils.FormattingUtil;


import net.minecraft.network.chat.Component;

import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.MULTIBLOCK;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.register;
import static com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection.LEFT_TO_RIGHT;

public class GCYMRecipeTypes {

    //////////////////////////////////////
    // ******* Multiblock *******//
    //////////////////////////////////////
    public final static GTRecipeType ALLOY_BLAST_RECIPES = register("alloy_blast_smelter", MULTIBLOCK)
            .setMaxIOSize(9, 0, 3, 1)
            .setEUIO(IO.IN)
            .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, LEFT_TO_RIGHT)
            .setSlotOverlay(false, false, false, GuiTextures.FURNACE_OVERLAY_1)
            .setSlotOverlay(false, false, true, GuiTextures.FURNACE_OVERLAY_1)
            .setSlotOverlay(false, true, false, GuiTextures.FURNACE_OVERLAY_2)
            .setSlotOverlay(false, true, true, GuiTextures.FURNACE_OVERLAY_2)
            .setSlotOverlay(true, true, false, GuiTextures.FURNACE_OVERLAY_2)
            .setSlotOverlay(true, true, true, GuiTextures.FURNACE_OVERLAY_2)
            .addDataRequirement(Component.translatable("gtceu.recipe.info.temperature"),
                    recipe -> Component.translatable("gtceu.recipe.info.kelvin", FormattingUtil.formatNumbers(recipe.data.getInt(GTRecipeDataKeys.EBF_TEMP))))
            .addDataRequirement(Component.translatable("gtceu.recipe.info.coil"), recipe -> {
                ICoilType requiredCoil = ICoilType.getMinRequiredType(recipe.data.getInt(GTRecipeDataKeys.EBF_TEMP));
                if (requiredCoil == null || requiredCoil.getMaterial() == null) return null;
                return requiredCoil.getMaterial().getLocalizedName();
            })
            .setUiBuilder(RecipeDisplaySlots::coilInfo)
            .setSound(GTSoundEntries.ARC);

    public static void init() {}
}
