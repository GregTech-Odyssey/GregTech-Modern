package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import java.util.function.BiPredicate;

/**
 * 能自行<b>凭空造出配方</b>的机器（例如矿物处理、泵、研究站这类「无配方表配方」的机器）。
 *
 * <p>
 * 相比 {@link IRecipeHandlerHolder#findRecipe} 的常规流程（从配方表里检索），本接口允许机器
 * 直接根据自身状态生成一个 {@link GTRecipeDefinition}。检索时先问自定义配方，再回退到常规检索，
 * 见 {@link #findRecipe}。
 */
public interface ICustomRecipeLogicHolder extends IRecipeHandlerHolder {

    /**
     * 为该处理器分组生成一个自定义配方。
     *
     * @param unit 输入处理器分组；机器没有任何输入单元时传入 {@link RecipeHandlerUnit#NO_DATA}
     * @return 生成的配方，返回 {@code null} 表示这次生成不出配方
     */
    GTRecipeDefinition createCustomRecipe(RecipeHandlerUnit unit);

    /**
     * 自定义配方之外，是否还要继续走常规的配方表检索。
     *
     * @return {@code true} 表示生成失败后仍会检索配方表；默认 {@code false} 表示完全由本机器接管
     */
    default boolean searchRecipe() {
        return false;
    }

    /**
     * 检索顺序：先自定义配方，{@link #searchRecipe()} 为 {@code true} 时再依次尝试配方表检索与
     * {@link GTRecipeType#getCustomRecipeLogicRunners()} 提供的额外逻辑。
     *
     * <p>
     * 与父接口不同的是，机器连一个输入单元都没有时（{@code inputs} 为空）也会尝试用
     * {@link RecipeHandlerUnit#NO_DATA} 生成配方，而不是直接失败。
     */
    @Override
    default boolean findRecipe(GTRecipeType type, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var inputs = this.getInputUnits();
        if (inputs.isEmpty()) {
            var r = this.createCustomRecipe(RecipeHandlerUnit.NO_DATA);
            return r != null && canHandle.test(RecipeHandlerUnit.NO_DATA, r);
        } else {
            var customRecipeLogic = type.getCustomRecipeLogicRunners();
            var hasCustomRecipeLogic = !customRecipeLogic.isEmpty();
            var searchRecipe = searchRecipe();
            for (var unit : inputs) {
                var r = this.createCustomRecipe(unit);
                if (r != null && canHandle.test(unit, r)) return true;
                if (searchRecipe) {
                    if (unit.findRecipe(type, canHandle)) return true;
                    if (hasCustomRecipeLogic) {
                        for (var logic : customRecipeLogic) {
                            r = logic.createCustomRecipe(this, unit);
                            if (r != null && canHandle.test(unit, r)) return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
