package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePreviewWidget;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.WidgetHolder;
import org.jetbrains.annotations.Nullable;

public class MultiblockInfoEmiRecipe extends ModularEmiRecipe<WidgetGroup> {

    public final MultiblockMachineDefinition definition;

    public MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition) {
        super(() -> new WidgetGroup(0, 0, StructurePreviewWidget.WIDTH, StructurePreviewWidget.HEIGHT));
        this.definition = definition;
        widget = this::createWidget;
    }

    private WidgetGroup createWidget() {
        var structure = definition.displayStructure();
        if (structure == null) return new WidgetGroup(0, 0, StructurePreviewWidget.WIDTH, StructurePreviewWidget.HEIGHT);
        return new StructurePreviewWidget(definition, structure, StructurePreviewWidget.WIDTH, StructurePreviewWidget.HEIGHT,
                () -> StructurePreviewTrigger.open(definition, structure), MultiblockEmiActions.of(this, definition));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        super.addWidgets(widgets);
        MultiblockEmiActions.blockFavoriteKey(widgets);
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
