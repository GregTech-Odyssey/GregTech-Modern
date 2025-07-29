package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MultiblockInfoEmiRecipe extends ModularEmiRecipe<WidgetGroup> {

    public final MultiblockMachineDefinition definition;

    public MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition) {
        super(() -> PatternPreviewWidget.getPatternWidget(definition));
        this.definition = definition;
        this.inputs = getParts(definition.getPatternFactory().get());
    }

    private List<EmiIngredient> getParts(BlockPattern pattern) {
        HashSet<TraceabilityPredicate> predicateMap = new HashSet<>();

        for (var layer : pattern.blockMatches) {
            for (var aisle : layer) {
                predicateMap.addAll(Arrays.asList(aisle));
            }
        }

        List<List<ItemStack>> parts = new ArrayList<>();
        for (var predicate : predicateMap) {
            if (predicate == null) continue;
            List<SimplePredicate> predicates = new ArrayList<>();
            predicates.addAll(predicate.common);
            predicates.addAll(predicate.limited);
            predicates.removeIf(p -> p == null || p.candidates == null);
            for (SimplePredicate simplePredicate : predicates) {
                List<ItemStack> itemStacks = simplePredicate.getCandidates();
                if (!itemStacks.isEmpty()) {
                    parts.add(simplePredicate.getCandidates());
                }
            }
        }

        return parts.stream().map(p -> EmiIngredient.of(Ingredient.of(p.stream()))).toList();
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return MultiblockInfoEmiCategory.CATEGORY;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return definition.getId();
    }
}
