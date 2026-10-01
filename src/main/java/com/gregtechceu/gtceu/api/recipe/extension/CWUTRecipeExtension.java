package com.gregtechceu.gtceu.api.recipe.extension;

import com.gregtechceu.gtceu.api.machine.feature.IComputationContainerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;

import com.gto.datasynclib.DataSyncCodec;
import com.gto.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;

/**
 * 计算功（CWU）扩展：给配方附上「每 tick 需要多少算力」。
 *
 * <p>
 * 它自己就是那个数据键——{@code GTRecipeDataKeys} 直接注册了本单例，
 * 所以 {@code recipe.data.getLong(GTRecipeDataKeys.CWUT)} 取到的就是本扩展的值。
 *
 * <p>
 * 这是个 <b>tick 扩展</b>（{@code isTick == true}）：每 tick 通过
 * {@link IComputationContainerMachine#requestCWU} 向机器索要算力，要不到就把停机原因写成
 * 「算力不足」并失败；并行数用机器能提供的总算力除以单份需求得出。它不参与配方检索索引
 * （{@link #extractInput} 是空实现）。
 */
public final class CWUTRecipeExtension extends RecipeExtension<Long> {

    /** 全局唯一实例，注册名为 {@code cwut}。 */
    public static final CWUTRecipeExtension INSTANCE = new CWUTRecipeExtension();

    private CWUTRecipeExtension() {
        super("cwut", DataSyncCodec.LONG_CODEC, true);
    }

    /**
     * 每 tick 索要算力：单份配方需求为 {@link GTRecipe#getInputCWUt()}。
     *
     * <p>
     * 需求为 0 时直接通过；机器不是 {@link IComputationContainerMachine} 时视为失败。
     */
    @Override
    public boolean handleTick(@NotNull IRecipeHandlerHolder holder, @NotNull GTRecipe recipe, boolean simulate) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return true;
        boolean result;
        if (holder instanceof IComputationContainerMachine machine) {
            result = machine.requestCWU(cwu, simulate) >= cwu;
        } else {
            result = false;
        }
        if (result) return true;
        holder.setIdleReason(() -> Component.translatable("gtceu.multiblock.computation.not_enough_computation"));
        return false;
    }

    /** 不参与配方检索索引，故留空。 */
    @Override
    public void extractInput(GTRecipeDefinition recipe, IntLongMap map) {}

    /**
     * 用机器可提供的总算力（{@code requestCWU(Long.MAX_VALUE, true)}）除以单份需求收紧并行数；
     * 机器不给算力时把停机原因写成「算力不足」并返回 {@code 0}。
     */
    @Override
    public long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long parallel) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return parallel;
        if (holder instanceof IComputationContainerMachine machine) {
            parallel = Math.min(parallel, machine.requestCWU(Long.MAX_VALUE, true) / cwu);
        } else {
            parallel = 0;
        }
        if (parallel > 0) return parallel;
        holder.setIdleReason(() -> Component.translatable("gtceu.multiblock.computation.not_enough_computation"));
        return 0;
    }

    /** 按并行数放大每 tick 的算力需求（{@code cwu * parallel}）。 */
    @Override
    public void setParallel(GTRecipe recipe, long parallel) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return;
        recipe.setCWUt(cwu * parallel);
    }

    /**
     * 在配方界面补说明：先是每 tick 的算力需求；若配方带有
     * {@code DURATION_IS_TOTAL_CWU} 标记（表示 {@code duration} 一栏代表总计算量），
     * 再补一行把它显示成总消耗。
     */
    @Override
    public void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        info.line("gtceu.recipe.info.cwut", () -> Component.literal(FormattingUtil.formatNumbers(recipe.data.getLong(GTRecipeDataKeys.CWUT)) + " CWU/t"));
        if (recipe.data.getBoolean(GTRecipeDataKeys.DURATION_IS_TOTAL_CWU)) {
            info.line("gtceu.recipe.info.total_cwu", () -> Component.literal(FormattingUtil.formatNumbers(recipe.duration) + " CWU"));
        }
    }
}
