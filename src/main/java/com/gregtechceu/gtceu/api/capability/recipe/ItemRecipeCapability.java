package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ResearchData;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.SerializerIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.core.mixins.IntersectionIngredientAccessor;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemStackList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemEntryHandler;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemStackHandler;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IntersectionIngredient;
import net.minecraftforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ItemRecipeCapability extends RecipeCapability<Ingredient> {

    public final static ItemRecipeCapability CAP = new ItemRecipeCapability();

    protected ItemRecipeCapability() {
        super("item", 0xFFD96106, true, 0, SerializerIngredient.INSTANCE);
    }

    @Override
    public Ingredient copyInner(Ingredient content) {
        return SizedIngredient.copy(content);
    }

    @Override
    public Ingredient copyWithModifier(Ingredient content, ContentModifier modifier) {
        if (content instanceof SizedIngredient sizedIngredient) {
            return SizedIngredient.create(sizedIngredient.getInner(), modifier.apply(sizedIngredient.getAmount()));
        }
        return SizedIngredient.create(content, modifier.apply(1));
    }

    @Override
    public boolean isRecipeSearchFilter() {
        return true;
    }

    @Override
    public @NotNull List<Object> createXEIContainerContents(List<Content> contents, GTRecipe recipe, IO io) {
        List<Object> entryLists = contents.stream()
                .map(Content::getContent)
                .map(this::of)
                .map(ItemRecipeCapability::mapItem)
                .collect(Collectors.toList());

        if (io == IO.OUT && recipe.recipeType.isScanner()) {
            List<Object> scannerPossibilities = new ObjectArrayList<>();
            // Scanner Output replacing, used for cycling research outputs
            ResearchManager.ResearchItem researchData = null;
            for (Content stack : recipe.getOutputContents(this)) {
                ItemStack[] stacks = this.of(stack.content).getItems();
                if (stacks.length == 0 || stacks[0].isEmpty()) continue;

                researchData = ResearchManager.readResearchId(stacks[0]);
                if (researchData != null) break;
            }
            if (researchData != null) {
                Collection<GTRecipe> possibleRecipes = researchData.recipeType()
                        .getDataStickEntry(researchData.researchId());
                Set<ItemStack> cache = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.ITEM);
                if (possibleRecipes != null) {
                    for (GTRecipe r : possibleRecipes) {
                        var outputs = r.getOutputContents(this);
                        if (outputs.isEmpty()) continue;

                        Content outputContent = outputs.get(0);
                        ItemStack[] stacks = this.of(outputContent.content).getItems();
                        if (stacks.length == 0) continue;

                        ItemStack researchStack = stacks[0];
                        if (!researchStack.isEmpty() && !cache.contains(researchStack)) {
                            cache.add(researchStack);
                            scannerPossibilities.add(ItemStackList.of(researchStack.copyWithCount(1)));
                        }
                    }
                }
                scannerPossibilities.add(entryLists.get(0));
                entryLists = scannerPossibilities;
            }
        }

        while (entryLists.size() < recipe.recipeType.getMaxOutputs(this)) entryLists.add(null);
        return entryLists;
    }

    public Object createXEIContainer(List<?> contents) {
        // cast is safe if you don't pass the wrong thing.
        // noinspection unchecked
        return new CycleItemEntryHandler((List<ItemEntryList>) contents);
    }

    @NotNull
    @Override
    public Widget createWidget() {
        SlotWidget slot = new SlotWidget();
        slot.initTemplate();
        return slot;
    }

    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return SlotWidget.class;
    }

    @Override
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipe recipe,
                                @Nullable Content content,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof SlotWidget slot) {
            if (storage instanceof IItemHandlerModifiable items) {
                if (index >= 0 && index < items.getSlots()) {
                    slot.setHandlerSlot(items, index);
                    slot.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
                    slot.setCanTakeItems(!isXEI);
                    slot.setCanPutItems(!isXEI && io.support(IO.IN));
                }
                // 1 over container size.
                // If in a recipe viewer and a research slot can be added, add it.
                if (isXEI && recipeType.isHasResearchSlot() && index == items.getSlots()) {
                    if (ConfigHolder.INSTANCE.machines.enableResearch) {
                        ResearchCondition condition = recipeHolder.conditions().stream()
                                .filter(ResearchCondition.class::isInstance).findAny()
                                .map(ResearchCondition.class::cast).orElse(null);
                        if (condition != null) {
                            List<ItemStack> dataItems = new ObjectArrayList<>();
                            for (ResearchData.ResearchEntry entry : condition.data) {
                                ItemStack dataStick = entry.getDataItem().copy();
                                ResearchManager.writeResearchToNBT(dataStick.getOrCreateTag(), entry.getResearchId(),
                                        recipeType);
                                dataItems.add(dataStick);
                            }
                            CycleItemStackHandler handler = new CycleItemStackHandler(List.of(dataItems));
                            slot.setHandlerSlot(handler, 0);
                            slot.setIngredientIO(IngredientIO.CATALYST);
                            slot.setCanTakeItems(false);
                            slot.setCanPutItems(false);
                        }
                    }
                }
            }
            if (content != null) {
                float chance = (float) recipeType.getChanceFunction()
                        .getBoostedChance(content, recipeTier, chanceTier) / Content.MAX_CHANCE;
                slot.setXEIChance(chance);
                slot.setOnAddedTooltips((w, tooltips) -> {
                    GTRecipeWidget.setConsumedChance(content,
                            recipe.getChanceLogicForCapability(this, io, isTickSlot(index, io, recipe)),
                            tooltips, recipeTier, chanceTier, recipeType.getChanceFunction());
                    if (isTickSlot(index, io, recipe)) {
                        tooltips.add(Component.translatable("gtceu.gui.content.per_tick"));
                    }
                });
                if (io == IO.IN && (content.chance == 0 || this.of(content.content) instanceof IntCircuitIngredient)) {
                    slot.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    // Maps ingredients to an ItemEntryList for XEI: either an ItemTagList or an ItemStackList
    private static ItemEntryList mapItem(final Ingredient ingredient) {
        if (ingredient instanceof SizedIngredient sizedIngredient) {
            final int amount = sizedIngredient.getAmount();
            var mapped = tryMapInner(sizedIngredient.getInner(), amount);
            if (mapped != null) return mapped;
        }
        if (ingredient instanceof IntersectionIngredient intersection) {
            return mapIntersection(intersection, -1);
        } else {
            var tagList = tryMapTag(ingredient, 1);
            if (tagList != null) return tagList;
        }
        return new ItemStackList(ingredient.getItems());
    }

    private static @Nullable ItemEntryList tryMapInner(final Ingredient inner, int amount) {
        if (inner instanceof IntersectionIngredient intersection) return mapIntersection(intersection, amount);
        return tryMapTag(inner, amount);
    }

    // Map intersection ingredients to the items inside, as recipe viewers don't support them.
    private static ItemEntryList mapIntersection(final IntersectionIngredient intersection, int amount) {
        List<Ingredient> children = ((IntersectionIngredientAccessor) intersection).getChildren();
        if (children.isEmpty()) return new ItemStackList();

        var childList = mapItem(children.get(0));
        ItemStackList stackList = new ItemStackList();
        for (var stack : childList.getStacks()) {
            if (children.stream().skip(1).allMatch(child -> child.test(stack))) {
                if (amount > 0) stackList.add(stack.copyWithCount(amount));
                else stackList.add(stack.copy());
            }
        }
        return stackList;
    }

    private static @Nullable ItemTagList tryMapTag(final Ingredient ingredient, int amount) {
        var values = ingredient.values;
        if (values.length > 0 && values[0] instanceof Ingredient.TagValue tagValue) {
            return ItemTagList.of(tagValue.tag, amount, null);
        }
        return null;
    }
}
