package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;

import net.minecraft.network.chat.Component;

/**
 * 能在配方页信息区显示内容的对象：配方条件、配方扩展、配方修饰器。
 */
public interface IRecipeInfo {

    /**
     * 最简单的写法：一句说明。{@link #appendInfo} 的默认实现把它作为一行整句追加；
     * 要显示"名称 …… 数值"或额外展示槽时改写 {@link #appendInfo}。
     *
     * @return 说明，为 null 时不显示
     */
    default Component getTooltips() {
        return null;
    }

    /**
     * 往配方页信息区追加内容（见 {@link RecipeInfoBuilder} 的约定：只按配方决定追加什么，数值与槽以 Supplier 传入）。
     */
    default void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        var tooltips = getTooltips();
        if (tooltips != null) info.sentence(tooltips);
    }
}
