package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * 配方处理器：机器上「可以参与配方」的一个环节。
 *
 * <p>
 * 它<b>不限定内容种类</b>。支持哪些种类由 {@link #canHandleContent(ContentRecipeInfo)} 按内容键逐个回答，
 * 因此一个 handler 可以同时支持多种，也可以一种都不支持——例如
 * {@code NotifiableEnergyContainer}、{@code NotifiableComputationContainer} 就是只承载能量 / 算力能力、
 * 不处理任何内容的 handler。{@link RecipeHandlerUnit} 在组装时会用这些回答建立「内容键 → 支持的 handler」索引。
 *
 * <h2>通用内容路径与物品 / 流体的专用方法</h2>
 * <p>
 * 凡是内容都走以 {@link ContentRecipeInfo} 为键的通用方法（{@link #canHandleContent}、
 * {@link #handleRecipeContent}、{@link #forEachContent} 等）；物品与流体另外有各自的一组专用方法
 * （{@link #canHandleItem()}、{@link #forEachItems}、{@link #handleRecipeItem}……），
 * 热路径直接调它们，省掉键比较与装箱。实现类通常只需实现 {@link IItemRecipeHandler} 或
 * {@link IFluidRecipeHandler}，由这两个子接口把通用调用桥接到专用方法。
 *
 * <h2>约定</h2>
 * <ul>
 * <li>以 {@code key == null} 调用表示「任意内容类型是否处理」，用于构建处理器分组；</li>
 * <li>返回 {@code boolean} 的遍历方法中，{@code true} 表示<b>中断</b>遍历，
 * 即不再询问后续元素（以及后续 handler）；</li>
 * <li>{@code simulate} 为 {@code true} 时只允许读取，不得改动任何存储。</li>
 * </ul>
 */
public interface IRecipeHandler extends IFilteredHandler {

    /**
     * 遍历该内容类型下本 handler 持有的全部条目。
     *
     * @param key      内容类型键，决定 {@code T} 的实际类型（物品为 {@link ItemStack}，流体为 {@link FluidStack}）
     * @param function 回调，返回 {@code true} 表示中断本次遍历
     * @return 是否中断；{@code false} 同时也表示本 handler 不支持这种内容
     */
    default <T, C extends ContentInner<T>> boolean forEachContent(@NotNull ContentRecipeInfo<T, C> key, ObjLongPredicate<T> function) {
        return false;
    }

    /**
     * {@link #forEachContent} 的快速版本：回调不返回中断标志，因此无法提前结束遍历。
     *
     * <p>
     * 适用于只做累加、不需要短路的热路径（并行数计算、搜索索引构建等）。
     *
     * @param key      内容类型键
     * @param function 回调，只接收元素与数量
     */
    default <T, C extends ContentInner<T>> void fastForEachContent(@NotNull ContentRecipeInfo<T, C> key, ObjLongConsumer<T> function) {}

    /**
     * 返回本 handler 的配方搜索索引：内容 id → 可用数量。
     *
     * <p>
     * 该索引用于在 {@link GTRecipeType} 的全部配方中快速筛出「材料可能凑得齐」的候选，
     * 避免逐条配方做完整匹配。不需要参与检索的 handler 可以返回
     * {@link IntLongMap#EMPTY}（默认行为）。
     *
     * <p>
     * 实现通常会把结果缓存起来，仅在库存变化后重建。
     *
     * @param type 目标配方类型，不同配方类型可能需要填不同的索引内容
     * @return 可复用的索引对象；返回 {@link IntLongMap#EMPTY} 表示不参与检索
     */
    default IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        return IntLongMap.EMPTY;
    }

    /**
     * Merge this handler's search map into the given target map.
     * Default implementation accumulates via {@link IntLongMap#addTo} (i.e. adds amounts
     * to already-present keys). Handlers that only need to report one shared count for a key
     * (e.g. to avoid double-counting virtual items) may override with {@code put} semantics.
     */
    default void addToSearchMap(@NotNull IntLongMap target, @NotNull GTRecipeType type) {
        getSearchMap(type).addTo(target);
    }

    /**
     * 本 handler 是否参与配方。
     *
     * <p>
     * 返回 {@code false} 的 handler 会在机器组装处理器分组时被直接跳过
     * （例如机器为占位而创建的零容量槽位），既不计入并行数，也不参与匹配。
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 本 handler 的内容是否是<b>非消耗品</b>（催化剂）。
     *
     * <p>
     * 为 {@code true} 时：模拟阶段仍然会被查询（配方要能匹配上它），但真正执行阶段会被跳过，
     * 因此它的内容永远不会被扣掉。典型例子是集成电路槽——电路参与匹配配方，但不会被消耗。
     */
    default boolean isNotConsumable() {
        return false;
    }

    /**
     * 本 handler 是否只能在<b>存在真实配方</b>时工作。
     *
     * <p>
     * 为 {@code true} 时，那些不携带配方的纯内容操作（例如手动向机器塞物品 / 抽产物，
     * 内部以 {@link GTRecipe#EMPTY} 调用）会跳过本 handler。
     */
    default boolean isOnlyRecipe() {
        return false;
    }

    /**
     * 本 handler 是否处理给定种类的配方内容。
     *
     * @param key 内容类型键；传 {@code null} 表示「任意内容类型是否处理」，用于构建处理器分组
     * @return 是否处理；默认 {@code false}
     */
    default <T, C extends ContentInner<T>> boolean canHandleContent(@Nullable ContentRecipeInfo<T, C> key) {
        return false;
    }

    /**
     * 本 handler 在<b>输出</b>方向上的容量是否可视为无限。
     *
     * <p>
     * 它是 {@link #isInfiniteItemCapacity()}、{@link #isInfiniteFluidCapacity()} 按内容键的推广形式，
     * 供支持其它内容种类的实现声明同样的语义。{@link RecipeHandlerUnit} 聚合的是物品 / 流体那两个专用判断
     * （见其 {@code isInfiniteItemCapacity} 字段）。
     *
     * @param key 内容类型键；传 {@code null} 表示「任意内容类型」
     */
    default <T, C extends ContentInner<T>> boolean isInfiniteCapacity(@Nullable ContentRecipeInfo<T, C> key) {
        return false;
    }

    /**
     * 按给定内容类型处理一批配方内容。
     *
     * <p>
     * 与快速路径的 {@link #handleRecipeItem} / {@link #handleRecipeFluid} 语义一致：
     * 要么整体成功，要么整体失败，不存在「成功一半」的返回方式。
     *
     * @param key      内容类型键，不可为 {@code null}
     * @param io       方向，必须与本 handler 的 IO 一致，否则实现方会抛出异常
     * @param recipe   源配方；纯内容操作时传入 {@link GTRecipe#EMPTY}
     * @param contents 待处理内容，数量已按并行数放大
     * @param simulate {@code true} 表示只做试算，不得改动存储
     * @return 是否处理成功；默认 {@code false}
     */
    default <T, C extends ContentInner<T>> boolean handleRecipeContent(@NotNull ContentRecipeInfo<T, C> key, IO io, GTRecipe recipe, List<Content<C>> contents, boolean simulate) {
        return false;
    }

    // ===== 物品 / 流体的快速路径 =====
    // 这些方法只服务于性能敏感的热路径，语义与上面以 ContentRecipeInfo 为键的通用方法一一对应。
    // 新内容类型不需要实现它们，交给 IItemRecipeHandler / IFluidRecipeHandler 桥接即可。

    /**
     * 本 handler 是否处理物品。
     *
     * <p>
     * 物品的专用方法（{@link #forEachItems}、{@link #handleRecipeItem} 等）以它为开关；
     * 通用路径则通过 {@link #canHandleContent} 传入物品键来判断。
     */
    default boolean canHandleItem() {
        return false;
    }

    /** 物品版的 {@link #forEachContent}。 */
    default boolean forEachItems(ObjLongPredicate<ItemStack> function) {
        return false;
    }

    /** 物品版的 {@link #fastForEachContent}。 */
    default void fastForEachItems(ObjLongConsumer<ItemStack> function) {}

    /**
     * 物品版的 {@link #handleRecipeContent}。
     *
     * <p>
     * 注意两者并不完全等价：{@link RecipeHandlerUnit#handleRecipeItem} 额外做了
     * 「输出方向 + 模拟 + 容量无限」的短路，以及 {@link GTRecipe#EMPTY} 时跳过
     * {@link #isOnlyRecipe()} 成员的过滤，而通用路径没有这两项。
     */
    default boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        return false;
    }

    /** 物品版的 {@link #isInfiniteCapacity}。 */
    default boolean isInfiniteItemCapacity() {
        return false;
    }

    /**
     * 本 handler 是否处理流体。
     *
     * <p>
     * 流体的专用方法（{@link #forEachFluids}、{@link #handleRecipeFluid} 等）以它为开关；
     * 通用路径则通过 {@link #canHandleContent} 传入流体键来判断。
     */
    default boolean canHandleFluid() {
        return false;
    }

    /** 流体版的 {@link #forEachContent}。 */
    default boolean forEachFluids(ObjLongPredicate<FluidStack> function) {
        return false;
    }

    /** 流体版的 {@link #fastForEachContent}。 */
    default void fastForEachFluids(ObjLongConsumer<FluidStack> function) {}

    /** 流体版的 {@link #handleRecipeItem}，与通用路径的差异同上。 */
    default boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
        return false;
    }

    /** 流体版的 {@link #isInfiniteCapacity}。 */
    default boolean isInfiniteFluidCapacity() {
        return false;
    }
}
