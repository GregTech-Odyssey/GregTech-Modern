package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.extension.RecipeExtension;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface RecipeTierPreview {

    int minTier();

    int maxTier();

    Component tierName(int tier);

    List<Component> tooltip();

    boolean supportsPerfect();

    int duration(int tier, boolean perfect);

    List<String> rowLabels();

    Component rowValue(int row, int tier, boolean perfect);

    default boolean replaces(RecipeExtension<?> extension) {
        return false;
    }

    boolean hasStepper();

    List<Function<GTRecipeDefinition, @Nullable RecipeTierPreview>> FACTORIES = new ArrayList<>(List.of(EnergyTierPreview::of));

    static void register(Function<GTRecipeDefinition, @Nullable RecipeTierPreview> factory) {
        FACTORIES.add(factory);
    }

    @Nullable
    static RecipeTierPreview create(GTRecipeDefinition recipe) {
        for (var factory : FACTORIES) {
            var preview = factory.apply(recipe);
            if (preview != null) return preview;
        }
        return null;
    }
}
