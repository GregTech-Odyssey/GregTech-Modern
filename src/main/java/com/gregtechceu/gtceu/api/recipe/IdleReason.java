package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.misc.ComponentSupplier;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class IdleReason extends ComponentSupplier {

    private static final List<IdleReason> VALUES = new ArrayList<>();

    public static final IdleReason AMOUNT_DETAILED = new IdleReason("gtceu.idle_reason.amount_detailed", "%s Requires %s, but only %s is available");
    public static final IdleReason INVALID_INPUT = new IdleReason("gtceu.idle_reason.invalid_input", "Invalid Input");
    public static final IdleReason NO_RECIPE_FOUND = new IdleReason("gtceu.idle_reason.no_recipe_found", "No Recipe Found");
    public static final IdleReason NO_CWU = new IdleReason("gtceu.multiblock.computation.not_enough_computation", null);
    public static final IdleReason NO_EU = new IdleReason("behavior.prospector.not_enough_energy", null);
    public static final IdleReason INSUFFICIENT_OUT = new IdleReason("gtceu.recipe_logic.insufficient_out", null);
    public static final IdleReason MAINTENANCE_BROKEN = new IdleReason("gtceu.top.maintenance_broken", null);
    public static final IdleReason MUFFLER_OBSTRUCTED = new IdleReason("gtceu.multiblock.universal.muffler_obstructed", null);
    public static final IdleReason INSUFFICIENT_TEMPERATURE = new IdleReason("gtceu.idle_reason.insufficient_temperature", "Insufficient Temperature");
    public static final IdleReason INSUFFICIENT_VOLTAGE_TIER = new IdleReason("gtceu.idle_reason.insufficient_voltage_tier", "Insufficient Voltage Tier");

    public static final IdleReason ORDERED_ITEM = new IdleReason("gtceu.idle_reason.ordered.item", "Item Ordered Not Satisfies");
    public static final IdleReason ORDERED_FLUID = new IdleReason("gtceu.idle_reason.ordered.fluid", "Fluid Ordered Not Satisfies");

    @Getter
    private final String en;

    public IdleReason(String key, String en) {
        super(key);
        this.en = en;
        VALUES.add(this);
    }

    public static void setIdleReason(Object machine, IdleReason reason, Object... args) {
        if (machine instanceof IRecipeLogicMachine recipeLogicMachine) recipeLogicMachine.getRecipeLogic().setIdleReason(reason.get(args));
    }

    public static void setIdleReason(Object machine, IdleReason reason) {
        if (machine instanceof IRecipeLogicMachine recipeLogicMachine) recipeLogicMachine.getRecipeLogic().setIdleReason(reason.get());
    }

    public static List<IdleReason> values() {
        return VALUES;
    }
}
