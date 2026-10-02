package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiRecipe;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderPanel;
import com.gregtechceu.gtceu.uiwidgets.structure.StructureBuildFlow;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePlans;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePreviewScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.RecipeScreen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Ae2PatternTerminalHandler<T extends PatternEncodingTermMenu> implements EmiRecipeHandler<T> {

    private List<Slot> getInputSources(T handler) {
        return handler.slots;
    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        return new EmiPlayerInventory(
                getInputSources(screen.getMenu()).stream().map(Slot::getItem).map(EmiStack::of).toList());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof GTEmiRecipe || recipe instanceof MultiblockInfoEmiRecipe;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return true;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        T menu = context.getScreenHandler();
        if (!(recipe instanceof MultiblockInfoEmiRecipe multiblock) || !openPatternBuilder(menu, multiblock, context.getScreen())) {
            EncodingHelper.encodeProcessingRecipe(menu,
                    ofInputs(recipe),
                    ofOutputs(recipe));
        }
        if (Minecraft.getInstance().screen instanceof RecipeScreen e) {
            e.onClose();
        }
        return true;
    }

    private static boolean openPatternBuilder(PatternEncodingTermMenu menu, MultiblockInfoEmiRecipe recipe, Screen terminal) {
        var structure = recipe.definition.displayStructure();
        if (structure == null) return false;
        StructurePreviewScreen.open(recipe.definition, structure, previous -> () -> Minecraft.getInstance().setScreen(terminal),
                encodeAction(menu, recipe.definition, terminal));
        return true;
    }

    public static StructurePreviewScreen.Action encodeAction(PatternEncodingTermMenu menu, MultiblockMachineDefinition definition, Screen terminal) {
        var outputs = Collections.singletonList(new GenericStack(AEItemKey.of(definition.asStack()), 1));
        return encodeAction(menu, menu.getClientRepo(), menu.getProcessingInputSlots().length, definition, terminal,
                inputs -> EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs));
    }

    public static StructurePreviewScreen.Action encodeAction(AbstractContainerMenu menu, @Nullable IClientRepo repo, int inputLimit, MultiblockMachineDefinition definition,
                                                             Screen terminal, Consumer<List<List<GenericStack>>> encode) {
        var icon = definition.asStack();
        var target = terminal instanceof RecipeScreen recipes && recipes.old != null ? recipes.old : terminal;
        return StructureBuildFlow.action(StructurePreviewScreen.ENCODE, (layout, values, navigator, preview) -> {
            var model = Ae2PatternBuilder.model(menu, repo, StructurePlans.modelBuilder(icon, layout, true));
            model.selectMinimum();
            return new PatternBuilderPanel(model, AEItems.BLANK_PATTERN.stack(), icon.getHoverName(), inputLimit, navigator.maxHeight(), () -> {
                var minecraft = Minecraft.getInstance();
                if (minecraft.player != null && minecraft.player.containerMenu == menu) encode.accept(Ae2PatternBuilder.inputs(model));
                minecraft.setScreen(target);
            }, navigator::close, new PatternBuilderPanel.Footer(PatternBuilderPanel.TITLE, PatternBuilderPanel.WRITE, PatternBuilderPanel.INCLUDE,
                    PatternBuilderPanel.CANNOT_WRITE, true, false, navigator::close));
        });
    }

    public static List<List<GenericStack>> ofInputs(EmiRecipe emiRecipe) {
        return emiRecipe.getInputs()
                .stream()
                .map(Ae2PatternTerminalHandler::intoGenericStack)
                .toList();
    }

    public static List<GenericStack> ofOutputs(EmiRecipe emiRecipe) {
        return emiRecipe.getOutputs()
                .stream()
                .flatMap(slot -> intoGenericStack(slot).stream().limit(1))
                .toList();
    }

    private static List<GenericStack> intoGenericStack(EmiIngredient ingredient) {
        if (ingredient.isEmpty()) {
            return new ArrayList<>();
        }
        return ingredient.getEmiStacks().stream().map(stack -> fromEmiStack(stack, ingredient.getAmount())).toList();
    }

    private static GenericStack fromEmiStack(EmiStack stack, long amount) {
        if (stack.getKey() instanceof Item item) {
            return new GenericStack(AEItemKey.of(item.getDefaultInstance()), amount);
        } else if (stack.getKey() instanceof Fluid fluid) {
            return new GenericStack(AEFluidKey.of(fluid), amount);
        }
        return new GenericStack(AEItemKey.of(ItemStack.EMPTY), 0);
    }
}
