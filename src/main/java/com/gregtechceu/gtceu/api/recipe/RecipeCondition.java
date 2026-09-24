package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.IRecipeInfo;

/**
 * 配方触发条件：判断当前环境 / 机器状态是否允许这条配方运行。
 *
 * <p>
 * 匹配一条配方时会先检查它的全部条件（见 {@code IRecipeHandlerHolder#checkConditions}），
 * 全部通过才继续做输入输出匹配。条件可以按类型分组取「或」——见 {@link #isOr()}。
 *
 * <p>
 * 同时实现 {@link IRecipeInfo}，所以条件也能往配方界面上补说明文字（默认显示 {@code getTooltips()}）。
 */
public abstract class RecipeCondition implements IRecipeInfo {

    /** 是否反转判定结果：{@code true} 时 {@link #testCondition} 为假才算通过。 */
    protected final boolean isReverse;

    public RecipeCondition() {
        this(false);
    }

    public RecipeCondition(boolean isReverse) {
        this.isReverse = isReverse;
    }

    /**
     * 该类型的条件之间是否取「或」。
     *
     * @return {@code true} 表示同一条配方里本类型的多个条件只要满足一个即可；
     *         默认 {@code false}，即每个条件都必须满足
     */
    public boolean isOr() {
        return false;
    }

    /**
     * 检查条件是否通过，会套用 {@link #isReverse} 的反转。
     *
     * @return 是否通过
     */
    public boolean check(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        boolean test = testCondition(holder, unit, recipe);
        return test != isReverse;
    }

    /** 子类实现的原始判定，不需要自己处理反转。 */
    protected abstract boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe);
}
