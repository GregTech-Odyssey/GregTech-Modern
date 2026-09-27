package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.lowdragmc.lowdraglib.emi.IGui2Renderable;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;

public class GTRecipeEMICategory extends EmiRecipeCategory {

    public static final Function<GTRecipeCategory, GTRecipeEMICategory> CATEGORIES = Util
            .memoize(GTRecipeEMICategory::new);
    private static final Comparator<MachineDefinition> WORKSTATION_ORDER = Comparator
            .comparing((MachineDefinition machine) -> machine instanceof MultiblockMachineDefinition)
            .thenComparingInt(MachineDefinition::getTier);
    private final GTRecipeCategory category;

    private GTRecipeEMICategory(GTRecipeCategory category) {
        super(category.registryKey, IGui2Renderable.toDrawable(category.getIcon(), 16, 16));
        this.category = category;
    }

    public static void registerDisplays(EmiRegistry registry) {
        GTRegistries.RECIPE_CATEGORIES.forEachKeyValue((id, category) -> {
            if (!category.shouldRegisterDisplays()) return;
            var type = category.getRecipeType();
            if (category == type.getCategory()) type.buildRepresentativeRecipes();
            EmiRecipeCategory emiCategory = CATEGORIES.apply(category);
            type.getRecipesInCategory(category).stream()
                    .map(recipe -> new GTEmiRecipe(recipe, emiCategory))
                    .forEach(registry::addRecipe);
        });
    }

    public static void registerWorkStations(EmiRegistry registry) {
        var machines = new ArrayList<MachineDefinition>();
        GTRegistries.MACHINES.forEachKeyValue((id, machine) -> machines.add(machine));
        machines.sort(WORKSTATION_ORDER);
        for (MachineDefinition machine : machines) {
            if (machine.getRecipeTypes() == null) continue;
            for (GTRecipeType type : machine.getRecipeTypes()) {
                if (type == null) continue;
                for (GTRecipeCategory category : type.getCategories()) {
                    if (!category.isXEIVisible() && !GTCEu.isDev()) continue;
                    registry.addWorkstation(machineCategory(category), new FrontLitEmiStack(machine.asStack()));
                }
            }
        }
    }

    public static EmiRecipeCategory machineCategory(GTRecipeCategory category) {
        if (category == GTRecipeTypes.FURNACE_RECIPES.getCategory()) return VanillaEmiRecipeCategories.SMELTING;
        else return CATEGORIES.apply(category);
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta) {
        MachineRenderer.frontLitGui = true;
        try {
            super.render(draw, x, y, delta);
        } finally {
            MachineRenderer.frontLitGui = false;
        }
    }

    @Override
    public Component getName() {
        return Component.translatable(category.getLanguageKey());
    }
}
