package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.CircuitHandler;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfoMap;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeInfos;
import com.gregtechceu.gtceu.utils.collection.SafeR2LMap;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.util.FluidStackHashStrategy;
import com.gto.datasynclib.util.ItemStackHashStrategy;
import com.gto.datasynclib.util.holder.LongHolder;
import com.gto.fastcollection.fastutil.O2LOpenCacheHashMap;
import com.gto.recipesearch.IntLongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.ObjLongConsumer;

/**
 * 处理器分组：把同一方向上的一组 {@link IRecipeHandler} 当成<b>一个共享存储池</b>来参与配方。
 *
 * <p>
 * 单方块机器按方向把自身所有 trait 组成一个 unit；多方块的每个部件各自成组，
 * 控制器再把它们合并成更大的池子（默认实现在
 * {@code IWorkableMultiController#arrangeHandlerList}），输入侧与输出侧规则不同：
 * <ul>
 * <li><b>输入侧</b>：带涂装色的 unit 先按颜色、再按部件归类，然后<b>按层对齐</b>合并——
 * 取各部件第 i 个 unit 合成一组，层数不足的部件用自己最后一个 unit 顶替，
 * 因此每组的层数等于最厚的那个部件；{@link #isDistinct} 为 {@code true} 的无色 unit
 * 各自单独成组；其余无色 unit 全部并成一组。</li>
 * <li><b>输出侧</b>：带涂装色的 unit 按颜色整体合并成一组，并登记进
 * {@code getOutputColorMap()} 供 {@code recipe.outputColor} 选组；
 * 其余无色 unit 并成一组，这一侧不使用 {@link #isDistinct}。</li>
 * </ul>
 * 合并后同组内的存储会一起凑材料，并行数取决于整组的库存总量，而不是单个槽位。
 *
 * <h2>构造期完成的分组</h2>
 * <p>
 * 构造时会遍历所有 handler，按能力预先切好若干视图并缓存下来，避免热路径上反复判断：
 * <ul>
 * <li>{@link #itemHandlers}、{@link #fluidHandlers}：物品 / 流体的快速路径成员；</li>
 * <li>{@link #contentHandlers}：可参与配方检索的成员；</li>
 * <li>{@link #byType}：内容类型键 → 支持该类型的成员，泛型接口按此分发；</li>
 * <li>{@link #allHandlerTraits}：其中实现了 {@code IRecipeHandlerTrait} 的部分，用于订阅变更通知。</li>
 * </ul>
 *
 * <h2>方向一致性</h2>
 * <p>
 * 一个 unit 只有一个方向（{@link #handlerIO}）。把方向不同的 handler 放进同一个 unit 会在构造时抛异常，
 * 所有 {@code handle*} 方法收到不匹配的 {@code io} 也会立即抛异常——这是为了防止「把输入当输出扣」这类错误。
 */
public class RecipeHandlerUnit {

    /** 按优先级从高到低排序，用于机器装配时决定先问谁。 */
    public static final Comparator<RecipeHandlerUnit> PRIORITY_COMPARATOR = Comparator.comparingInt(u -> -u.priority);
    /** 先按类型、再按优先级排序：基类 unit 排在最后，让子类实现先被访问。 */
    public static final Comparator<RecipeHandlerUnit> TYPE_COMPARATOR = (a, b) -> {
        var aClass = a.getClass();
        var bClass = b.getClass();
        if (aClass == bClass) return Integer.compare(b.priority, a.priority);
        if (aClass == RecipeHandlerUnit.class) return 1;
        if (bClass == RecipeHandlerUnit.class) return -1;
        int cmp = Integer.compare(b.priority, a.priority);
        if (cmp != 0) return cmp;
        return aClass.getName().compareTo(bClass.getName());
    };

    /**
     * 空分组：方向为 {@link IO#NONE}，且没有任何成员。
     *
     * <p>
     * 典型用途：没有任何可用 trait 的部件、没有输入单元却仍要产配方的机器
     * （见 {@link ICustomRecipeLogicHolder}），以及不经处理器分组直接跑配方的机器。
     * 它对空内容恒为成功、对非空内容恒为失败。
     */
    public static final RecipeHandlerUnit NO_DATA = new RecipeHandlerUnit(IO.NONE, null) {

        @Override
        public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
            return items.isEmpty();
        }

        @Override
        public boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
            return fluids.isEmpty();
        }
    };

    /** 本分组所属的多方块部件；自建分组（{@link #of}）时为 {@code null}。 */
    public final IMultiPart part;

    /** 内容类型键 → 支持该类型的分组成员。泛型接口（{@code *Content}）按此表分发。 */
    public final RecipeInfoMap<IRecipeHandler[]> byType;
    /** 处理物品的成员（快速路径）。 */
    public final IRecipeHandler[] itemHandlers;
    /** 处理流体的成员（快速路径）。 */
    public final IRecipeHandler[] fluidHandlers;
    /** 可参与配方检索的成员，即 {@code canHandleContent(null)} 为真的 handler。 */
    public final IRecipeHandler[] contentHandlers;
    /** 全部成员，按优先级降序。 */
    public final IRecipeHandler[] allHandlers;
    /** 全部成员中实现了 {@code IRecipeHandlerTrait} 的部分，用于订阅存储变更。 */
    public final IRecipeHandlerTrait[] allHandlerTraits;

    /** 本分组的方向，由构造时传入，之后不可变；所有 {@code handle*} 调用都必须与之匹配。 */
    public final IO handlerIO;
    /**
     * 本分组的隔离标识，{@code -1} 表示未分组。
     *
     * <p>
     * 它<b>不是展示用的颜色</b>，而是「同色配对」的依据。完整链路是：
     * <ol>
     * <li>控制器按 {@link #color} 把 unit 分组（同色归为一组，见类注释）；</li>
     * <li>匹配用的输入分组若带颜色，{@code IRecipeLogicMachine#fullModifyRecipe} 会把它写进
     * {@code recipe.outputColor}；</li>
     * <li>输出侧据 {@code recipe.outputColor} 从 {@code IWorkableMultiController#getOutputColorMap()}
     * 取出同色的输出分组，产物只进这一组。</li>
     * </ol>
     * 于是给输入仓与输出仓涂同一种颜色，就能把二者绑成一对独立通道。
     * 具体取值方式由设置方决定，基础模组里取自部件涂装色。
     */
    public int color = -1;
    /**
     * 是否独立成组。
     *
     * <p>
     * 只在<b>没有涂装色</b>的 unit 之间起作用：为 {@code true} 时不再与其它无颜色 unit 合并，
     * 而是自己单独成为一组。由部件自行维护（基础模组里通过 {@code IDistinctPart} 的开关设置，
     * 且按需只在输入侧生效），本类只负责读取与重新装配。
     */
    public boolean isDistinct;

    /** 组内最高优先级，构造时取各成员的最大值。 */
    public int priority;

    /**
     * 组内是否有成员声明物品容量无限。
     *
     * <p>
     * 构造时聚合各成员的 {@link IRecipeHandler#isInfiniteItemCapacity()} 得到（只询问处理物品的成员）。
     * 它供 {@link com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic} 直接跳过输出并行数计算，
     * 也让 {@link #handleRecipeItem} 在「输出方向 + 模拟」时无条件成功。
     */
    public boolean isInfiniteItemCapacity;
    /** 流体版的 {@link #isInfiniteItemCapacity}。 */
    public boolean isInfiniteFluidCapacity;

    // cache
    /** 检索索引的复用对象，避免每次查询都新建 map。 */
    protected final IntLongMap intIngredientMap = new IntLongMap();
    /** 按内容类型缓存的并行数计算用的库存快照（{@link #getInputContentParallelAmount}）。 */
    protected RecipeInfoMap<O2LOpenCacheHashMap<Object>> byTypeCacheMap;
    /** 物品版库存快照（{@link #getInputItemParallelAmount}）。 */
    protected Reference2LongOpenHashMap<Item> itemMap;
    /** 流体版库存快照（{@link #getInputFluidParallelAmount}）。 */
    protected Reference2LongOpenHashMap<Fluid> fluidMap;

    /**
     * 组装一个分组。
     *
     * @param handlerIO 方向，组内所有 trait 的方向都必须与之一致
     * @param part      所属部件，自建分组时可为 {@code null}
     * @param handlers  成员；会就地被 {@link IFilteredHandler#PRIORITY_COMPARATOR} 排序
     */
    protected RecipeHandlerUnit(IO handlerIO, IMultiPart part, IRecipeHandler... handlers) {
        this.handlerIO = handlerIO;
        this.part = part;
        Arrays.sort(handlers, IFilteredHandler.PRIORITY_COMPARATOR);
        this.allHandlers = handlers;
        var types = new RecipeInfoMap<ArrayList<IRecipeHandler>>();
        var items = new ArrayList<IRecipeHandler>();
        var fluids = new ArrayList<IRecipeHandler>();
        var searchs = new ArrayList<IRecipeHandler>();
        var traits = new ArrayList<IRecipeHandlerTrait>();
        for (var handler : handlers) {
            var priority = handler.getPriority();
            if (this.priority < priority) this.priority = priority;
            if (handler.canHandleItem()) {
                if (handler.isInfiniteItemCapacity()) {
                    isInfiniteItemCapacity = true;
                }
                items.add(handler);
            }
            if (handler.canHandleFluid()) {
                if (handler.isInfiniteFluidCapacity()) {
                    isInfiniteFluidCapacity = true;
                }
                fluids.add(handler);
            }
            if (handler.canHandleContent(null)) searchs.add(handler);
            if (handler instanceof IRecipeHandlerTrait trait) {
                if (trait.getHandlerIO() != handlerIO) throw new IllegalArgumentException("RecipeHandlerTrait IO must match RecipeHandlerUnit IO");
                traits.add(trait);
            }
            for (var key : GTRecipeInfos.CONTENT_RECIPE_INFOS) {
                if (handler.canHandleContent(key)) {
                    types.computeIfAbsent(key, k -> new ArrayList<>()).add(handler);
                }
            }
        }
        this.itemHandlers = items.toArray(new IRecipeHandler[0]);
        this.fluidHandlers = fluids.toArray(new IRecipeHandler[0]);
        this.contentHandlers = searchs.toArray(new IRecipeHandler[0]);
        this.allHandlerTraits = traits.toArray(new IRecipeHandlerTrait[0]);
        this.byType = new RecipeInfoMap<>();
        types.forEach((k, v) -> this.byType.put(k, v.toArray(new IRecipeHandler[0])));
    }

    /** 用一组 handler 临时拼一个不隶属于任何部件的分组（无部件可通知）。 */
    public static RecipeHandlerUnit of(IO io, IRecipeHandler... handlers) {
        return new RecipeHandlerUnit(io, null, handlers);
    }

    /** {@link #of(IO, IRecipeHandler...)} 的集合版本。 */
    public static RecipeHandlerUnit of(IO io, Collection<IRecipeHandler> handlers) {
        return new RecipeHandlerUnit(io, null, handlers.toArray(new IRecipeHandler[0]));
    }

    /** {@link #of(IO, IRecipeHandler...)} 的集合版本，并关联所属部件。 */
    public static RecipeHandlerUnit of(IO io, IMultiPart part, Collection<IRecipeHandler> handlers) {
        return new RecipeHandlerUnit(io, part, handlers.toArray(new IRecipeHandler[0]));
    }

    /** 过滤出至少有一个成员能参与配方检索的分组（即 {@link #contentHandlers} 非空）。 */
    public static List<RecipeHandlerUnit> filterContent(Collection<RecipeHandlerUnit> handlers) {
        var list = new ArrayList<RecipeHandlerUnit>();
        handlers.forEach(h -> {
            if (h.contentHandlers.length > 0) list.add(h);
        });
        return list;
    }

    /**
     * {@link #wrapper} 出的新分组不会带上 {@code part}（新 unit 的 {@code part} 为 {@code null}），
     * 因此它不再归属于任何部件。
     *
     * <p>
     * 多方块装配时用它把多个同色仓室的 handler 合并成一个大池子，
     * 这样并行数就能按整组库存计算。
     */
    public RecipeHandlerUnit wrapper(Collection<IRecipeHandler> handlers) {
        var u = of(this.handlerIO, handlers);
        u.priority = this.priority;
        return u;
    }

    /** 修改 {@link #isDistinct} 并通知所属部件重新装配处理器列表。 */
    public final void setDistinctAndNotify(boolean distinct) {
        setDistinct(distinct);
        if (part != null) {
            notify(part);
        }
    }

    /** 只修改 {@link #isDistinct}，不触发重新装配。 */
    public final void setDistinct(boolean distinct) {
        if (isDistinct != distinct) {
            isDistinct = distinct;
        }
    }

    /** 设置展示颜色，不通知。 */
    public void setColor(int color) {
        setColor(color, false);
    }

    /**
     * 设置展示颜色。
     *
     * @param notify 是否通知所属部件重新装配（改颜色会影响多方块的合并分组）
     */
    public void setColor(int color, boolean notify) {
        this.color = color;
        if (notify && part != null) {
            notify(part);
        }
    }

    /** 让该部件所属的所有多方块控制器重新装配处理器列表。 */
    public static void notify(IMultiPart part) {
        for (IMultiController controller : part.getControllers()) {
            if (controller instanceof IWorkableMultiController workableMultiController) {
                workableMultiController.arrangeHandlerList();
            }
        }
    }

    /** 本分组能否用于指定方向；{@link #NO_DATA} 与 {@link IO#NONE} 恒为不可用。 */
    public boolean isValid(IO extIO) {
        if (this == NO_DATA || handlerIO == IO.NONE) return false;
        return extIO == handlerIO;
    }

    /**
     * 订阅组内所有 trait 的存储变更。
     *
     * @return 调用 {@code unsubscribe()} 即可一次性取消全部订阅
     */
    public ISubscription subscribe(Runnable listener) {
        ISubscription[] subs = new ISubscription[allHandlerTraits.length];
        for (int i = 0; i < subs.length; i++) {
            subs[i] = allHandlerTraits[i].addChangedListener(listener);
        }
        return () -> {
            for (var s : subs) {
                s.unsubscribe();
            }
        };
    }

    /** {@link #subscribe(Runnable)} 的过滤版本：只订阅实现了 {@code capability} 的 trait。 */
    public <T> ISubscription subscribe(Runnable listener, Class<T> capability) {
        var subs = new ArrayList<ISubscription>(allHandlerTraits.length);
        for (IRecipeHandlerTrait trait : allHandlerTraits) {
            if (capability.isInstance(trait)) {
                subs.add(trait.addChangedListener(listener));
            }
        }
        return () -> subs.forEach(ISubscription::unsubscribe);
    }

    /**
     * 取支持指定内容类型、且实现了 {@code capability} 的成员。
     *
     * <p>
     * 与只按类型过滤的 {@link #getCapabilities(Class)} 不同，这里会先按内容类型键缩小范围，
     * 例如「找支持流体的 {@code IFluidHandler}」不会误取到只处理物品的成员。
     *
     * @param key        内容类型键
     * @param capability 期望的能力接口
     */
    @NotNull
    public <T> List<T> getCapabilities(RecipeInfo key, Class<T> capability) {
        var all = byType.get(key);
        if (all == null || all.length == 0) return Collections.emptyList();
        var list = new ArrayList<T>(all.length);
        for (var handler : all) {
            if (capability.isInstance(handler)) {
                list.add(capability.cast(handler));
            }
        }
        return list;
    }

    /**
     * 取所有实现了 {@code capability} 的成员，不区分内容类型。
     *
     * @param capability 期望的能力接口
     */
    @NotNull
    public <T> List<T> getCapabilities(Class<T> capability) {
        var all = allHandlers;
        if (all.length == 0) return Collections.emptyList();
        var list = new ArrayList<T>(all.length);
        for (var handler : all) {
            if (capability.isInstance(handler)) {
                list.add(capability.cast(handler));
            }
        }
        return list;
    }

    /**
     * 用本分组的检索索引在配方类型中查一条配方。
     *
     * <p>
     * 先取 {@link #getSearchMap(GTRecipeType)}，索引为空说明本组没有任何可用于检索的材料，
     * 直接返回 {@code false} 省掉一次检索。
     */
    public boolean findRecipe(GTRecipeType recipeType, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var map = this.getSearchMap(recipeType);
        if (map.isEmpty()) return false;
        return recipeType.search(this, map, canHandle);
    }

    /**
     * 计算本分组能支撑的<b>输入</b>并行数：受 {@code contents} 里最紧缺的那一项限制。
     *
     * <p>
     * 先把组内所有非催化剂成员（{@link IRecipeHandler#isNotConsumable()} 为 {@code false}）的库存
     * 汇总成一份以 {@code Object} 为键的快照，再逐项检查：任何一项库存不足就直接返回 {@code 0}，
     * 否则把并行数收紧到 {@code 库存 / 需求} 的最小值。
     *
     * <p>
     * 只统计 {@code chance > 0} 的条目，即真正会被消耗的那部分输入；
     * {@code chance == 0} 的条目（催化剂）虽然参与配方匹配，但不会被消耗，因此不限制并行数。
     *
     * <p>
     * 快照刻意以 {@code Object} 为键——同一次计算里可能同时汇总物品与流体——因此取回时需要转回 {@code T}。
     *
     * @param key        内容类型键
     * @param contents   单份配方所需内容
     * @param multiplier 上游已算出的并行数上限，只会被本方法调小
     * @return 最终并行数；库存不足时为 {@code 0}
     */
    @SuppressWarnings("unchecked")
    public <T, C extends ContentInner<T>> long getInputContentParallelAmount(@NotNull ContentRecipeInfo<T, C> key, List<Content<C>> contents, long multiplier) {
        var handlers = byType.get(key);
        if (handlers == null) return 0;
        if (byTypeCacheMap == null) byTypeCacheMap = new RecipeInfoMap<>();
        var map = byTypeCacheMap.get(key);
        if (map == null) {
            map = new O2LOpenCacheHashMap<>();
            byTypeCacheMap.put(key, map);
        }
        var stacks = map;
        for (var container : handlers) {
            if (container.isNotConsumable()) continue;
            container.fastForEachContent(key, stacks::addTo);
        }
        for (var content : contents) {
            if (content.chance > 0) {
                long needed = content.amount;
                if (needed < 1) continue;
                long available = 0;
                for (var it = stacks.object2LongEntrySet().fastIterator(); it.hasNext();) {
                    var inventoryEntry = it.next();
                    if (content.inner.test((T) inventoryEntry.getKey())) {
                        available = inventoryEntry.getLongValue();
                        break;
                    }
                }
                if (available >= needed) {
                    multiplier = Math.min(multiplier, available / needed);
                } else {
                    multiplier = 0;
                    break;
                }
            }
        }
        stacks.clear();
        return multiplier;
    }

    /** {@link #getInputContentParallelAmount} 的物品快速路径版本，库存按 {@link Item} 汇总。 */
    public long getInputItemParallelAmount(List<Content<ItemIngredient>> contents, long multiplier) {
        if (itemMap == null) itemMap = new SafeR2LMap<>();
        var stacks = itemMap;
        for (var container : itemHandlers) {
            if (container.isNotConsumable()) continue;
            container.fastForEachItems((stack, amount) -> stacks.addTo(stack.getItem(), amount));
        }
        for (var content : contents) {
            if (content.chance > 0) {
                long needed = content.amount;
                if (needed < 1) continue;
                long available = 0;
                for (var iter = stacks.reference2LongEntrySet().fastIterator(); iter.hasNext();) {
                    var inventoryEntry = iter.next();
                    if (content.inner.testItem(inventoryEntry.getKey())) {
                        available += inventoryEntry.getLongValue();
                        if (available >= needed) break;
                    }
                }
                if (available >= needed) {
                    multiplier = Math.min(multiplier, available / needed);
                } else {
                    multiplier = 0;
                    break;
                }
            }
        }
        stacks.clear();
        return multiplier;
    }

    /** {@link #getInputContentParallelAmount} 的流体快速路径版本，库存按 {@link Fluid} 汇总。 */
    public long getInputFluidParallelAmount(List<Content<FluidIngredient>> contents, long multiplier) {
        if (fluidMap == null) fluidMap = new SafeR2LMap<>();
        var stacks = fluidMap;
        for (var container : fluidHandlers) {
            if (container.isNotConsumable()) continue;
            container.fastForEachFluids((stack, amount) -> stacks.addTo(stack.getFluid(), amount));
        }

        for (var content : contents) {
            if (content.chance > 0) {
                long needed = content.amount;
                if (needed < 1) continue;
                long available = 0;
                for (var it = stacks.reference2LongEntrySet().fastIterator(); it.hasNext();) {
                    var inventoryEntry = it.next();
                    if (content.inner.testFluid(inventoryEntry.getKey())) {
                        available = inventoryEntry.getLongValue();
                        break;
                    }
                }
                if (available >= needed) {
                    multiplier = Math.min(multiplier, available / needed);
                } else {
                    multiplier = 0;
                    break;
                }
            }
        }
        stacks.clear();
        return multiplier;
    }

    /**
     * 按内容类型取检索索引，只会问支持该类型的成员。
     *
     * <p>
     * 返回值是内部复用的同一个 {@link IntLongMap}，<b>下次调用即被清空</b>，不要长期持有。
     *
     * @return 内容 id → 数量；本组没有支持该类型的成员时返回 {@link IntLongMap#EMPTY}
     */
    public <T, C extends ContentInner<T>> IntLongMap getSearchMap(@NotNull ContentRecipeInfo<T, C> key, @NotNull GTRecipeType type) {
        var handlers = byType.get(key);
        if (handlers == null) return IntLongMap.EMPTY;
        intIngredientMap.clear();
        for (var s : handlers) {
            s.addToSearchMap(intIngredientMap, type);
        }
        return intIngredientMap;
    }

    /**
     * 取本分组完整的检索索引，汇总所有参与检索的成员。
     *
     * <p>
     * 返回值是内部复用的同一个 {@link IntLongMap}，<b>下次调用即被清空</b>，不要长期持有。
     */
    public IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        intIngredientMap.clear();
        for (var s : contentHandlers) {
            s.addToSearchMap(intIngredientMap, type);
        }
        return intIngredientMap;
    }

    /**
     * 遍历本组中支持 {@code key} 的成员所持有的内容。
     *
     * @param consumable {@code true} 时跳过催化剂类成员，即只报告真正会被消耗的那部分
     * @param function   回调，返回 {@code true} 表示中断
     * @return 是否被中断
     */
    public <T, C extends ContentInner<T>> boolean forEachContent(@NotNull ContentRecipeInfo<T, C> key, boolean consumable, ObjLongPredicate<T> function) {
        var handlers = byType.get(key);
        if (handlers == null) return false;
        for (var handler : handlers) {
            if (consumable && handler.isNotConsumable()) continue;
            if (handler.forEachContent(key, function)) return true;
        }
        return false;
    }

    /** {@link #forEachContent} 的不可中断版本。 */
    public <T, C extends ContentInner<T>> void fastForEachContent(@NotNull ContentRecipeInfo<T, C> key, boolean consumable, ObjLongConsumer<T> function) {
        var handlers = byType.get(key);
        if (handlers == null) return;
        for (var handler : handlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachContent(key, function);
        }
    }

    /**
     * 按内容类型把 {@code contents} 交给本组中支持该类型的成员处理，命中一个即成功。
     *
     * <p>
     * 内容为空视为成功；{@code simulate} 为 {@code false} 时会跳过催化剂类成员，
     * 避免把它们的内容真的扣掉。
     *
     * @param io 方向，必须与 {@link #handlerIO} 一致，否则抛 {@link IllegalStateException}
     * @return 是否有成员处理成功
     */
    public <T, C extends ContentInner<T>> boolean handleRecipeContent(@NotNull ContentRecipeInfo<T, C> key, IO io, GTRecipe recipe, List<Content<C>> contents, boolean simulate) {
        if (contents.isEmpty()) return true;
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        var handlers = byType.get(key);
        if (handlers == null) return false;
        for (var handler : handlers) {
            if (!simulate && handler.isNotConsumable()) continue;
            if (handler.handleRecipeContent(key, io, recipe, contents, simulate)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #handleRecipeContent} 的免配方版本，等价于传入 {@link GTRecipe#EMPTY}。 */
    public <T, C extends ContentInner<T>> boolean handleContent(@NotNull ContentRecipeInfo<T, C> key, IO io, List<Content<C>> contents, boolean simulate) {
        return handleRecipeContent(key, io, GTRecipe.EMPTY, contents, simulate);
    }

    /** 遍历组内物品。{@code consumable} 为 {@code true} 时跳过催化剂类成员。 */
    public boolean forEachItems(boolean consumable, ObjLongPredicate<ItemStack> function) {
        for (var handler : itemHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            if (handler.forEachItems(function)) return true;
        }
        return false;
    }

    /** 遍历组内流体。{@code consumable} 为 {@code true} 时跳过催化剂类成员。 */
    public boolean forEachFluids(boolean consumable, ObjLongPredicate<FluidStack> function) {
        for (var handler : fluidHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            if (handler.forEachFluids(function)) return true;
        }
        return false;
    }

    /** {@link #forEachItems} 的不可中断版本。 */
    public void fastForEachItems(boolean consumable, ObjLongConsumer<ItemStack> function) {
        for (var handler : itemHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachItems(function);
        }
    }

    /** {@link #forEachFluids} 的不可中断版本。 */
    public void fastForEachFluids(boolean consumable, ObjLongConsumer<FluidStack> function) {
        for (var handler : fluidHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachFluids(function);
        }
    }

    /** 一次性遍历组内物品与流体，直接问每个成员而不过滤方向。 */
    public void fastForEach(boolean consumable, ObjLongConsumer<ItemStack> itemFunction, ObjLongConsumer<FluidStack> FluidFunction) {
        for (var handler : allHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachItems(itemFunction);
            handler.fastForEachFluids(FluidFunction);
        }
    }

    /**
     * 处理一批物品。
     *
     * <p>
     * 与 {@link #handleRecipeContent} 的区别：输出方向且处于模拟时，只要组内有容量无限的成员就直接成功；
     * 另外传入 {@link GTRecipe#EMPTY} 时会跳过 {@link IRecipeHandler#isOnlyRecipe()} 的成员，
     * 因为它们只能配合真实配方工作。
     *
     * @param io 方向，必须与 {@link #handlerIO} 一致
     */
    public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        if (items.isEmpty()) return true;
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        if (io == IO.OUT && simulate && isInfiniteItemCapacity) return true;
        for (var handler : itemHandlers) {
            if (!simulate && handler.isNotConsumable()) continue;
            if (recipe == GTRecipe.EMPTY && handler.isOnlyRecipe()) continue;
            if (handler.handleRecipeItem(io, recipe, items, simulate)) {
                return true;
            }
        }
        return false;
    }

    /** 处理一批流体，语义与 {@link #handleRecipeItem} 完全对称。 */
    public boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
        if (fluids.isEmpty()) return true;
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        if (io == IO.OUT && simulate && isInfiniteFluidCapacity) return true;
        for (var handler : fluidHandlers) {
            if (!simulate && handler.isNotConsumable()) continue;
            if (recipe == GTRecipe.EMPTY && handler.isOnlyRecipe()) continue;
            if (handler.handleRecipeFluid(io, recipe, fluids, simulate)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #handleRecipeItem} 的免配方版本，等价于传入 {@link GTRecipe#EMPTY}。 */
    public boolean handleItem(IO io, List<Content<ItemIngredient>> items, boolean simulate) {
        return handleRecipeItem(io, GTRecipe.EMPTY, items, simulate);
    }

    /** {@link #handleRecipeFluid} 的免配方版本，等价于传入 {@link GTRecipe#EMPTY}。 */
    public boolean handleFluid(IO io, List<Content<FluidIngredient>> fluids, boolean simulate) {
        return handleRecipeFluid(io, GTRecipe.EMPTY, fluids, simulate);
    }

    /** 塞入物品：先模拟再执行，两步都成功才算成功。 */
    public boolean inputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        return handleItem(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleItem(IO.IN, contentList, false);
    }

    /** {@link #inputItem(ItemLike, long)} 的多个物品堆版本，空堆会被忽略。 */
    public boolean inputItem(ItemStack... items) {
        var contentList = toItemIngredient(items);
        return handleItem(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleItem(IO.IN, contentList, false);
    }

    /** 试算能否产出该物品，不改动存储。 */
    public boolean simulateOutputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        return handleItem(IO.OUT, contentList, true);
    }

    /** {@link #simulateOutputItem(ItemLike, long)} 的多个物品堆版本。 */
    public boolean simulateOutputItem(ItemStack... items) {
        var contentList = toItemIngredient(items);
        return handleItem(IO.OUT, contentList, true);
    }

    /** 真正产出该物品。 */
    public boolean outputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        return handleItem(IO.OUT, contentList, false);
    }

    /** {@link #outputItem(ItemLike, long)} 的多个物品堆版本。 */
    public boolean outputItem(ItemStack... items) {
        var contentList = toItemIngredient(items);
        return handleItem(IO.OUT, contentList, false);
    }

    /** 是否有该物品（{@code 1} 个即可）。 */
    public boolean matchItem(ItemLike item) {
        return matchItem(item, 1);
    }

    /**
     * 是否至少有 {@code amount} 个该物品。
     *
     * <p>
     * 用倒计数实现：遍历时不断从目标数量里扣除，扣到 0 即中断遍历并返回成功。
     * 遍历时 {@code consumable} 传 {@code false}，因此催化剂类成员里的存货也算数。
     */
    public boolean matchItem(ItemLike item, long amount) {
        var i = item.asItem();
        var holder = new LongHolder(amount);
        return forEachItems(false, (stack, a) -> {
            if (stack.is(i)) {
                holder.value -= a;
                return holder.value <= 0;
            }
            return false;
        });
    }

    /** 是否同时具备给定的所有物品堆（每个都按自身数量独立判定，任一不足即失败）。 */
    public boolean matchItem(ItemStack... items) {
        for (var item : items) {
            var holder = new LongHolder(item.getCount());
            if (!forEachItems(false, (stack, a) -> {
                if (ItemStackHashStrategy.ITEM_AND_TAG.equals(stack, item)) {
                    holder.value -= a;
                    return holder.value <= 0;
                }
                return false;
            })) {
                return false;
            }
        }
        return true;
    }

    /** 是否配置了指定编号的集成电路。 */
    public boolean matchCircuit(int configuration) {
        for (var iRecipeHandler : itemHandlers) {
            if (iRecipeHandler instanceof CircuitHandler circuitHandler) {
                var itemStack = circuitHandler.storage.stacks[0];
                if (itemStack.is(IntCircuitIngredient.PROGRAMMED_CIRCUIT) && configuration == IntCircuitIngredient.getConfiguration(itemStack.getTag())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 注入流体：先模拟再执行，两步都成功才算成功。 */
    public boolean inputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        return handleFluid(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleFluid(IO.IN, contentList, false);
    }

    /** {@link #inputFluid(Fluid, long)} 的多个流体堆版本，空堆会被忽略。 */
    public boolean inputFluid(FluidStack... fluids) {
        var contentList = toFluidIngredient(fluids);
        return handleFluid(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleFluid(IO.IN, contentList, false);
    }

    /** 试算能否产出该流体，不改动存储。 */
    public boolean simulateOutputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        return handleFluid(IO.OUT, contentList, true);
    }

    /** {@link #simulateOutputFluid(Fluid, long)} 的多个流体堆版本。 */
    public boolean simulateOutputFluid(FluidStack... fluids) {
        var contentList = toFluidIngredient(fluids);
        return handleFluid(IO.OUT, contentList, true);
    }

    /** 真正产出该流体。 */
    public boolean outputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        return handleFluid(IO.OUT, contentList, false);
    }

    /** {@link #outputFluid(Fluid, long)} 的多个流体堆版本。 */
    public boolean outputFluid(FluidStack... fluids) {
        var contentList = toFluidIngredient(fluids);
        return handleFluid(IO.OUT, contentList, false);
    }

    /** 是否有该流体（{@code 1} 桶即可）。 */
    public boolean matchFluid(Fluid fluid) {
        return matchFluid(fluid, 1);
    }

    /** 是否至少有 {@code amount} 的该流体，实现方式同 {@link #matchItem(ItemLike, long)}。 */
    public boolean matchFluid(Fluid fluid, long amount) {
        var holder = new LongHolder(amount);
        return forEachFluids(false, (stack, a) -> {
            if (stack.getFluid() == fluid) {
                holder.value -= a;
                return holder.value <= 0;
            }
            return false;
        });
    }

    /** 是否同时具备给定的所有流体堆（每个都按自身数量独立判定）。 */
    public boolean matchFluid(FluidStack... fluids) {
        for (var fluid : fluids) {
            var holder = new LongHolder(fluid.getAmount());
            if (!forEachFluids(false, (stack, a) -> {
                if (FluidStackHashStrategy.FLUID_AND_TAG.equals(stack, fluid)) {
                    holder.value -= a;
                    return holder.value <= 0;
                }
                return false;
            })) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读取集成电路编号。
     *
     * @param sum {@code true} 时累加所有单元上的编号；{@code false} 时只取第一个非 0 的编号
     */
    public int getCircuit(boolean sum) {
        int circuit = 0;
        for (var iRecipeHandler : itemHandlers) {
            if (iRecipeHandler instanceof CircuitHandler circuitHandler) {
                var itemStack = circuitHandler.storage.stacks[0];
                if (itemStack.is(IntCircuitIngredient.PROGRAMMED_CIRCUIT)) {
                    var c = IntCircuitIngredient.getConfiguration(itemStack.getTag());
                    if (c > 0) {
                        circuit += c;
                        if (!sum) break;
                    }
                }
            }
        }
        return circuit;
    }

    /** 统计指定物品的数量，见 {@link #getItemAmount(boolean, Item[], long[])}。 */
    public long[] getItemAmount(boolean consumable, Item... items) {
        long[] amounts = new long[items.length];
        getItemAmount(consumable, items, amounts);
        return amounts;
    }

    /** 统计指定流体的数量，见 {@link #getFluidAmount(boolean, Fluid[], long[])}。 */
    public long[] getFluidAmount(boolean consumable, Fluid... fluids) {
        long[] amounts = new long[fluids.length];
        getFluidAmount(consumable, fluids, amounts);
        return amounts;
    }

    /**
     * 把各物品的数量累加进 {@code amounts}。
     *
     * <p>
     * 数量溢出时饱和到 {@link Long#MAX_VALUE}，不会回绕成负数。
     *
     * @param consumable {@code true} 时跳过催化剂类成员
     * @param amounts    结果数组，与 {@code items} 一一对应，会被就地修改
     */
    public void getItemAmount(boolean consumable, Item[] items, long[] amounts) {
        fastForEachItems(consumable, (stack, amount) -> {
            var item = stack.getItem();
            for (int i = 0; i < items.length; i++) {
                if (items[i] == item) {
                    var a = amount + amounts[i];
                    if (a < 0) {
                        amounts[i] = Long.MAX_VALUE;
                    } else {
                        amounts[i] = a;
                    }
                    break;
                }
            }
        });
    }

    /**
     * 把各流体的数量累加进 {@code amounts}，溢出时同样饱和到 {@link Long#MAX_VALUE}。
     *
     * @param consumable {@code true} 时跳过催化剂类成员
     * @param amounts    结果数组，与 {@code fluids} 一一对应，会被就地修改
     */
    public void getFluidAmount(boolean consumable, Fluid[] fluids, long[] amounts) {
        fastForEachFluids(consumable, (stack, amount) -> {
            var fluid = stack.getFluid();
            for (int i = 0; i < fluids.length; i++) {
                if (fluids[i] == fluid) {
                    var a = amount + amounts[i];
                    if (a < 0) {
                        amounts[i] = Long.MAX_VALUE;
                    } else {
                        amounts[i] = a;
                    }
                    break;
                }
            }
        });
    }

    /** 把物品堆转成配方内容列表，空堆会被跳过。 */
    public static List<Content<ItemIngredient>> toItemIngredient(ItemStack... item) {
        var contentList = new ArrayList<Content<ItemIngredient>>(item.length);
        for (var content : item) {
            if (content.isEmpty()) continue;
            contentList.add(new Content<>(ItemIngredient.of(content)));
        }
        return contentList;
    }

    /** 把流体堆转成配方内容列表，空堆会被跳过。 */
    public static List<Content<FluidIngredient>> toFluidIngredient(FluidStack... fluid) {
        var contentList = new ArrayList<Content<FluidIngredient>>(fluid.length);
        for (FluidStack content : fluid) {
            if (content.isEmpty()) continue;
            contentList.add(new Content<>(FluidIngredient.of(content)));
        }
        return contentList;
    }
}
