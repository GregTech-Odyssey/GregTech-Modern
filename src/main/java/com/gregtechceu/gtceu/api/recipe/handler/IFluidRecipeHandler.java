package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * 处理<b>流体</b>内容的配方处理器。
 *
 * <p>
 * 与 {@link IItemRecipeHandler} 完全对称：凡是 {@code key} 为 {@link FluidRecipeInfo#INSTANCE} 的
 * {@code *Content} 调用都会转发到本接口的流体方法上，其余内容类型一律返回「未处理」。
 * 实现类只要实现下面三个流体方法，就自动获得了完整的通用接口。
 *
 * <p>
 * 典型实现见 {@code NotifiableFluidTank}。
 */
public interface IFluidRecipeHandler extends IRecipeHandler {

    /**
     * 遍历本 handler 持有的全部流体。
     *
     * @param function 回调（流体堆、数量），返回 {@code true} 表示中断遍历
     * @return 是否中断
     */
    @Override
    boolean forEachFluids(ObjLongPredicate<FluidStack> function);

    /**
     * {@link #forEachFluids} 的快速版本：回调不返回中断标志。
     *
     * @param function 回调（流体堆、数量）
     */
    @Override
    void fastForEachFluids(ObjLongConsumer<FluidStack> function);

    /**
     * 按给定方向处理一批流体内容：要么整体成功、要么整体失败。
     *
     * @param io       方向；{@code IO.IN} 消耗材料、{@code IO.OUT} 产出流体
     * @param recipe   源配方；纯内容操作时传入 {@link GTRecipe#EMPTY}
     * @param fluids   待处理内容，数量已按并行数放大
     * @param simulate {@code true} 表示只做试算，不得改动存储
     * @return 是否处理成功
     */
    @Override
    boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate);

    /** 本接口恒为 {@code true}，见 {@link IFluidRecipeHandler}。 */
    @Override
    default boolean canHandleFluid() {
        return true;
    }

    /**
     * 默认只认 {@link FluidRecipeInfo}：{@code null}（任意）与流体键返回 {@code true}，其余键返回 {@code false}。
     *
     * <p>
     * 需要同时支持多种内容种类的实现可以覆写本方法。
     */
    @Override
    default <T, C extends ContentInner<T>> boolean canHandleContent(@Nullable ContentRecipeInfo<T, C> key) {
        return key == null || key == FluidRecipeInfo.INSTANCE;
    }

    /**
     * 把「容量是否无限」的判断收敛到 {@link IRecipeHandler#isInfiniteFluidCapacity()}：
     * 只有流体键（或 {@code null}）才会被认领，其他内容类型一律为 {@code false}。
     */
    @Override
    default <T, C extends ContentInner<T>> boolean isInfiniteCapacity(@Nullable ContentRecipeInfo<T, C> key) {
        if (isInfiniteFluidCapacity()) return key == null || key == FluidRecipeInfo.INSTANCE;
        return false;
    }

    /** 流体键的通用遍历，桥接到 {@link #forEachFluids}。 */
    @Override
    @SuppressWarnings("unchecked") // the key identity guarantees T == FluidStack / C == FluidIngredient here
    default <T, C extends ContentInner<T>> boolean forEachContent(@NotNull ContentRecipeInfo<T, C> key, ObjLongPredicate<T> function) {
        if (key == FluidRecipeInfo.INSTANCE) return forEachFluids((ObjLongPredicate<FluidStack>) function);
        return false;
    }

    /** 流体键的通用快速遍历，桥接到 {@link #fastForEachFluids}。 */
    @Override
    @SuppressWarnings("unchecked")
    default <T, C extends ContentInner<T>> void fastForEachContent(@NotNull ContentRecipeInfo<T, C> key, ObjLongConsumer<T> function) {
        if (key == FluidRecipeInfo.INSTANCE) fastForEachFluids((ObjLongConsumer<FluidStack>) function);
    }

    /** 流体键的通用处理入口，桥接到 {@link #handleRecipeFluid}。 */
    @Override
    @SuppressWarnings("unchecked")
    default <T, C extends ContentInner<T>> boolean handleRecipeContent(@NotNull ContentRecipeInfo<T, C> key, IO io, GTRecipe recipe, List<Content<C>> contents, boolean simulate) {
        if (key == FluidRecipeInfo.INSTANCE) return handleRecipeFluid(io, recipe, (List<Content<FluidIngredient>>) (List<?>) contents, simulate);
        return false;
    }
}
