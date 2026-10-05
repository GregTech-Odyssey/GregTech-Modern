package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.machine.feature.IElectricMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.info.*;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * 持有配方处理器的机器：把机器上零散的 {@link IRecipeHandler}（物品槽、流体罐、能量仓……）
 * 组织成可按方向取用的处理器分组 {@link RecipeHandlerUnit}，并对外提供整套配方执行流程。
 *
 * <h2>两层结构</h2>
 * <ul>
 * <li><b>扁平层</b>（{@code getCapabilitiesFlat}）：{@code IO} → 该方向上所有 handler 的列表。
 * 用于按能力类型找东西（例如找能量容器、找计算提供者）。</li>
 * <li><b>分组层</b>（{@code getCapabilitiesProxy} / {@link #getInputUnits()} / {@link #getOutputUnits()}）：
 * {@code IO} → {@link RecipeHandlerUnit} 列表。一个分组内的 handler 会共享同一份存储池参与匹配与并行计算；
 * 多方块控制器还会按涂装色与 {@code isDistinct} 把各部件自己的 unit 合并成更大的组
 * （规则见 {@link RecipeHandlerUnit}）。</li>
 * </ul>
 *
 * <h2>一次配方的完整流程</h2>
 * <p>
 * 以 {@code RecipeLogic} 的实际调用顺序为准：
 * <ol>
 * <li>{@link #findRecipe} 检索候选配方；每命中一条就依次做
 * {@link #checkTier}、{@link #checkConditions}，再进入下一步；</li>
 * <li>先由 {@code fullModifyRecipe} 施加配方修饰（并行、超频等），得到实际要执行的那条配方；</li>
 * <li>{@link #matchTickRecipe} 试算每 tick 的持续消耗（能量、CWU），
 * 再 {@link #matchRecipe} 模拟整条配方（输入 + 输出都要放得下）；</li>
 * <li>通过后 {@link #handleRecipeInput} 真正扣料并开始工作；</li>
 * <li>工作中每 tick 调 {@link #handleTickRecipe} 持续扣电 / 发电；</li>
 * <li>完成后 {@link #handleRecipeOutput} 真正产出。</li>
 * </ol>
 * 「match」系列只试算，「handle」系列才真正改动存储。配方跑完后默认会优先尝试沿用上一条配方，
 * 只有在 {@link #alwaysSearchRecipe()} 为 {@code true} 时才每次都重新检索。
 *
 * <h2>失败原因</h2>
 * <p>
 * 流程中的失败大多会通过 {@link #setIdleReason} 记下原因，供 GUI 显示机器为何停机。
 */
public interface IRecipeHandlerHolder extends IMachineFeature {

    /** 是否已经挂载了任何处理器分组。 */
    default boolean hasCapabilityProxies() {
        return !getCapabilitiesProxy().isEmpty();
    }

    /** 该机器全部<b>输入</b>方向的分组。 */
    default List<RecipeHandlerUnit> getInputUnits() {
        return getCapabilitiesProxy().getOrDefault(IO.IN, Collections.emptyList());
    }

    /** 该机器全部<b>输出</b>方向的分组。 */
    default List<RecipeHandlerUnit> getOutputUnits() {
        return getCapabilitiesProxy().getOrDefault(IO.OUT, Collections.emptyList());
    }

    /**
     * 针对某条具体配方的输出分组。
     *
     * <p>
     * 默认与 {@link #getOutputUnits()} 相同、忽略配方。多方块控制器覆写它来实现
     * <b>按颜色路由产物</b>：配方里的 {@code recipe.outputColor} 不为 {@code -1} 时，
     * 只返回该颜色对应的那一组输出仓（映射见 {@code IWorkableMultiController#getOutputColorMap()}），
     * 找不到才退回全部输出组。
     */
    default List<RecipeHandlerUnit> getOutputUnits(GTRecipe recipe) {
        return getCapabilitiesProxy().getOrDefault(IO.OUT, Collections.emptyList());
    }

    /** @return 方向 → 处理器分组；分组由机器在装配时填充 */
    @NotNull
    Map<IO, List<RecipeHandlerUnit>> getCapabilitiesProxy();

    /** @return 方向 → 扁平化的 handler 列表，用于按能力类型检索 */
    @NotNull
    Map<IO, List<IRecipeHandler>> getCapabilitiesFlat();

    /** 取某个方向上扁平化的 handler 列表。 */
    @NotNull
    default List<IRecipeHandler> getCapabilitiesFlat(IO io) {
        return getCapabilitiesFlat().getOrDefault(io, Collections.emptyList());
    }

    @NotNull
    default List<IRecipeHandler> getItemCapabilitiesFlat(IO io) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<IRecipeHandler>(all.size());
        for (var handler : all) {
            if (handler.handlesItems()) {
                list.add(handler);
            }
        }
        return list;
    }

    @NotNull
    default List<IRecipeHandler> getFluidCapabilitiesFlat(IO io) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<IRecipeHandler>(all.size());
        for (var handler : all) {
            if (handler.handlesFluids()) {
                list.add(handler);
            }
        }
        return list;
    }

    /**
     * 取某个方向上实现了指定接口的 handler。
     *
     * @param capability 期望的能力接口或类型
     * @return 匹配的 handler；没有时返回空列表
     */
    @NotNull
    default <T> List<T> getCapabilitiesFlat(IO io, Class<T> capability) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<T>(all.size());
        for (var handler : all) {
            if (capability.isInstance(handler)) {
                list.add(capability.cast(handler));
            }
        }
        return list;
    }

    /**
     * 登记一个处理器分组：它会被同时放入分组层与扁平层（扁平层按 handler 去重）。
     *
     * <p>
     * {@link RecipeHandlerUnit#NO_DATA}、{@code IO.NONE} 以及不含任何 handler 的分组会被忽略。
     */
    default void addHandlerList(RecipeHandlerUnit unit) {
        if (unit == RecipeHandlerUnit.NO_DATA || unit.handlerIO == IO.NONE || unit.allHandlers.length == 0) return;
        getCapabilitiesProxy().computeIfAbsent(unit.handlerIO, i -> new ArrayList<>()).add(unit);
        var list = getCapabilitiesFlat().computeIfAbsent(unit.handlerIO, i -> new ArrayList<>());
        for (var handler : unit.allHandlers) {
            if (list.contains(handler)) continue;
            list.add(handler);
        }
    }

    /**
     * 是否改用「优先级检索」：先把一个分组能出的配方全部取出，按配方优先级排序后再逐个询问
     * {@code canHandle}，见 {@link #prioritySearch}。
     *
     * @return 默认 {@code false}，即直接按 {@link GTRecipeType} 的检索顺序命中即止
     */
    default boolean usePrioritySearch() {
        return false;
    }

    /**
     * 每次配方跑完后是否<b>跳过「沿用上一条配方」的快捷路径</b>，强制重新检索配方表。
     *
     * <p>
     * 为 {@code false}（默认）时，{@code RecipeLogic#onRecipeFinish} 会先用上次命中的
     * 配方与分组再匹配一次，成功就直接继续跑同一条配方；为 {@code true} 时跳过这一步，
     * 每次都走完整的 {@link #findRecipe}。
     *
     * @return 默认 {@code false}
     */
    default boolean alwaysSearchRecipe() {
        return false;
    }

    /**
     * 检索一条可执行配方。
     *
     * <p>
     * 依次询问每个输入分组 {@link RecipeHandlerUnit#findRecipe}；{@code canHandle} 返回 {@code true}
     * 即视为找到并立即返回。若配方类型注册了 {@link GTRecipeType#getCustomRecipeLogicRunners()}，
     * 每个分组还会额外询问这些自定义逻辑。覆写时也可以像
     * {@link ICustomRecipeLogicHolder} 那样自行造配方。
     *
     * @param type      要检索的配方类型
     * @param canHandle 判定回调（输入分组，候选配方）
     * @return 是否找到
     */
    default boolean findRecipe(GTRecipeType type, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle, GTRecipeDefinition lockedRecipe) {
        var units = this.getInputUnits();
        int unitCount = units.size();
        if (lockedRecipe != null) {
            for (int i = 0; i < unitCount; i++) {
                if (canHandle.test(units.get(i), lockedRecipe)) return true;
            }
        } else {
            if (usePrioritySearch()) return prioritySearch(type, this, canHandle);
            var customRecipeLogic = type.getCustomRecipeLogicRunners();
            int logicCount = customRecipeLogic.size();
            for (int i = 0; i < unitCount; i++) {
                var unit = units.get(i);
                if (unit.findRecipe(type, canHandle)) return true;
                for (int j = 0; j < logicCount; j++) {
                    var r = customRecipeLogic.get(j).createCustomRecipe(this, unit);
                    if (r != null && canHandle.test(unit, r)) return true;
                }
            }
        }
        return false;
    }

    /**
     * 优先级检索：先收集每个输入分组能出的全部候选配方，按 {@link GTRecipeDefinition#priority} 从高到低排序，
     * 再逐个交给 {@code canHandle}。
     *
     * <p>
     * 代价是每换一个分组都要重新收集并排序，因此只在
     * {@link #usePrioritySearch()} 为 {@code true} 时使用。
     *
     * @return 是否有配方被 {@code canHandle} 接受
     */
    static boolean prioritySearch(GTRecipeType type, IRecipeHandlerHolder holder, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var recipes = new ArrayList<Pair<RecipeHandlerUnit, GTRecipeDefinition>>();
        for (var list : holder.getInputUnits()) {
            list.findRecipe(type, (u, r) -> {
                recipes.add(Pair.of(u, r));
                return false;
            });
            recipes.sort(Comparator.comparingInt(p -> -p.getSecond().priority));
            for (var p : recipes) {
                if (canHandle.test(p.getFirst(), p.getSecond())) return true;
            }
        }
        return false;
    }

    /**
     * 设置机器当前的停机原因，供 GUI 显示。
     *
     * <p>
     * 接受 {@link Supplier} 是为了延迟构造文本——只有真正要显示时才会求值。
     */
    void setIdleReason(Supplier<Component> reason);

    /** {@link #setIdleReason(Supplier)} 的便捷重载，直接传入固定文本。 */
    default void setIdleReason(Component reason) {
        setIdleReason(() -> reason);
    }

    /** {@link #setIdleReason(Supplier)} 的便捷重载，失败时才会展示 {@link ActionResult} 携带的原因。 */
    default void setIdleReason(ActionResult result) {
        setIdleReason((Supplier<Component>) result);
    }

    /**
     * 检查配方的条件是否全部满足。
     *
     * <p>
     * 普通条件（{@link RecipeCondition#isOr()} 为 {@code false}）必须逐个通过；
     * 带 OR 标记的同类型条件之间只要有一个通过即可，全部失败时把它们的原因拼接后写入停机原因。
     *
     * @return 是否全部通过
     */
    default boolean checkConditions(RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (recipe.conditions.length == 0) return true;
        Map<Class<?>, List<RecipeCondition>> or = new Reference2ObjectArrayMap<>();
        for (RecipeCondition condition : recipe.conditions) {
            if (condition.isOr()) {
                or.computeIfAbsent(condition.getClass(), type -> new ArrayList<>()).add(condition);
            } else if (!condition.check(this, unit, recipe)) {
                setIdleReason(() -> ActionResult.failCondition(condition.getTooltips()).reason());
                return false;
            }
        }

        for (List<RecipeCondition> conditions : or.values()) {
            boolean passed = conditions.isEmpty();
            MutableComponent component = Component.translatable("gtceu.recipe_logic.condition_fails").append(": ");
            for (RecipeCondition condition : conditions) {
                passed = condition.check(this, unit, recipe);
                if (passed) break;
                else component.append(condition.getTooltips());
            }

            if (!passed) {
                setIdleReason(component);
                return false;
            }
        }
        return true;
    }

    /**
     * 检查机器等级是否够得上配方要求的等级。
     *
     * <p>
     * 只有配方声明了 {@code tier > 0} 且本机器是 {@link ITieredMachine} 时才会比较；
     * 不满足时写入 {@link ActionResult#FAIL_INSUFFICIENT_TIER}。
     */
    default boolean checkTier(GTRecipeDefinition recipe) {
        int tier = recipe.tier;
        if (tier > 0 && this instanceof ITieredMachine tieredMachine) {
            if (tier > tieredMachine.getRecipeTier()) {
                setIdleReason(ActionResult.FAIL_INSUFFICIENT_TIER);
                return false;
            }
        }
        return true;
    }

    default boolean matchRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        return matchRecipeInput(unit, recipe) && matchRecipeOutput(recipe);
    }

    default boolean matchRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!unit.matchInputs(recipe)) return false;
        for (var e : recipe.definition.recipeExtensions) {
            if (!e.handleInput(this, unit, recipe, true)) return false;
        }
        return true;
    }

    default boolean matchRecipeOutput(GTRecipe recipe) {
        for (var e : recipe.definition.recipeExtensions) {
            if (!e.handleOutput(this, recipe, true)) return false;
        }
        if (recipe.itemOutputs.isEmpty() && recipe.fluidOutputs.isEmpty()) return true;
        var p = PlanScratch.acquire();
        try {
            for (var unit : getOutputUnits(recipe)) {
                if (unit.fitsOutputs(recipe, p, recipe.scale)) return true;
            }
        } finally {
            PlanScratch.release();
        }
        setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
        return false;
    }

    default boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        var p = PlanScratch.acquire();
        try {
            return handleRecipeInput(unit, recipe, p);
        } finally {
            PlanScratch.release();
        }
    }

    private boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe, PlanScratch p) {
        unit.rollInputs(recipe, p);
        if (!unit.planInputs(recipe, p, recipe.scale, true)) return false;
        var extensions = recipe.definition.recipeExtensions;
        for (var e : extensions) {
            if (!e.handleInput(this, unit, recipe, true)) return false;
        }
        if (!unit.commitFallible(p)) return false;
        for (var e : extensions) {
            if (!e.handleInput(this, unit, recipe, false)) {
                unit.rollbackFallible(p);
                return false;
            }
        }
        unit.commitArrays(p);
        unit.onCommitted(recipe);
        return true;
    }

    default boolean handleRecipeOutput(GTRecipe recipe) {
        var extension = true;
        for (var e : recipe.definition.recipeExtensions) {
            if (!e.handleOutput(this, recipe, false)) extension = false;
        }
        var items = recipe.itemOutputs;
        var fluids = recipe.fluidOutputs;
        if (items.isEmpty() && fluids.isEmpty()) return extension;
        var p = PlanScratch.acquire();
        try {
            var itemLeft = p.need(false, items.size());
            var fluidLeft = p.need(true, fluids.size());
            RecipeHandlerUnit.rollList(recipe, items, itemLeft);
            RecipeHandlerUnit.rollList(recipe, fluids, fluidLeft);
            for (var unit : getOutputUnits(recipe)) {
                boolean item = unit.insertOutputs(items, itemLeft, false);
                boolean fluid = unit.insertOutputs(fluids, fluidLeft, true);
                if (item && fluid) return extension;
            }
            return false;
        } finally {
            PlanScratch.release();
        }
    }

    default boolean matchTickRecipe(GTRecipe recipe) {
        var eu = recipe.eut;
        if (eu != 0) {
            if (!(this instanceof IElectricMachine electricMachine && electricMachine.useEnergy(eu, true))) {
                if (eu > 0) {
                    setIdleReason(() -> ActionResult.failInsufficientIn(EURecipeInfo.INSTANCE.getName()).reason());
                } else {
                    setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                }
                return false;
            }
        }
        for (var e : recipe.definition.tickRecipeExtensions) {
            if (!e.handleTick(this, recipe, true)) return false;
        }
        return true;
    }

    default boolean handleTickRecipe(GTRecipe recipe) {
        var eu = recipe.eut;
        if (eu != 0) {
            if (!(this instanceof IElectricMachine electricMachine && electricMachine.useEnergy(eu, false))) {
                if (eu > 0) {
                    setIdleReason(() -> ActionResult.failInsufficientIn(EURecipeInfo.INSTANCE.getName()).reason());
                } else {
                    setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                }
                return false;
            }
        }
        for (var e : recipe.definition.tickRecipeExtensions) {
            if (!e.handleTick(this, recipe, false)) return false;
        }
        return true;
    }

    default int getCircuit(boolean sum) {
        int circuit = 0;
        for (var handler : getInputUnits()) {
            var c = handler.getCircuit(sum);
            if (c > 0) {
                circuit += c;
                if (!sum) break;
            }
        }
        return circuit;
    }

    default long[] getFluidAmount(boolean consumable, Fluid... fluids) {
        long[] amounts = new long[fluids.length];
        getFluidAmount(consumable, fluids, amounts);
        return amounts;
    }

    default void getFluidAmount(boolean consumable, Fluid[] fluids, long[] amounts) {
        for (var handler : getInputUnits()) {
            handler.getFluidAmount(consumable, fluids, amounts);
        }
    }

    default long[] getItemAmount(boolean consumable, Item... items) {
        long[] amounts = new long[items.length];
        getItemAmount(consumable, items, amounts);
        return amounts;
    }

    default void getItemAmount(boolean consumable, Item[] items, long[] amounts) {
        for (var handler : getInputUnits()) {
            handler.getItemAmount(consumable, items, amounts);
        }
    }

    default boolean forEachKey(AEKeyType type, boolean consumable, IRecipeHandler.KeyVisitor visitor) {
        for (var handler : getInputUnits()) {
            if (handler.forEachKey(type, consumable, visitor)) return true;
        }
        return false;
    }

    default boolean inputItem(ItemLike item, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.inputItem(item, amount)) return true;
        }
        return false;
    }

    default boolean inputItem(AEItemKey key, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.inputItem(key, amount)) return true;
        }
        return false;
    }

    default boolean simulateOutputItem(ItemLike item, long amount) {
        return simulateOutput(AEItemKey.of(item), amount);
    }

    default boolean outputItem(ItemLike item, long amount) {
        return output(AEItemKey.of(item), amount);
    }

    default boolean simulateOutput(AEKey key, long amount) {
        for (var handler : getOutputUnits()) {
            if (handler.output(key, amount, true)) return true;
        }
        setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
        return false;
    }

    default boolean output(AEKey key, long amount) {
        for (var handler : getOutputUnits()) {
            if (handler.output(key, amount, true)) return handler.output(key, amount, false);
        }
        setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
        return false;
    }

    default boolean matchItem(ItemLike item) {
        return matchItem(item, 1);
    }

    default boolean matchItem(ItemLike item, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.matchItem(item, amount)) return true;
        }
        return false;
    }

    default boolean matchKey(AEKey key, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.matchKey(key, amount)) return true;
        }
        return false;
    }

    default boolean matchCircuit(int configuration) {
        for (var handler : getInputUnits()) {
            if (handler.matchCircuit(configuration)) return true;
        }
        return false;
    }

    default boolean inputFluid(Fluid fluid, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.inputFluid(fluid, amount)) return true;
        }
        return false;
    }

    default boolean inputFluid(AEFluidKey key, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.inputFluid(key, amount)) return true;
        }
        return false;
    }

    default boolean simulateOutputFluid(Fluid fluid, long amount) {
        return simulateOutput(AEFluidKey.of(fluid), amount);
    }

    default boolean outputFluid(Fluid fluid, long amount) {
        return output(AEFluidKey.of(fluid), amount);
    }

    default boolean matchFluid(Fluid fluid) {
        return matchFluid(fluid, 1);
    }

    default boolean matchFluid(Fluid fluid, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.matchFluid(fluid, amount)) return true;
        }
        return false;
    }
}
