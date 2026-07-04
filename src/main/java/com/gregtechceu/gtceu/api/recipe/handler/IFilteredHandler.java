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

public interface IFilteredHandler {

    Comparator<IFilteredHandler> PRIORITY_COMPARATOR = Comparator.comparingInt(h -> -h.getPriority());

    int HIGHEST = Integer.MAX_VALUE;
    int HIGH = Integer.MAX_VALUE / 2;
    int NORMAL = 0;
    int LOW = Integer.MIN_VALUE / 2;
    int LOWEST = Integer.MIN_VALUE;

    static NumberInputFancyConfigurator<Integer> createPriorityConfigurator(Supplier<Integer> get, Consumer<Integer> set) {
        var configurator = new NumberInputFancyConfigurator<>(new IntInputWidget(Position.ORIGIN, get, set).setMin(Integer.MIN_VALUE));
        configurator.setTitle(Component.translatable("gui.ae2.Priority"));
        configurator.setTabTooltips(Collections.singletonList(Component.translatable("gui.ae2.Priority")));
        configurator.setTabIcon(() -> new ItemStackTexture(GTItems.TAG_FILTER.asItem()));
        return configurator;
    }

    /**
     * Test an ingredient for filtering & priority.
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
