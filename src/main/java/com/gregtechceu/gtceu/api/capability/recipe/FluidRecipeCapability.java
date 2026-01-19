package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.SerializerFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidStackList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.fast.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.List;
import java.util.stream.Collectors;

public class FluidRecipeCapability extends RecipeCapability<FluidIngredient> {

    public final static FluidRecipeCapability CAP = new FluidRecipeCapability();

    protected FluidRecipeCapability() {
        super("fluid", 0xFF3C70EE, true, 1, SerializerFluidIngredient.INSTANCE);
    }

    @Override
    public void convert(FluidIngredient ingredient, IntLongMap map) {
        if (ingredient.value instanceof Fluid fluid) {
            map.add(fluid.hashCode(), ingredient.amount);
        } else if (ingredient.value instanceof TagKey<?> tagKey) {
            map.add(tagKey.hashCode(), ingredient.amount);
        }
    }

    @Override
    public FluidIngredient copyInner(FluidIngredient content) {
        return content.copy();
    }

    @Override
    public FluidIngredient copyWithModifier(FluidIngredient content, ContentModifier modifier) {
        var amount = modifier.apply(content.amount);
        return amount == content.amount ? content.copy() : content.copy(modifier.apply(content.amount));
    }

    @Override
    public boolean isRecipeSearchFilter() {
        return true;
    }

    @Override
    public @NotNull List<Object> createXEIContainerContents(List<Content> contents, GTRecipe recipe, IO io) {
        List<Object> entryLists = contents.stream()
                .map(Content::getContent)
                .map(this::of)
                .map(FluidRecipeCapability::mapFluid)
                .collect(Collectors.toList());

        while (entryLists.size() < recipe.recipeType.getMaxOutputs(this)) entryLists.add(null);
        return entryLists;
    }

    public Object createXEIContainer(List<?> contents) {
        // cast is safe if you don't pass the wrong thing.
        // noinspection unchecked
        return new CycleFluidEntryHandler((List<FluidEntryList>) contents);
    }

    @NotNull
    @Override
    public Widget createWidget() {
        TankWidget tank = new TankWidget();
        tank.initTemplate();
        tank.setFillDirection(ProgressTexture.FillDirection.ALWAYS_FULL);
        return tank;
    }

    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return TankWidget.class;
    }

    @Override
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipe recipe,
                                @Nullable Content content,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof TankWidget tank) {
            if (storage instanceof IFluidHandler fluidHandler) {
                tank.setFluidTank(fluidHandler, index);
            }
            tank.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
            tank.setAllowClickFilled(!isXEI);
            tank.setAllowClickDrained(!isXEI && io.support(IO.IN));
            if (isXEI) tank.setShowAmount(false);
            if (content != null) {
                float chance = (float) recipeType.getChanceFunction()
                        .getBoostedChance(content, recipeTier, chanceTier) / Content.MAX_CHANCE;
                tank.setXEIChance(chance);
                tank.setOnAddedTooltips((w, tooltips) -> {
                    FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content);
                    if (!isXEI && ingredient.getStacks().length > 0) {
                        FluidStack stack = ingredient.getStacks()[0];
                        TooltipsHandler.appendFluidTooltips(stack, tooltips::add, TooltipFlag.NORMAL);
                    }

                    GTRecipeWidget.setConsumedChance(content,
                            recipe.getChanceLogicForCapability(this, io, isTickSlot(index, io, recipe)),
                            tooltips, recipeTier, chanceTier, recipeType.getChanceFunction());
                    if (isTickSlot(index, io, recipe)) {
                        tooltips.add(Component.translatable("gtceu.gui.content.per_tick"));
                    }
                });
                if (io == IO.IN && (content.chance == 0)) {
                    tank.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    // Maps fluids to a FluidEntryList for XEI: either a FluidTagList or a FluidStackList
    public static FluidEntryList mapFluid(FluidIngredient ingredient) {
        int amount = ingredient.getAmount();
        CompoundTag nbt = ingredient.nbt;
        if (ingredient.value instanceof Fluid fluid) {
            return FluidStackList.of(new FluidStack(fluid, amount, nbt));
        } else if (ingredient.value instanceof TagKey tag) {
            return FluidTagList.of(tag, amount, nbt);
        }
        return new FluidStackList();
    }
}
