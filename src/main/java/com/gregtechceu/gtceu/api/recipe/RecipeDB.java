package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.gto.recipesearch.*;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class RecipeDB extends AbstractRecipeDB<GTRecipeDefinition> {

    RecipeSearcher<GTRecipeDefinition> searchContext = new RecipeSearcher<>();
    private final UnitMatcher unitMatcher = new UnitMatcher();
    private final MapMatcher mapMatcher = new MapMatcher();

    public RecipeDB() {
        super();
    }

    public boolean search(RecipeHandlerUnit unit, IntLongMap map, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        if (rootBranch != null) {
            var matcher = unitMatcher;
            matcher.unit = unit;
            matcher.map = map;
            matcher.canHandle = canHandle;
            searchContext.reset(maxSearchDepth, rootBranch, map, unit != null ? unit.searchKeys(map) : map.toIntArray(), matcher, null);
            if (searchContext.findAny() != null) {
                return true;
            }
        }
        if (!unindexedSerial.isEmpty()) {
            for (var recipe : unindexedSerial) {
                if (canHandle.test(unit, recipe)) return true;
            }
        }
        return false;
    }

    public boolean search(IntLongMap map, Predicate<GTRecipeDefinition> canHandle) {
        if (rootBranch != null) {
            var matcher = mapMatcher;
            matcher.map = map;
            matcher.canHandle = canHandle;
            searchContext.reset(maxSearchDepth, rootBranch, map, map.toIntArray(), matcher, null);
            if (searchContext.findAny() != null) {
                return true;
            }
        }
        if (!unindexedSerial.isEmpty()) {
            for (var recipe : unindexedSerial) {
                if (canHandle.test(recipe)) return true;
            }
        }
        return false;
    }

    @Override
    protected boolean supportsParallel(GTRecipeDefinition recipe) {
        return false;
    }

    @Override
    protected IntLongMap extractIngredientMap(GTRecipeDefinition recipe) {
        var intMap = new IntLongMap();
        var items = recipe.itemInputs;
        for (int i = 0; i < items.size(); i++) recipe.recipeType.convertIngredient(items.ingredient(i), items.amount(i), intMap);
        var fluids = recipe.fluidInputs;
        for (int i = 0; i < fluids.size(); i++) recipe.recipeType.convertIngredient(fluids.ingredient(i), fluids.amount(i), intMap);
        for (var extension : recipe.recipeExtensions) {
            extension.extractInput(recipe, intMap);
        }
        for (var extension : recipe.tickRecipeExtensions) {
            extension.extractInput(recipe, intMap);
        }
        return intMap;
    }

    @Override
    protected void setIngredientTable(GTRecipeDefinition gtRecipe, IngredientTable intMapContainer) {
        gtRecipe.container = intMapContainer;
    }

    @Override
    protected void finishBuild() {
        super.finishBuild();
        if (unindexedSerial.isEmpty()) return;
        GTCEu.LOGGER.warn("Unindexed: {}", unindexedSerial);
    }

    private static final class UnitMatcher implements Predicate<GTRecipeDefinition> {

        RecipeHandlerUnit unit;
        IntLongMap map;
        BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle;

        @Override
        public boolean test(GTRecipeDefinition r) {
            return r.container.match(map) && canHandle.test(unit, r);
        }
    }

    private static final class MapMatcher implements Predicate<GTRecipeDefinition> {

        IntLongMap map;
        Predicate<GTRecipeDefinition> canHandle;

        @Override
        public boolean test(GTRecipeDefinition r) {
            return r.container.match(map) && canHandle.test(r);
        }
    }
}
