package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import java.util.List;

@FunctionalInterface
public interface ParallelFunction<T> {

    long getParallel(IRecipeLogicMachine holder, List<Content> contents, long parallelAmount, T args);
}
