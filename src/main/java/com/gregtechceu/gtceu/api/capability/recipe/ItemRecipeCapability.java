package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.SerializerItemIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
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

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IntersectionIngredient;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.fast.recipesearch.IntLongMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.*;
import java.util.stream.Collectors;

public class ItemRecipeCapability extends ContentRecipeCapability<ItemIngredient> {

    public final static ItemRecipeCapability CAP = new ItemRecipeCapability();

    protected ItemRecipeCapability() {
        super("item", 0xFFD96106, true, 0, SerializerItemIngredient.INSTANCE);
    }

    @Override
    public void convert(ItemIngredient ingredient, IntLongMap map) {
        if (ingredient instanceof IntCircuitIngredient circuitIngredient) {
            map.add(circuitIngredient.configuration, 1);
        } else if (ingredient.inner.isVanilla() && ingredient.inner.values.length == 1) {
            if (ingredient.inner.values[0] instanceof Ingredient.ItemValue itemValue) {
                map.add(itemValue.item.getItem().hashCode(), ingredient.amount);
            } else if (ingredient.inner.values[0] instanceof Ingredient.TagValue tagValue) {
                map.add(tagValue.tag.hashCode(), ingredient.amount);
            }
        }
    }

    @Override
    public @NotNull List<Object> createXEIContainerContents(List<Content> contents, GTRecipeDefinition recipe, IO io) {
        List<Object> entryLists = contents.stream()

                .map(this::of)
                .map(ItemRecipeCapability::mapItem)
                .collect(Collectors.toList());

        if (io == IO.OUT && recipe.recipeType.isScanner()) {
            List<Object> scannerPossibilities = new ArrayList<>();
            // Scanner Output replacing, used for cycling research outputs
            ResearchManager.ResearchItem researchData = null;
            for (Content stack : recipe.getOutputContents(this)) {
                ItemStack stacks = this.of(stack).getInnerItemStack();
                if (stacks.isEmpty()) continue;

                researchData = ResearchManager.readResearchId(stacks);
                if (researchData != null) break;
            }
            if (researchData != null) {
                Collection<GTRecipeDefinition> possibleRecipes = researchData.recipeType()
                        .getDataStickEntry(researchData.researchId());
                Set<ItemStack> cache = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.ITEM);
                if (possibleRecipes != null) {
                    for (GTRecipeDefinition r : possibleRecipes) {
                        var outputs = r.getOutputContents(this);
                        if (outputs.isEmpty()) continue;

                        Content outputContent = outputs.getFirst();
                        var stack = this.of(outputContent).getInnerItemStack();
                        if (stack.isEmpty()) continue;
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
                                @UnknownNullability("null when content == null") GTRecipeDefinition recipe,
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
                            CycleItemStackHandler handler = new CycleItemStackHandler(Collections.singletonList(Collections.singletonList(condition.dataStack)));
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
                            recipe.getChanceLogicForCapability(this, io),
                            tooltips, recipeTier, chanceTier, recipeType.getChanceFunction());
                });
                if (io == IO.IN && content.chance == 0) {
                    slot.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    // Maps ingredients to an ItemEntryList for XEI: either an ItemTagList or an ItemStackList
    private static ItemEntryList mapItem(final ItemIngredient ingredient) {
        return tryMapInner(ingredient.inner, ingredient.getAmount());
    }

    private static ItemEntryList tryMapInner(final Ingredient ingredient, int amount) {
        if (ingredient instanceof IntersectionIngredient intersection) return mapIntersection(intersection, amount);
        var tagList = tryMapTag(ingredient, amount);
        if (tagList != null) return tagList;
        return new ItemStackList(Arrays.stream(ingredient.getItems()).map(stack -> stack.copyWithCount(amount)).toArray(ItemStack[]::new));
    }

    // Map intersection ingredients to the items inside, as recipe viewers don't support them.
    private static ItemEntryList mapIntersection(final IntersectionIngredient intersection, int amount) {
        List<Ingredient> children = ((IntersectionIngredientAccessor) intersection).getChildren();
        if (children.isEmpty()) return new ItemStackList();

        var childList = tryMapInner(children.getFirst(), amount);
        ItemStackList stackList = new ItemStackList();
        for (var stack : childList.getStacks()) {
            if (children.stream().skip(1).allMatch(child -> child.test(stack))) {
                if (amount > 0) stackList.add(stack.copyWithCount(amount));
                else stackList.add(stack.copy());
            }
        }
        return stackList;
    }

    private static ItemTagList tryMapTag(final Ingredient ingredient, int amount) {
        var values = ingredient.values;
        if (values.length > 0 && values[0] instanceof Ingredient.TagValue tagValue) {
            return ItemTagList.of(tagValue.tag, amount, null);
        }
        return null;
    }
}
