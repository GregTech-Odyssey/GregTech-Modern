package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.IntIngredientMap;
import com.gregtechceu.gtceu.utils.function.ObjectLongConsumer;
import com.gregtechceu.gtceu.utils.function.ObjectLongPredicate;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IRecipeHandler<K> extends IFilteredHandler<K> {

    /**
     * matching or handling the given recipe.
     *
     * @param io       the IO type of this recipe. always be one of the {@link IO#IN} or {@link IO#OUT}
     * @param recipe   recipe.
     * @param left     left contents for to be handled.
     * @param simulate simulate.
     * @return left contents for continue handling by other proxies.
     *         <br>
     *         null - nothing left. handling successful/finish. you should always return null as a handling-done mark.
     */
    List<K> handleRecipeInner(IO io, GTRecipe recipe, List<K> left, boolean simulate);

    /**
     * container size, if it has one. otherwise -1.
     */
    default int getSize() {
        return -1;
    }

    /**
     * @return interruption
     */
    default boolean forEachItems(ObjectLongPredicate<ItemStack> function) {
        return false;
    }

    /**
     * @return interruption
     */
    default boolean forEachFluids(ObjectLongPredicate<FluidStack> function) {
        return false;
    }

    default void fastForEachItems(ObjectLongConsumer<ItemStack> function) {}

    default void fastForEachFluids(ObjectLongConsumer<FluidStack> function) {}

    default IntIngredientMap getIngredientMap(@NotNull GTRecipeType type) {
        return IntIngredientMap.EMPTY;
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

    default boolean isRecipeOnly() {
        return false;
    }

    /**
     * Whether the content of same capability can only be handled distinct.
     */
    default boolean isDistinct() {
        return false;
    }

    /**
     * Returns {@code true} if this {@code IRecipeHandler} has content to be searched.
     * The main use of this is differentiating circuit inventories from item inventories
     * 
     * @return {@code true} if this {@code IRecipeHandler} has content to be searched
     */
    default boolean shouldSearchContent() {
        return true;
    }

    RecipeCapability<K> getCapability();

    @SuppressWarnings("unchecked")
    default K copyContent(Object content) {
        return getCapability().copyInner((K) content);
    }

    default List<K> handleRecipe(IO io, GTRecipe recipe, List<?> left, boolean simulate) {
        List<K> contents = new ObjectArrayList<>(left.size());
        for (Object leftObj : left) {
            contents.add(copyContent(leftObj));
        }
        return handleRecipeInner(io, recipe, contents, simulate);
    }
}
