package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.emi.ModularForegroundRenderWidget;
import com.lowdragmc.lowdraglib.emi.ModularWrapperWidget;
import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;

import com.gto.datasynclib.util.ItemStackHashStrategy;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TankWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.IntSupplier;

/**
 * GT 配方在 EMI 里的一页。启动时要为全部配方建对象，所以构造时不建界面、也不算尺寸：
 * 配方页（{@link GTRecipeWidget}）在显示时才建，尺寸在 EMI 第一次排版这个类别时按配方算一次
 * （{@link GTRecipeWidget#getPageSize}，不建控件）。
 * <p>
 * 其余场合（收藏栏悬停预览、截图、生产规划图……）没有右侧按钮、也不分页，{@link #getDisplayWidth}/{@link #getDisplayHeight}
 * 报按内容的紧凑尺寸，页面不挖缺口、不画卡片，保留 EMI 的底框。
 * <p>
 * 右侧按钮数取决于当前打开的界面（能否"填充配方"）和 EMI 设置（可在游戏里改），每次排版重新数，尺寸按按钮数分别缓存。
 */
public class GTEmiRecipe extends ModularEmiRecipe<Widget> implements EmiPageLayout.Paged {

    /// 交给父类构造器的占位控件：父类构造时只读它的尺寸，真正的尺寸见 getDisplayWidth / getDisplayHeight
    private static final Widget PLACEHOLDER = new Widget(0, 0, 0, 0);

    private final EmiRecipeCategory category;
    protected final GTRecipeDefinition recipe;
    public final IntSupplier displayPriority;
    private final EmiPageSizes sizes;
    /// 正在建的页面的外框（addWidgets 里设好再建页）
    protected GTRecipeWidget.PageFrame frame = GTRecipeWidget.PageFrame.COMPACT;
    private List<EmiIngredient> encodingCatalysts;
    private volatile boolean ingredientsReady;

    public GTEmiRecipe(GTRecipeDefinition recipe, EmiRecipeCategory category) {
        super(() -> PLACEHOLDER);
        this.recipe = recipe;
        this.category = category;
        this.displayPriority = () -> recipe.priority;
        this.inputs = null;
        this.widget = () -> new GTRecipeWidget(recipe, frame);
        this.sizes = new EmiPageSizes(this::measure, () -> EmiPageLayout.recipeAreaHeight(category));
    }

    /** 按外框量页面尺寸（不建控件）。一个 EMI 配方显示多个配方方案时取最大的。 */
    protected Size measure(GTRecipeWidget.PageFrame frame) {
        return GTRecipeWidget.getPageSize(recipe, frame);
    }

    @Override
    public int getPagedWidth() {
        return sizes.getPagedWidth(this);
    }

    @Override
    public int getPagedHeight() {
        return sizes.getPagedHeight();
    }

    @Override
    public int getDisplayWidth() {
        return sizes.getCompactSize().width;
    }

    @Override
    public int getDisplayHeight() {
        return sizes.getCompactSize().height;
    }

    public int getTier() {
        return recipe.tier;
    }

    public GTRecipeType getRecipeType() {
        return recipe.recipeType;
    }

    @Override
    public List<Widget> getFlatWidgetCollection(Widget widget) {
        return Collections.emptyList();
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return recipe.id;
    }

    /** {@code widget} 是否位于带裁剪的滚动区里（沿父链找，不只看直接父控件）。 */
    public static boolean isInsideScroller(Widget widget) {
        return ScrolledSlotWidget.findScroller(widget) != null;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        frame = frameFor(widgets);
        var widget = this.widget.get();
        var modular = new ModularWrapper<>(widget);
        modular.setRecipeWidget(0, 0);

        synchronized (CACHE_OPENED) {
            CACHE_OPENED.add(modular);
        }
        List<Widget> widgetList = new ArrayList<>();
        if (widget instanceof WidgetGroup group) {
            for (Widget w : group.widgets) {
                widgetList.add(w);
                if (w instanceof WidgetGroup group1) {
                    widgetList.addAll(group1.getContainedWidgets(true));
                }
            }
        } else {
            widgetList.add(widget);
        }
        List<dev.emi.emi.api.widget.Widget> slots = new ArrayList<>();
        for (com.lowdragmc.lowdraglib.gui.widget.Widget w : widgetList) {
            if (w instanceof IRecipeIngredientSlot slot) {
                var scroller = ScrolledSlotWidget.findScroller(w);
                var io = slot.getIngredientIO();
                if (io != null && io != IngredientIO.RENDER_ONLY) {
                    // noinspection unchecked
                    var ingredients = EmiIngredient
                            .of((List<? extends EmiIngredient>) (List<?>) slot.getXEIIngredients());
                    ingredients = resolveSlotIngredient(slot, ingredients);

                    SlotWidget slotWidget = null;
                    // Clear the LDLib slots & add EMI slots based on them.
                    if (slot instanceof com.gregtechceu.gtceu.api.gui.widget.SlotWidget slotW) {
                        slotW.setHandlerSlot(ICustomItemStackHandler.EMPTY, 0);
                        slotW.setDrawHoverOverlay(false).setDrawHoverTips(false);
                    } else if (slot instanceof com.gregtechceu.gtceu.api.gui.widget.TankWidget tankW) {
                        tankW.setFluidTank(EmptyFluidHandler.INSTANCE);
                        tankW.setDrawHoverOverlay(false).setDrawHoverTips(false);
                        if (scroller == null) {
                            long capacity = getTankCapacity(slot, ingredients);
                            slotWidget = new TankWidget(ingredients, w.getPosition().x, w.getPosition().y,
                                    w.getSize().width, w.getSize().height, capacity);
                        }
                    }
                    if (scroller != null) {
                        slotWidget = new ScrolledSlotWidget(ingredients, w, scroller, modular);
                    } else if (slotWidget == null) {
                        slotWidget = createItemSlot(ingredients, w.getPosition().x, w.getPosition().y);
                    }

                    slotWidget
                            .customBackground(null, w.getPosition().x, w.getPosition().y, w.getSize().width,
                                    w.getSize().height)
                            .drawBack(false);
                    if (io == IngredientIO.CATALYST) {
                        slotWidget.catalyst(true);
                    } else if (io == IngredientIO.OUTPUT) {
                        slotWidget.recipeContext(this);
                    }
                    for (Component component : w.getTooltipTexts()) {
                        slotWidget.appendTooltip(component);
                    }
                    decorateSlot(slotWidget, ingredients);
                    slots.add(slotWidget);
                }
            }
        }
        widgets.add(new ModularWrapperWidget(modular, slots));
        slots.forEach(widgets::add);
        widgets.add(new ModularForegroundRenderWidget(modular));
    }

    protected GTRecipeWidget.PageFrame frameFor(WidgetHolder widgets) {
        return sizes.frameFor(widgets);
    }

    protected SlotWidget createItemSlot(EmiIngredient ingredient, int x, int y) {
        return new SlotWidget(ingredient, x, y);
    }

    public boolean supportsTransfer() {
        return true;
    }

    protected EmiIngredient resolveSlotIngredient(IRecipeIngredientSlot slot, EmiIngredient ingredient) {
        return ingredient;
    }

    protected void decorateSlot(SlotWidget slotWidget, EmiIngredient ingredient) {}

    public GTRecipeDefinition getEncodingRecipe() {
        return recipe;
    }

    public List<EmiIngredient> getEncodingInputs() {
        return getInputs();
    }

    public List<EmiIngredient> getEncodingCatalysts() {
        ensureIngredients();
        return encodingCatalysts;
    }

    protected long getTankCapacity(IRecipeIngredientSlot slot, EmiIngredient ingredient) {
        return Math.max(1, ingredient.getAmount());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        ensureIngredients();
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        ensureIngredients();
        return outputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        ensureIngredients();
        return catalysts;
    }

    private void ensureIngredients() {
        if (ingredientsReady) return;
        synchronized (this) {
            if (ingredientsReady) return;
            if (inputs == null) {
                var newInputs = new ArrayList<EmiIngredient>();
                var newOutputs = new ArrayList<EmiStack>();
                var newCatalysts = new ArrayList<EmiIngredient>();
                try {
                    collectIngredients(newInputs, newOutputs, newCatalysts);
                } catch (RuntimeException e) {
                    GTCEu.LOGGER.error("Failed to collect EMI ingredients of recipe {}", recipe.id, e);
                    newInputs.clear();
                    newOutputs.clear();
                    newCatalysts.clear();
                }
                encodingCatalysts = List.copyOf(newCatalysts);
                try {
                    collectDisplayIngredients(newOutputs, newCatalysts);
                } catch (RuntimeException e) {
                    GTCEu.LOGGER.error("Failed to collect EMI display slots of recipe {}", recipe.id, e);
                }
                outputs = newOutputs;
                catalysts = newCatalysts;
                inputs = newInputs;
            } else if (encodingCatalysts == null) {
                encodingCatalysts = catalysts;
            }
            ingredientsReady = true;
        }
    }

    protected void collectIngredients(List<EmiIngredient> inputs, List<EmiStack> outputs, List<EmiIngredient> catalysts) {
        for (var content : recipe.itemInputs) {
            if (!(content.inner instanceof ItemIngredient ingredient)) continue;
            float chance = (float) content.chance / Content.MAX_CHANCE;
            var emiIngredient = getEmiIngredient(ingredient, true).setChance(chance);
            if (chance > 0) inputs.add(emiIngredient);
            else catalysts.add(emiIngredient);
        }
        for (var content : recipe.fluidInputs) {
            if (!(content.inner instanceof FluidIngredient ingredient) || ingredient.getFluid() == null) continue;
            float chance = (float) content.chance / Content.MAX_CHANCE;
            var emiIngredient = EmiStack.of(ingredient.getFluid(), ingredient.nbt, ingredient.amount).setChance(chance);
            if (chance > 0) inputs.add(emiIngredient);
            else catalysts.add(emiIngredient);
        }
        for (var content : recipe.itemOutputs) {
            if (!(content.inner instanceof ItemIngredient ingredient)) continue;
            outputs.add(getEmiStack(ingredient).setChance((float) content.chance / Content.MAX_CHANCE));
        }
        for (var content : recipe.fluidOutputs) {
            if (!(content.inner instanceof FluidIngredient ingredient) || ingredient.getFluid() == null) continue;
            outputs.add(EmiStack.of(ingredient.getFluid(), ingredient.nbt, ingredient.amount).setChance((float) content.chance / Content.MAX_CHANCE));
        }
        if (recipe.recipeType.isScanner()) collectResearchOutputs(outputs);
    }

    private void collectResearchOutputs(List<EmiStack> outputs) {
        ResearchManager.ResearchItem researchData = null;
        for (var content : recipe.itemOutputs) {
            var stack = content.inner.getInnerItemStack();
            if (stack.isEmpty()) continue;
            researchData = ResearchManager.readResearchId(stack);
            if (researchData != null) break;
        }
        if (researchData == null) return;
        var possibleRecipes = researchData.recipeType().getDataStickEntry(researchData.researchId());
        if (possibleRecipes == null) return;
        Set<ItemStack> seen = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.ITEM);
        for (var possible : possibleRecipes) {
            if (possible.itemOutputs.isEmpty()) continue;
            var ingredient = possible.itemOutputs.getFirst().inner;
            var stack = ingredient.getInnerItemStack();
            if (!stack.isEmpty() && seen.add(stack)) outputs.add(getEmiStack(ingredient));
        }
    }

    protected void collectDisplayIngredients(List<EmiStack> outputs, List<EmiIngredient> catalysts) {
        for (var widget : GTRecipeWidget.createInfoSlots(recipe)) {
            if (!(widget instanceof IRecipeIngredientSlot slot)) continue;
            var io = slot.getIngredientIO();
            if (io == null || io == IngredientIO.RENDER_ONLY) continue;
            for (Object ingredient : slot.getXEIIngredients()) {
                if (!(ingredient instanceof EmiIngredient emiIngredient) || emiIngredient.isEmpty()) continue;
                if (io == IngredientIO.OUTPUT) outputs.add(emiIngredient.getEmiStacks().getFirst());
                else catalysts.add(emiIngredient);
            }
        }
    }

    protected static EmiIngredient getEmiIngredient(ItemIngredient ingredient, boolean input) {
        if (!input) return getEmiStack(ingredient);
        Ingredient inner = ingredient.inner;
        ItemStack[] stacks = inner.getItems();
        if (stacks.length == 0) return EmiStack.EMPTY;
        if (inner.values.length == 1 && inner.values[0] instanceof Ingredient.TagValue tagValue) {
            var tag = new TagEmiIngredient(tagValue.tag, ingredient.amount);
            if (!tag.getEmiStacks().isEmpty() || isEmptyTagPlaceholder(stacks)) return tag;
        }
        if (stacks.length == 1) return stackOf(stacks[0], ingredient.amount);
        var list = new ArrayList<EmiIngredient>(stacks.length);
        for (var stack : stacks) list.add(EmiStack.of(stack));
        return EmiIngredient.of(list, ingredient.amount);
    }

    private static boolean isEmptyTagPlaceholder(ItemStack[] stacks) {
        return stacks.length == 1 && stacks[0].is(Items.BARRIER) && stacks[0].hasCustomHoverName();
    }

    protected static EmiStack getEmiStack(ItemIngredient ingredient) {
        ItemStack[] stacks = ingredient.inner.getItems();
        return stacks.length == 0 ? EmiStack.EMPTY : stackOf(stacks[0], ingredient.amount);
    }

    private static EmiStack stackOf(ItemStack stack, long amount) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null || nbt.isEmpty()) return new ItemEmiStack(stack.getItem(), null, amount);
        var emiStack = new ItemEmiStack(stack.getItem(), nbt, amount);
        emiStack.comparison(EmiPort.compareStrict());
        return emiStack;
    }
}
