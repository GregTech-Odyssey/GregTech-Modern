package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
final class MultiblockTreeRecipe implements EmiRecipe {

    private static final int COLUMNS = 6;
    private static final int SLOT = 18;
    private static final int ARROW_GAP = 4;
    private static final int ARROW_WIDTH = 24;
    private static final int OUTPUT = 26;
    private static final long BUCKET = 1000;
    private static final ItemStack TERMINAL = GTItems.TERMINAL.asStack();
    private static final EmiRenderable TERMINAL_ICON = (graphics, x, y, delta) -> graphics.renderItem(TERMINAL, x, y);
    private static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(GTCEu.id("multiblock_tree"), TERMINAL_ICON) {

        @Override
        public Component getName() {
            return Component.translatable(MultiblockEmiActions.TREE_CATEGORY);
        }
    };

    private final ResourceLocation id;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    MultiblockTreeRecipe(ResourceLocation id, List<PatternBuilderModel.Input> inputs, EmiStack output) {
        this.id = id;
        this.inputs = new ArrayList<>(inputs.size());
        for (var input : inputs) {
            var stack = input.stack();
            if (stack.getItem() instanceof BucketItem bucket && bucket.getFluid() != Fluids.EMPTY) {
                this.inputs.add(EmiStack.of(bucket.getFluid(), input.count() * BUCKET));
            } else {
                this.inputs.add(EmiStack.of(stack, input.count()));
            }
        }
        this.outputs = Collections.singletonList(output.copy().setAmount(1));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    private int rows() {
        return Math.max(1, (inputs.size() + COLUMNS - 1) / COLUMNS);
    }

    @Override
    public int getDisplayWidth() {
        return COLUMNS * SLOT + ARROW_GAP * 2 + ARROW_WIDTH + OUTPUT;
    }

    @Override
    public int getDisplayHeight() {
        return Math.max(rows() * SLOT, OUTPUT);
    }

    @Override
    public boolean supportsRecipeTree() {
        return true;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            widgets.addSlot(inputs.get(i), i % COLUMNS * SLOT, i / COLUMNS * SLOT);
        }
        int height = getDisplayHeight();
        int arrowX = COLUMNS * SLOT + ARROW_GAP;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, arrowX, (height - 17) / 2);
        widgets.addSlot(outputs.get(0), arrowX + ARROW_WIDTH + ARROW_GAP, (height - OUTPUT) / 2).large(true).recipeContext(this);
    }
}
