package com.gregtechceu.gtceu.api.recipe.extension;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.IRecipeInfo;

import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
/**
 * 配方扩展：给配方挂上一份自定义数据，并定义这份数据如何参与配方流程。
 *
 * <p>
 * 它自身就是一个数据键（{@link DataComponentKey}），值以「自己」为键存放在
 * {@link GTRecipeDefinition#data} / {@link GTRecipe#data} 中，因此每条配方都能有独立取值。
 * 典型例子是 {@link CWUTRecipeExtension}：它把「每 tick 消耗多少算力」塞进配方，
 * 再把这份数据接进 tick 结算与并行数计算。
 *
 * <p>
 * 用不到的回调留默认实现即可，需要参与的地方有：
 * <ul>
 * <li>{@link #handleInput} / {@link #handleOutput}——匹配与执行输入 / 输出时调用，
 * 返回 {@code false} 表示这条配方不可用（模拟时）或执行失败（真执行时）；</li>
 * <li>{@link #handleTick}——每 tick 的持续结算；</li>
 * <li>{@link #getParallel} / {@link #setParallel}——参与并行数计算与随并行放大；</li>
 * <li>{@link #extractInput}——往配方检索索引里加料；</li>
 * <li>从 {@link IRecipeInfo} 继承的 {@code getTooltips} / {@code addInfo} / {@code getInfoHeight}——
 * 在配方界面上补一段说明。</li>
 * </ul>
 *
 * <p>
 * {@link #isTick} 在构造时确定，并决定它被归到哪一边：
 * {@code true} 进 {@link GTRecipeDefinition#tickRecipeExtensions}，走 {@link #handleTick}；
 * {@code false} 进 {@link GTRecipeDefinition#recipeExtensions}，在输入输出匹配时结算。
 * 两者的并行数分别计算，但都会在配方按并行放大时收到 {@link #setParallel}。
 *
 * @param <T> 该扩展携带的数据类型
 */
public abstract class RecipeExtension<T> extends DataComponentKey<T> implements IRecipeInfo {

    /** 是否为每 tick 结算的扩展，构造时确定，之后不变。 */
    public final boolean isTick;

    public RecipeExtension(String name, DataSyncCodec<T> codec, boolean isTick) {
        super(name, codec);
        this.isTick = isTick;
    }

    /**
     * 匹配或执行输入时调用。
     *
     * <p>
     * {@code simulate} 为 {@code true}（匹配阶段）时返回 {@code false} 表示这条配方不可用；
     * 为 {@code false}（真正执行）时返回 {@code false} 表示执行失败。
     *
     * @return 是否通过；默认 {@code true}（不参与输入判定）
     */
    @SuppressWarnings("unused")
    public boolean handleInput(@NotNull IRecipeHandlerHolder holder, @NotNull RecipeHandlerUnit unit, @NotNull GTRecipe recipe, boolean simulate) {
        return true;
    }

    /**
     * 模拟或执行产出时调用。
     *
     * <p>
     * 注意两者的失败处理不同：模拟阶段（{@code matchRecipeOutput}）遇到 {@code false} 立即失败；
     * 实际产出阶段（{@code handleRecipeOutput}）会把所有扩展都调用一遍再合并结果，
     * 只要有一个返回 {@code false} 整体就算失败。
     *
     * @return 是否通过；默认 {@code true}
     */
    @SuppressWarnings("unused")
    public boolean handleOutput(@NotNull IRecipeHandlerHolder holder, @NotNull GTRecipe recipe, boolean simulate) {
        return true;
    }

    /**
     * 每 tick 的持续结算（能量、算力等）。{@link #isTick} 为 {@code false} 的扩展不会被调用。
     *
     * @param simulate {@code true} 表示只试算，不得真正扣资源
     * @return 是否通过；默认 {@code true}
     */
    public boolean handleTick(@NotNull IRecipeHandlerHolder holder,
                              @NotNull GTRecipe recipe, boolean simulate) {
        return true;
    }

    /**
     * 把本扩展对配方的「输入需求」写进配方检索索引，供 {@code RecipeDB} 预先筛掉不可能的配方。
     *
     * @param map 内容 id → 数量的索引，就地累加；不需要参与检索就留空实现
     */
    public abstract void extractInput(GTRecipeDefinition recipe, IntLongMap map);

    /**
     * 按自身资源上限收紧并行数。
     *
     * @param parallel 上游已算出的并行数上限
     * @return 收紧后的并行数；返回 {@code 0} 表示当前并行不了（调用方会据此判定配方不可用）
     */
    public abstract long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit,
                                     GTRecipe recipe, long parallel);

    /**
     * 配方按 {@code parallel} 倍放大时，把本扩展携带的数据一起放大。
     *
     * <p>
     * 由 {@link GTRecipe#modifier} 调用；不需要放大就留空实现。
     */
    public abstract void setParallel(GTRecipe recipe, long parallel);
}
