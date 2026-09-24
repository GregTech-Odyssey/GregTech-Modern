package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.core.mixins.IntersectionIngredientAccessor;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IntersectionIngredient;

import com.gto.datasynclib.util.ItemStackHashStrategy;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 物品内容种类的元信息与渲染实现。
 *
 * <p>
 * 负责把配方里的 {@link ItemIngredient} 转成配方查看器能显示的条目（单个物品堆、标签列表，
 * 或交叉配方的多个可选结果），并把这些条目绑定到普通物品槽控件上。扫描仪的产物还做了特殊处理：
 * 会把扫描可能得到的全部研究产物列成可循环切换的候选。
 */
public final class ItemRecipeInfo extends ContentRecipeInfo<ItemStack, ItemIngredient> {

    /** 全局唯一实例，注册名为 {@code item}。 */
    public final static ItemRecipeInfo INSTANCE = new ItemRecipeInfo();

    private ItemRecipeInfo() {
        super("item", 0xFFD96106, true, 0);
    }

    /**
     * 把物品内容转成查看器条目。
     *
     * <p>
     * 扫描仪的<b>输出</b>会被替换成「本次扫描可能得到的全部研究产物」的循环列表，
     * 这样在配方查看器里逐个切换就能看到所有可能结果。
     */
    @Override
    public @NotNull List<Object> createXEIContainerContents(List<Content<ItemIngredient>> contents, GTRecipeDefinition recipe, IO io) {
        List<Object> entryLists = contents.stream()
                .map(ItemRecipeInfo::mapItem)
                .collect(Collectors.toList());

        if (io == IO.OUT && recipe.recipeType.isScanner()) {
            List<Object> scannerPossibilities = new ArrayList<>();
            // Scanner Output replacing, used for cycling research outputs
            ResearchManager.ResearchItem researchData = null;
            for (Content<ItemIngredient> stack : recipe.itemOutputs) {
                ItemStack stacks = stack.inner.getInnerItemStack();
                if (stacks.isEmpty()) continue;

                researchData = ResearchManager.readResearchId(stacks);
                if (researchData != null) break;
            }
            if (researchData != null) {
                Collection<GTRecipeDefinition> possibleRecipes = researchData.recipeType()
                        .getDataStickEntry(researchData.researchId());
                var cache = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.ITEM);
                if (possibleRecipes != null) {
                    for (GTRecipeDefinition r : possibleRecipes) {
                        var outputs = r.itemOutputs;
                        if (outputs.isEmpty()) continue;

                        var outputContent = outputs.getFirst();
                        var stack = outputContent.inner.getInnerItemStack();
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

    /** 把条目列表包成可循环切换的物品显示 handler。 */
    @SuppressWarnings("unchecked") // cast is safe if you don't pass the wrong thing.
    public Object createXEIContainer(List<?> contents) {
        return new CycleItemEntryHandler((List<ItemEntryList>) contents);
    }

    /** 标准物品槽（uipro {@link ItemSlot}），绑定前是空槽。 */
    @NotNull
    @Override
    public Widget createWidget() {
        return ItemSlot.unbound();
    }

    /** 该种类使用 {@link SlotWidget} 显示。 */
    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return SlotWidget.class;
    }

    /**
     * 把槽位信息应用到物品槽上：绑定存储槽位（若有）、设置输入 / 输出类型与是否可交互，
     * 并填充产出概率与提示文本。{@code storage} 为 null 时只刷新内容信息（配方页切换电压档时）。
     * 配方所需的研究数据不再占用槽位区，由研究条件作为额外展示槽加在配方页信息区。
     */
    @Override
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipeDefinition recipe,
                                @Nullable Content<ItemIngredient> content,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof SlotWidget slot) {
            if (storage instanceof ICustomItemStackHandler items) {
                if (index >= 0 && index < items.getSlots()) {
                    slot.setHandlerSlot(items, index);
                    slot.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
                    slot.setCanTakeItems(!isXEI);
                    slot.setCanPutItems(!isXEI && io.support(IO.IN));
                }
            }
            if (content != null) {
                float chance = (float) recipe.chanceFunction
                        .getBoostedChance(content, recipeTier, chanceTier) / Content.MAX_CHANCE;
                slot.setXEIChance(chance);
                slot.setOnAddedTooltips((w, tooltips) -> {
                    GTRecipeWidget.setConsumedChance(content,
                            ChanceLogic.OR,
                            tooltips, recipeTier, chanceTier, recipe.chanceFunction);
                });
                if (io == IO.IN && content.chance == 0) {
                    slot.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    /** 把单条物品内容映射成查看器条目。 */
    private static ItemEntryList mapItem(final Content<ItemIngredient> ingredient) {
        return tryMapInner(ingredient.inner.inner, ingredient.inner.getAmount());
    }

    /**
     * 按优先级尝试映射：交叉配方 → 标签 → 普通物品堆列表。
     *
     * <p>
     * 若某个 {@link ItemIngredient} 实际是交叉（intersection）配方，则先降级成它内部的子配方再映射。
     */
    private static ItemEntryList tryMapInner(final Ingredient ingredient, int amount) {
        if (ingredient instanceof IntersectionIngredient intersection) return mapIntersection(intersection, amount);
        var tagList = tryMapTag(ingredient, amount);
        if (tagList != null) return tagList;
        return new ItemStackList(Arrays.stream(ingredient.getItems()).map(stack -> stack.copyWithCount(amount)).toArray(ItemStack[]::new));
    }

    /**
     * 把交叉配方展开成「同时满足所有子配方」的物品集合。
     *
     * <p>
     * 配方查看器不支持交叉配方，所以这里取第一个子配方的物品，再逐个用其余子配方过滤。
     */
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

    /**
     * 若该配方的第一个候选是标签，则映射成标签列表，否则返回 {@code null} 交给上层按物品堆处理。
     */
    private static ItemTagList tryMapTag(final Ingredient ingredient, int amount) {
        var values = ingredient.values;
        if (values.length > 0 && values[0] instanceof Ingredient.TagValue tagValue) {
            return ItemTagList.of(tagValue.tag, amount, null);
        }
        return null;
    }
}
