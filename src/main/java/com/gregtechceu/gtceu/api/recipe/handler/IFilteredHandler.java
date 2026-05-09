package com.gregtechceu.gtceu.api.recipe.handler;

import java.util.Comparator;

public interface IFilteredHandler {

    Comparator<IFilteredHandler> PRIORITY_COMPARATOR = Comparator
            .comparingInt(IFilteredHandler::getPriority).reversed();

    int HIGHEST = Integer.MAX_VALUE;
    int HIGH = Integer.MAX_VALUE / 2;
    int NORMAL = 0;
    int LOW = Integer.MIN_VALUE / 2;
    int LOWEST = Integer.MIN_VALUE;

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
