package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeDisplaySlots;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class DimensionCondition extends RecipeCondition {

    public final ResourceKey<Level> dimension;

    public DimensionCondition(boolean isReverse, ResourceKey<Level> dimension) {
        super(isReverse);
        this.dimension = dimension;
    }

    /** 配方页上以展示槽显示所在维度的标志物品（悬停看维度名，可选显示维度等级）。 */
    @Override
    public void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        info.slot(this::createDimensionSlot);
    }

    @Override
    public boolean isOr() {
        return true;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.dimension.tooltip", dimension);
    }

    /** 维度标志物品的展示槽。 */
    public Widget createDimensionSlot() {
        DimensionMarker dimMarker = GTRegistries.DIMENSION_MARKERS.getOrDefault(this.dimension.location(), new DimensionMarker(DimensionMarker.MAX_TIER, () -> Blocks.BARRIER, this.dimension.toString()));
        var dimSlot = RecipeDisplaySlots.item(dimMarker.getIcon(), IngredientIO.INPUT);
        if (ConfigHolder.INSTANCE.compat.showDimensionTier) {
            dimSlot.setOverlay(new TextTexture("T" + (dimMarker.tier >= DimensionMarker.MAX_TIER ? "?" : dimMarker.tier)).scale(0.75F).transform(-3.0F, 5.0F));
        }
        return dimSlot;
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        return level != null && dimension == level.dimension();
    }
}
