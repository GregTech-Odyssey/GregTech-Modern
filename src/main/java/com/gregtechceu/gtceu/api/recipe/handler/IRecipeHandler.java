package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.fast.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.ObjLongConsumer;

public interface IRecipeHandler extends IFilteredHandler {

    /**
     * @return interruption
     */
    default boolean forEachItems(ObjLongPredicate<ItemStack> function) {
        return false;
    }

    /**
     * @return interruption
     */
    default boolean forEachFluids(ObjLongPredicate<FluidStack> function) {
        return false;
    }

    default void fastForEachItems(ObjLongConsumer<ItemStack> function) {}

    default void fastForEachFluids(ObjLongConsumer<FluidStack> function) {}

    default IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        return IntLongMap.EMPTY;
    }

    default boolean isAvailable() {
        return true;
    }

    default boolean isEmpty() {
        return true;
    }

    default boolean isNotConsumable() {
        return false;
    }

    default boolean canHandleContent() {
        return canHandleItem() || canHandleFluid();
    }

    default boolean canHandleItem() {
        return false;
    }

    default boolean canHandleFluid() {
        return false;
    }

    default boolean isInfiniteOutputItem() {
        return false;
    }

    default boolean isInfiniteOutputFluid() {
        return false;
    }

    default boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        return false;
    }

    default boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
        return false;
    }
}
