package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * @param isSuccess is action success
 * @param reason    if fail, fail reason
 */
public record ActionResult(boolean isSuccess, @Nullable Component reason) {

    public final static ActionResult SUCCESS = new ActionResult(true, null);
    public final static ActionResult FAIL_NO_REASON = new ActionResult(false, null);
    public final static ActionResult PASS_NO_CONTENTS = new ActionResult(true,
            Component.translatable("gtceu.recipe_logic.no_contents"));
    public final static ActionResult FAIL_NO_RECIPE_FOUND = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.no_recipe_found"));
    public final static ActionResult FAIL_NO_CAPABILITIES = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.no_capabilities"));
    public final static ActionResult FAIL_INSUFFICIENT_OUT = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.insufficient_out"));
    public final static ActionResult FAIL_INSUFFICIENT_FUEL = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.insufficient_fuel"));
    public static final ActionResult FAIL_INSUFFICIENT_TIER = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.insufficient_tier"));

    public static final ActionResult FAIL_MAINTENANCE_BROKEN = new ActionResult(false,
            Component.translatable("gtceu.top.maintenance_broken"));
    public static final ActionResult FAIL_MUFFLER_OBSTRUCTED = new ActionResult(false,
            Component.translatable("gtceu.multiblock.universal.muffler_obstructed"));
    public static final ActionResult FAIL_ROTOR_OBSTRUCTED = new ActionResult(false,
            Component.translatable("gtceu.multiblock.universal.rotor_obstructed"));

    public static final ActionResult FAIL_ORDERED_ITEM = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.ordered_item"));

    public static final ActionResult FAIL_ORDERED_FLUID = new ActionResult(false,
            Component.translatable("gtceu.recipe_logic.ordered_fluid"));

    public static ActionResult fail(@Nullable Component component) {
        return new ActionResult(false, component);
    }

    public static ActionResult failCondition(Component key) {
        return new ActionResult(false, Component.translatable("gtceu.recipe_logic.condition_fails").append(": ").append(key));
    }

    public static ActionResult failAmountNotEnough(Component key, long needed, long available) {
        return new ActionResult(false, Component.translatable("gtceu.recipe_logic.amount_not_enough", key, needed, available));
    }

    public static ActionResult failUnableHandle(Component key) {
        return new ActionResult(false, Component.translatable("gtceu.recipe_logic.unable_handle", key));
    }

    public static ActionResult failInsufficientIn(Component key) {
        return new ActionResult(false, Component.translatable("gtceu.recipe_logic.insufficient_in").append(": ").append(key));
    }

    public static <T extends ContentInner> ActionResult failInsufficientIn(Content<T> content) {
        return new ActionResult(false, Component.translatable("gtceu.recipe_logic.insufficient_in").append(": ").append(content.getName()));
    }

    public Component reason() {
        if (reason == null) return Component.empty();
        return reason;
    }
}
