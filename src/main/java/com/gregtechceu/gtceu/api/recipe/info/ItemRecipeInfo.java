package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemStackList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemEntryHandler;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.util.ItemStackHashStrategy;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ItemRecipeInfo extends ContentRecipeInfo {

    public final static ItemRecipeInfo INSTANCE = new ItemRecipeInfo();

    private ItemRecipeInfo() {
        super("item", 0xFFD96106, true, 0);
    }

    @Override
    public @NotNull List<Object> createXEIContainerContents(ContentList contents, GTRecipeDefinition recipe, IO io) {
        List<Object> entryLists = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            entryLists.add(mapItem(contents, i));
        }

        if (io == IO.OUT && recipe.recipeType.isScanner()) {
            List<Object> scannerPossibilities = new ArrayList<>();
            // Scanner Output replacing, used for cycling research outputs
            ResearchManager.ResearchItem researchData = null;
            var outputs = recipe.itemOutputs;
            for (int i = 0; i < outputs.size(); i++) {
                if (!(outputs.ingredient(i).displayKey() instanceof AEItemKey key)) continue;
                researchData = ResearchManager.readResearchId(key.getReadOnlyStack());
                if (researchData != null) break;
            }
            if (researchData != null) {
                Collection<GTRecipeDefinition> possibleRecipes = researchData.recipeType()
                        .getDataStickEntry(researchData.researchId());
                var cache = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.ITEM);
                if (possibleRecipes != null) {
                    for (GTRecipeDefinition r : possibleRecipes) {
                        var rOutputs = r.itemOutputs;
                        if (rOutputs.isEmpty()) continue;
                        if (!(rOutputs.ingredient(0).displayKey() instanceof AEItemKey key)) continue;
                        var stack = key.getReadOnlyStack();
                        if (!cache.contains(stack)) {
                            cache.add(stack);
                            scannerPossibilities.add(ItemStackList.of(stack.copyWithCount(1)));
                        }
                    }
                }
                scannerPossibilities.add(entryLists.getFirst());
                entryLists = scannerPossibilities;
            }
        }

        while (entryLists.size() < recipe.recipeType.getMaxOutputs(this)) entryLists.add(null);
        return entryLists;
    }

    @SuppressWarnings("unchecked")
    public Object createXEIContainer(List<?> contents) {
        return new CycleItemEntryHandler((List<ItemEntryList>) contents);
    }

    @NotNull
    @Override
    public Widget createWidget() {
        return ItemSlot.unbound();
    }

    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return SlotWidget.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipeDefinition recipe,
                                @Nullable ContentList contents,
                                int contentIndex,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof SlotWidget slot) {
            int slots = storage instanceof KeyInventory<?> inv ? inv.size() : storage instanceof StackInventory stacks ? stacks.size : storage instanceof IItemHandlerModifiable handler ? handler.getSlots() : -1;
            if (index >= 0 && index < slots) {
                if (storage instanceof KeyInventory<?> inv) {
                    slot.setHandlerSlot((KeyInventory<AEItemKey>) inv, index);
                } else if (storage instanceof StackInventory stacks) {
                    slot.setHandlerSlot(stacks, index);
                } else {
                    slot.setHandlerSlot((IItemHandlerModifiable) storage, index);
                }
                slot.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
                slot.setCanTakeItems(!isXEI);
                slot.setCanPutItems(!isXEI && io.support(IO.IN));
            }
            if (contents != null && contentIndex >= 0 && contentIndex < contents.size()) {
                int chance = contents.chance(contentIndex);
                int boost = contents.boost(contentIndex);
                slot.setXEIChance((float) recipe.chanceFunction.getBoostedChance(chance, boost, recipeTier, chanceTier) / ContentList.MAX_CHANCE);
                slot.setOnAddedTooltips((w, tooltips) -> GTRecipeWidget.setConsumedChance(chance, boost, tooltips, recipeTier, chanceTier, recipe.chanceFunction));
                if (io == IO.IN && chance == 0) {
                    slot.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    public static ItemEntryList mapItem(ContentList contents, int i) {
        var ing = contents.ingredient(i);
        int amount = Keys.saturatedInt(contents.amount(i));
        if (ing.kind() == KeyIngredient.TAG && ing.tagKey() != null) {
            return ItemTagList.of(ing.itemTagKey(), amount, null);
        }
        var items = ing.getItems();
        var stacks = new ItemStack[items.length];
        for (int j = 0; j < items.length; j++) stacks[j] = items[j].copyWithCount(amount);
        return new ItemStackList(stacks);
    }
}
