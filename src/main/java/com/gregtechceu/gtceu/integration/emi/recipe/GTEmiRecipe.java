package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
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
import org.jetbrains.annotations.Nullable;

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
                        slotW.setHandlerSlot((IItemHandlerModifiable) EmptyHandler.INSTANCE, 0);
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

    @Override
    public void addTempWidgets(WidgetHolder widgets) {
        frame = GTRecipeWidget.PageFrame.COMPACT;
        super.addTempWidgets(widgets);
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
        collectInputs(recipe.itemInputs, inputs, catalysts);
        collectInputs(recipe.fluidInputs, inputs, catalysts);
        collectOutputs(recipe.itemOutputs, outputs);
        collectOutputs(recipe.fluidOutputs, outputs);
        if (recipe.recipeType.isScanner()) collectResearchOutputs(outputs);
    }

    private static void collectInputs(ContentList contents, List<EmiIngredient> inputs, List<EmiIngredient> catalysts) {
        for (int i = 0; i < contents.size(); i++) {
            var ingredient = contents.ingredient(i);
            float chance = (float) contents.chance(i) / ContentList.MAX_CHANCE;
            var emiIngredient = getEmiIngredient(ingredient, contents.amount(i), true);
            if (ingredient.isFluid() && emiIngredient.isEmpty()) continue;
            emiIngredient = emiIngredient.setChance(chance);
            if (chance > 0) inputs.add(emiIngredient);
            else catalysts.add(emiIngredient);
        }
    }

    private static void collectOutputs(ContentList contents, List<EmiStack> outputs) {
        for (int i = 0; i < contents.size(); i++) {
            var stack = getEmiStack(contents.ingredient(i), contents.amount(i));
            if (stack.isEmpty()) continue;
            outputs.add(stack.setChance((float) contents.chance(i) / ContentList.MAX_CHANCE));
        }
    }

    private void collectResearchOutputs(List<EmiStack> outputs) {
        ResearchManager.ResearchItem researchData = null;
        var itemOutputs = recipe.itemOutputs;
        for (int i = 0; i < itemOutputs.size(); i++) {
            if (!(itemOutputs.ingredient(i).displayKey() instanceof AEItemKey key)) continue;
            researchData = ResearchManager.readResearchId(key.getReadOnlyStack());
            if (researchData != null) break;
        }
        if (researchData == null) return;
        var possibleRecipes = researchData.recipeType().getDataStickEntry(researchData.researchId());
        if (possibleRecipes == null) return;
        Set<ItemStack> seen = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.ITEM);
        for (var possible : possibleRecipes) {
            var possibleOutputs = possible.itemOutputs;
            if (possibleOutputs.isEmpty()) continue;
            var ingredient = possibleOutputs.ingredient(0);
            if (ingredient.displayKey() instanceof AEItemKey key && seen.add(key.getReadOnlyStack())) {
                outputs.add(getEmiStack(ingredient, possibleOutputs.amount(0)));
            }
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

    protected static EmiIngredient getEmiIngredient(KeyIngredient ingredient, long amount, boolean input) {
        if (!input) return getEmiStack(ingredient, amount);
        switch (ingredient.kind) {
            case KeyIngredient.EXACT, KeyIngredient.BASE, KeyIngredient.CIRCUIT -> {
                return keyStack(ingredient.key(), amount);
            }
            case KeyIngredient.TAG -> {
                if (ingredient.tag() != null) {
                    var tag = new TagEmiIngredient(ingredient.tag(), amount);
                    var tagStacks = tag.getEmiStacks();
                    if (tagStacks.size() == 1) return tagStacks.getFirst().copy().setAmount(amount);
                    if (!tagStacks.isEmpty()) return tag;
                    var members = ingredient.isItem() ? ingredient.getItems().length : ingredient.getFluids(1).length;
                    if (members == 0) return tag;
                }
                return listOf(ingredient, amount);
            }
            default -> {
                return listOf(ingredient, amount);
            }
        }
    }

    private static EmiIngredient listOf(KeyIngredient ingredient, long amount) {
        if (ingredient.isFluid()) {
            FluidStack[] fluids = ingredient.getFluids(1);
            if (fluids.length == 0) return EmiStack.EMPTY;
            if (fluids.length == 1) return EmiStack.of(fluids[0].getFluid(), fluids[0].getTag(), amount);
            var list = new ArrayList<EmiIngredient>(fluids.length);
            for (var fluid : fluids) list.add(EmiStack.of(fluid.getFluid(), fluid.getTag()));
            return EmiIngredient.of(list, amount);
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) return EmiStack.EMPTY;
        if (stacks.length == 1) return stackOf(stacks[0], amount);
        var list = new ArrayList<EmiIngredient>(stacks.length);
        for (var stack : stacks) list.add(EmiStack.of(stack));
        return EmiIngredient.of(list, amount);
    }

    protected static EmiStack getEmiStack(KeyIngredient ingredient, long amount) {
        return keyStack(ingredient.displayKey(), amount);
    }

    private static EmiStack keyStack(@Nullable AEKey key, long amount) {
        if (key instanceof AEItemKey itemKey) return stackOf(itemKey.getReadOnlyStack(), amount);
        if (key instanceof AEFluidKey fluidKey) return EmiStack.of(fluidKey.getFluid(), fluidKey.getTag(), amount);
        return EmiStack.EMPTY;
    }

    private static EmiStack stackOf(ItemStack stack, long amount) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null || nbt.isEmpty()) return new ItemEmiStack(stack.getItem(), null, amount);
        var emiStack = new ItemEmiStack(stack.getItem(), nbt, amount);
        emiStack.comparison(EmiPort.compareStrict());
        return emiStack;
    }
}
