package com.gregtechceu.gtceu.api.recipe.info;

/**
 * 电能（EU）这一「内容种类」的元信息。
 *
 * <p>
 * 它是纯数值型能力，不是可搬运的内容，因此直接继承 {@link RecipeInfo} 而不是
 * {@link ContentRecipeInfo}，也就不会出现在内容处理器分组里——能量的匹配与扣除由
 * 配方执行流程单独处理（见 {@code matchTickRecipe} / {@code handleTickRecipe}）。
 *
 * <p>
 * {@code doRenderSlot} 为 {@code false}，表示它在配方界面中不占用槽位。
 */
public final class EURecipeInfo extends RecipeInfo {

    /** 全局唯一实例，注册名为 {@code eu}。 */
    public final static EURecipeInfo INSTANCE = new EURecipeInfo();

    private EURecipeInfo() {
        super("eu", 0xFFFFFF00, false, 2);
    }
}
