package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一种<b>带内容</b>的配方内容种类（物品、流体……）。
 *
 * <p>
 * 在 {@link RecipeInfo}（注册名、颜色、排序等元信息）之上，它把「这种内容的条目长什么样」
 * 也一并描述出来：配方查看器如何把内容渲染成条目、用哪个控件显示槽位、如何填充提示信息。
 * 这样配方处理器与界面代码就不必为每种内容各写一套分支。
 *
 * @param <T> 该内容在运行时用于遍历与匹配的元素类型（物品为 {@code ItemStack}，流体为 {@code FluidStack}）
 * @param <C> 该内容在配方中的条目类型（{@code ItemIngredient} / {@code FluidIngredient}）
 */
public abstract class ContentRecipeInfo<T, C extends ContentInner<T>> extends RecipeInfo {

    protected ContentRecipeInfo(String name, int color, boolean doRenderSlot, int sortIndex) {
        super(name, color, doRenderSlot, sortIndex);
    }

    /**
     * 把配方内容转成配方查看器用的条目列表，一个元素对应一个槽位。
     *
     * <p>
     * 条目可以是单个物品/流体，也可以是标签列表或多选循环列表；
     * 各实现会把不足 {@code recipeType.getMaxInputs/getMaxOutputs} 的槽位补成 {@code null}。
     *
     * @param io 方向，决定条目按输入还是输出渲染
     */
    @NotNull
    public abstract List<Object> createXEIContainerContents(List<Content<C>> contents, GTRecipeDefinition recipe, IO io);

    /**
     * 把 {@link #createXEIContainerContents} 产出的条目列表包装成查看器可用的 handler。
     *
     * @param contents 条目列表，元素类型必须与实现约定的一致
     */
    @Nullable
    public abstract Object createXEIContainer(List<?> contents);

    /**
     * 创建该内容种类的槽位控件。
     *
     * @return 控件；返回 {@code null} 表示该种类不提供槽位
     */
    @Nullable("null when getWidgetClass() == null")
    public abstract Widget createWidget();

    /**
     * Return the class of the supported widget that should be used to display this capability.
     */
    @Nullable
    public abstract Class<? extends Widget> getWidgetClass();

    /**
     * 把具体某个槽位的内容信息应用到控件上：数量、产出概率、提示文本等。
     *
     * @param content 该槽位对应的内容；{@code null} 表示这是个空槽位
     * @param storage 该槽位绑定的实际存储，可为 {@code null}（例如纯展示的配方查看器）
     */
    public abstract void applyWidgetInfo(@NotNull Widget widget,
                                         int index,
                                         boolean isXEI,
                                         IO io,
                                         @Nullable("null when storage == null") GTRecipeTypeUI.RecipeHolder recipeHolder,
                                         @NotNull GTRecipeType recipeType,
                                         @Nullable("null when content == null") GTRecipeDefinition recipe,
                                         @Nullable Content<C> content,
                                         @Nullable Object storage, int recipeTier, int chanceTier);
}
