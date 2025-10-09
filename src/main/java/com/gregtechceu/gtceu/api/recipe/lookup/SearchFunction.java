package com.gregtechceu.gtceu.api.recipe.lookup;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;

import java.util.Iterator;
import java.util.function.Predicate;

@FunctionalInterface
public interface SearchFunction<T, R> {

    Iterator<R> search(IRecipeCapabilityHolder holder, T type, RecipeHandlerList handlerList, Predicate<R> canHandle);
}
