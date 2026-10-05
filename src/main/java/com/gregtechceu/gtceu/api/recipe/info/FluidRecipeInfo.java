package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidStackList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.stacks.AEFluidKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.List;

public final class FluidRecipeInfo extends ContentRecipeInfo {

    public final static FluidRecipeInfo INSTANCE = new FluidRecipeInfo();

    private FluidRecipeInfo() {
        super("fluid", 0xFF3C70EE, true, 1);
    }

    @Override
    public @NotNull List<Object> createXEIContainerContents(ContentList contents, GTRecipeDefinition recipe, IO io) {
        List<Object> entryLists = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            entryLists.add(mapFluid(contents, i));
        }
        while (entryLists.size() < recipe.recipeType.getMaxOutputs(this)) entryLists.add(null);
        return entryLists;
    }

    @SuppressWarnings("unchecked")
    public Object createXEIContainer(List<?> contents) {
        return new CycleFluidEntryHandler((List<FluidEntryList>) contents);
    }

    @NotNull
    @Override
    public Widget createWidget() {
        var tank = FluidSlot.unbound();
        tank.setFillDirection(ProgressTexture.FillDirection.ALWAYS_FULL);
        return tank;
    }

    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return TankWidget.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipeDefinition recipe,
                                @Nullable ContentList contents,
                                int contentIndex,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof TankWidget tank) {
            if (storage instanceof KeyInventory<?> inv) {
                tank.setFluidTank(new ForgeFluidAdapter((KeyInventory<AEFluidKey>) inv), index);
            } else if (storage instanceof IFluidHandler fluidHandler) {
                tank.setFluidTank(fluidHandler, index);
            }
            tank.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
            tank.setAllowClickFilled(!isXEI);
            tank.setAllowClickDrained(!isXEI && io.support(IO.IN));
            if (isXEI) tank.setShowAmount(false);
            if (contents != null && contentIndex >= 0 && contentIndex < contents.size()) {
                int chance = contents.chance(contentIndex);
                int boost = contents.boost(contentIndex);
                var display = contents.ingredient(contentIndex).displayKey();
                tank.setXEIChance((float) recipe.chanceFunction.getBoostedChance(chance, boost, recipeTier, chanceTier) / ContentList.MAX_CHANCE);
                tank.setOnAddedTooltips((w, tooltips) -> {
                    if (!isXEI && display instanceof AEFluidKey fluidKey) {
                        TooltipsHandler.appendFluidTooltips(fluidKey.getReadOnlyStack(), tooltips::add, TooltipFlag.NORMAL);
                    }
                    GTRecipeWidget.setConsumedChance(chance, boost, tooltips, recipeTier, chanceTier, recipe.chanceFunction);
                });
                if (io == IO.IN && chance == 0) {
                    tank.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    public static FluidEntryList mapFluid(ContentList contents, int i) {
        var ing = contents.ingredient(i);
        int amount = Keys.saturatedInt(contents.amount(i));
        if ((ing.kind() == KeyIngredient.TAG || ing.kind() == KeyIngredient.PREDICATE) && ing.tagKey() != null) {
            var key = ing.displayKey();
            return FluidTagList.of(ing.fluidTagKey(), amount, key instanceof AEFluidKey fk ? fk.getTag() : null);
        }
        var key = ing.key();
        if (key instanceof AEFluidKey fluidKey) return FluidStackList.of(fluidKey.toStack(amount));
        return new FluidStackList(new FluidStack[0]);
    }
}
