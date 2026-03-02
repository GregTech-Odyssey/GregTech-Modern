package com.gregtechceu.gtceu.api.capability.recipe;

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
     * The priority of this recipe handler.
     */
    default int getPriority() {
        return NORMAL;
    }

    default boolean isAvailable() {
        return true;
    }

    default IO getHandlerIO() {
        return IO.NONE;
    }

    RecipeCapability<?> getCapability();
}
