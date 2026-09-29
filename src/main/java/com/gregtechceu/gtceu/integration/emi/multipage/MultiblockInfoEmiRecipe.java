package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructurePattern;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePreviewWidget;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import org.jetbrains.annotations.Nullable;

public class MultiblockInfoEmiRecipe extends ModularEmiRecipe<WidgetGroup> {

    public final MultiblockMachineDefinition definition;

    public MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition) {
        super(() -> {
            var structure = StructurePattern.of(definition);
            return structure != null ? new StructurePreviewWidget(definition, structure, () -> StructurePreviewTrigger.onShown(definition, structure)) :
                    new WidgetGroup(0, 0, StructurePreviewWidget.WIDTH, StructurePreviewWidget.HEIGHT);
        });
        this.definition = definition;
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
