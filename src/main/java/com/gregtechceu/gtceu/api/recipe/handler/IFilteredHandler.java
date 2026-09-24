package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.NumberInputFancyConfigurator;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 配方参与者的两个公共属性：一个筛选判定钩子（{@link #test}）与一个优先级（{@link #getPriority}）。
 *
 * <p>
 * 作为 {@link IRecipeHandler} 的顶层父接口。{@link #getPriority()} 决定同一分组内成员的询问顺序：
 * {@code RecipeHandlerUnit} 构造时按 {@link #PRIORITY_COMPARATOR} 排序成员，之后各处理循环都是
 * 「第一个成功即返回」，因此优先级高的成员会先被用到；多方块机器还可以通过
 * {@link #createPriorityConfigurator} 把它开放给玩家调整。
 *
 * <p>
 * {@link #test} 是留给筛选逻辑的钩子，由调用方决定如何解释「这个材料能不能接受」。
 */
public interface IFilteredHandler {

    /** 按优先级从高到低排序；{@code priority} 越大越靠前。 */
    Comparator<IFilteredHandler> PRIORITY_COMPARATOR = Comparator.comparingInt(h -> -h.getPriority());

    /** 最高优先级，取 {@link Integer#MAX_VALUE}。 */
    int HIGHEST = Integer.MAX_VALUE;
    /** 高优先级，取 {@link Integer#MAX_VALUE} 的一半。 */
    int HIGH = Integer.MAX_VALUE / 2;
    /** 默认优先级。 */
    int NORMAL = 0;
    /** 低优先级，取 {@link Integer#MIN_VALUE} 的一半。 */
    int LOW = Integer.MIN_VALUE / 2;
    /** 最低优先级，取 {@link Integer#MIN_VALUE}。 */
    int LOWEST = Integer.MIN_VALUE;

    /**
     * 创建一个用于在 GUI 中调整优先级的配置面板。
     *
     * @param get 读取当前优先级
     * @param set 写回新优先级
     */
    static NumberInputFancyConfigurator<Integer> createPriorityConfigurator(Supplier<Integer> get, Consumer<Integer> set) {
        var configurator = new NumberInputFancyConfigurator<>(new IntInputWidget(Position.ORIGIN, get, set).setMin(Integer.MIN_VALUE));
        configurator.setTitle(Component.translatable("gui.ae2.Priority"));
        configurator.setTabTooltips(Collections.singletonList(Component.translatable("gui.ae2.Priority")));
        configurator.setTabIcon(() -> new ItemStackTexture(GTItems.TAG_FILTER.asItem()));
        return configurator;
    }

    /**
     * Test an ingredient for filtering &amp; priority.
     * 
     * @param ingredient the ingredient
     * @return {@code true} if the input argument matches the predicate,
     *         otherwise {@code false}
     */
    default boolean test(Object ingredient) {
        return true;
    }

    /**
     * The priority of this recipe handler.
     */
    default int getPriority() {
        return NORMAL;
    }
}
