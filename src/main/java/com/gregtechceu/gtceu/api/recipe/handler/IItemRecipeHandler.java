package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * 处理<b>物品</b>内容的配方处理器。
 *
 * <p>
 * 继承 {@link IRecipeHandler} 后把物品相关的通用调用桥接到快速路径：凡是 {@code key} 为
 * {@link ItemRecipeInfo#INSTANCE} 的 {@code *Content} 调用，都会转发到本接口的物品方法上；
 * 其余内容类型一律返回「未处理」。因此实现类只要实现下面三个物品方法，就自动获得了完整的通用接口。
 *
 * <p>
 * {@link #canHandleItem()}、{@link #canHandleContent}、{@link #isInfiniteCapacity} 都已有默认实现，
 * 其中 {@link #isInfiniteCapacity} 会把容量是否无限的判断委托给 {@link #isInfiniteItemCapacity()}。
 *
 * <p>
 * 典型实现见 {@code NotifiableItemStackHandler}。
 */
public interface IItemRecipeHandler extends IRecipeHandler {

    /**
     * 遍历本 handler 持有的全部物品。
     *
     * @param function 回调（物品堆、数量），返回 {@code true} 表示中断遍历
     * @return 是否中断
     */
    @Override
    boolean forEachItems(ObjLongPredicate<ItemStack> function);

    /**
     * {@link #forEachItems} 的快速版本：回调不返回中断标志。
     *
     * @param function 回调（物品堆、数量）
     */
    @Override
    void fastForEachItems(ObjLongConsumer<ItemStack> function);

    /**
     * 按给定方向处理一批物品内容：要么整体成功、要么整体失败。
     *
     * @param io       方向；{@code IO.IN} 消耗材料、{@code IO.OUT} 产出物品
     * @param recipe   源配方；纯内容操作时传入 {@link GTRecipe#EMPTY}
     * @param items    待处理内容，数量已按并行数放大
     * @param simulate {@code true} 表示只做试算，不得改动存储
     * @return 是否处理成功
     */
    @Override
    boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate);

    /** 本接口恒为 {@code true}，见 {@link IItemRecipeHandler}。 */
    @Override
    default boolean canHandleItem() {
        return true;
    }

    /**
     * 默认只认 {@link ItemRecipeInfo}：{@code null}（任意）与物品键返回 {@code true}，其余键返回 {@code false}。
     *
     * <p>
     * 需要同时支持多种内容种类的实现可以覆写本方法。
     */
    @Override
    default <T, C extends ContentInner<T>> boolean canHandleContent(@Nullable ContentRecipeInfo<T, C> key) {
        return key == null || key == ItemRecipeInfo.INSTANCE;
    }

    /**
     * 把「容量是否无限」的判断收敛到 {@link IRecipeHandler#isInfiniteItemCapacity()}：
     * 只有物品键（或 {@code null}）才会被认领，其他内容类型一律为 {@code false}。
     */
    @Override
    default <T, C extends ContentInner<T>> boolean isInfiniteCapacity(@Nullable ContentRecipeInfo<T, C> key) {
        if (isInfiniteItemCapacity()) return key == null || key == ItemRecipeInfo.INSTANCE;
        return false;
    }

    /** 物品键的通用遍历，桥接到 {@link #forEachItems}。 */
    @Override
    @SuppressWarnings("unchecked") // the key identity guarantees T == ItemStack / C == ItemIngredient here
    default <T, C extends ContentInner<T>> boolean forEachContent(@NotNull ContentRecipeInfo<T, C> key, ObjLongPredicate<T> function) {
        if (key == ItemRecipeInfo.INSTANCE) return forEachItems((ObjLongPredicate<ItemStack>) function);
        return false;
    }

    /** 物品键的通用快速遍历，桥接到 {@link #fastForEachItems}。 */
    @Override
    @SuppressWarnings("unchecked")
    default <T, C extends ContentInner<T>> void fastForEachContent(@NotNull ContentRecipeInfo<T, C> key, ObjLongConsumer<T> function) {
        if (key == ItemRecipeInfo.INSTANCE) fastForEachItems((ObjLongConsumer<ItemStack>) function);
    }

    /** 物品键的通用处理入口，桥接到 {@link #handleRecipeItem}。 */
    @Override
    @SuppressWarnings("unchecked")
    default <T, C extends ContentInner<T>> boolean handleRecipeContent(@NotNull ContentRecipeInfo<T, C> key, IO io, GTRecipe recipe, List<Content<C>> contents, boolean simulate) {
        if (key == ItemRecipeInfo.INSTANCE) return handleRecipeItem(io, recipe, (List<Content<ItemIngredient>>) (List<?>) contents, simulate);
        return false;
    }
}
