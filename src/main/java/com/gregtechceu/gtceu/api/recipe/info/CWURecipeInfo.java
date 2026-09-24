package com.gregtechceu.gtceu.api.recipe.info;

/**
 * 计算功（CWU）这一「内容种类」的元信息。
 *
 * <p>
 * 与 {@link EURecipeInfo} 一样属于纯数值型能力：直接继承 {@link RecipeInfo}，
 * 不参与内容处理器的分组与检索，由配方执行流程单独结算。
 * 常见于研究站等需要消耗计算量的配方。
 *
 * <p>
 * {@code doRenderSlot} 为 {@code false}，表示它在配方界面中不占用槽位。
 */
public final class CWURecipeInfo extends RecipeInfo {

    /** 全局唯一实例，注册名为 {@code cwu}。 */
    public final static CWURecipeInfo INSTANCE = new CWURecipeInfo();

    private CWURecipeInfo() {
        super("cwu", 0xFFEEEE00, false, 3);
    }
}
