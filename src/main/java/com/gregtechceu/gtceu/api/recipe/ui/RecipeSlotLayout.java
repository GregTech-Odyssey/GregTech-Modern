package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.uipro.UIElement;

/**
 * 配方类型的槽位区排布：把 {@link RecipeSlots} 给出的槽位和进度条摆成一块 {@link UIElement}。
 * <p>
 * 同一个排布既用于配方查看器（槽位绑定配方内容），也用于单方块机器界面（槽位绑定机器库存），
 * 所以排布只负责摆放，不关心槽里是什么；绑定由 {@link GTRecipeTypeUI} 在排布完成后按槽位 id 统一处理。
 * <p>
 * 默认排布见 {@code RecipeSlotLayouts#DEFAULT}（输入网格 → 进度箭头 → 输出网格）；
 * 带专用底图的配方类型（装配线、蒸馏塔……）用 {@link GTRecipeTypeUI#setSlotLayout} 换成自己的 Java 排布。
 * 排布里只能用 {@link RecipeSlots} 创建槽位和进度条，控件树必须只取决于配方类型（两端要一致）。
 */
@FunctionalInterface
public interface RecipeSlotLayout {

    UIElement build(RecipeSlots slots);
}
