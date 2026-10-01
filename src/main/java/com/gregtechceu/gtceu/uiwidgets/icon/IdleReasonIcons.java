package com.gregtechceu.gtceu.uiwidgets.icon;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IdleReasonIcons {

    private static final Map<String, IGuiTexture> ICONS = new ConcurrentHashMap<>();

    private static volatile Cached cached;

    static {
        register("gtceu.recipe_logic.no_recipe_found", WidgetIcons.IDLE_NO_RECIPE);
        register("behavior.prospector.not_enough_energy", WidgetIcons.IDLE_NO_POWER);
        register("gtceu.recipe_logic.insufficient_tier", WidgetIcons.IDLE_LOW_TIER);
        register("gtceu.recipe_logic.no_capabilities", WidgetIcons.IDLE_NO_CAPABILITY);
        register("gtceu.recipe_logic.condition_fails", WidgetIcons.IDLE_CONDITION);
        register("gtceu.top.maintenance_broken", WidgetIcons.STATUS_MAINTENANCE);
        register("gtceu.multiblock.universal.muffler_obstructed", WidgetIcons.STATUS_OBSTRUCTED);
        register("gtceu.multiblock.universal.rotor_obstructed", WidgetIcons.STATUS_OBSTRUCTED);
        register("gtceu.multiblock.universal.muffler_insufficient", WidgetIcons.STATUS_OBSTRUCTED);
        register("gtceu.recipe_logic.insufficient_in", WidgetIcons.IDLE_INPUT_SHORT);
        register("gtceu.recipe_logic.amount_not_enough", WidgetIcons.IDLE_INPUT_SHORT);
        register("gtceu.recipe_logic.unable_handle", WidgetIcons.IDLE_INPUT_SHORT);
        register("gtceu.recipe_logic.ordered_item", WidgetIcons.IDLE_INPUT_SHORT);
        register("gtceu.recipe_logic.ordered_fluid", WidgetIcons.IDLE_INPUT_SHORT);
        register("gtceu.recipe_logic.insufficient_out", WidgetIcons.IDLE_OUTPUT_FULL);
        register("gtceu.recipe_logic.insufficient_fuel", WidgetIcons.IDLE_NO_FUEL);
        register("gtceu.multiblock.computation.not_enough_computation", WidgetIcons.IDLE_NO_COMPUTATION);
        register("gtceu.issue.eu_short", WidgetIcons.IDLE_NO_POWER);
        register("gtceu.issue.rotor_missing", WidgetIcons.IDLE_NO_CAPABILITY);
        register("gtceu.issue.output_power_low", WidgetIcons.IDLE_LOW_TIER);
        register("gtceu.issue.start_energy_short", WidgetIcons.IDLE_NO_POWER);
        register("gtceu.issue.start_energy_capacity", WidgetIcons.IDLE_LOW_TIER);
        register("gtceu.issue.intake_obstructed", WidgetIcons.STATUS_OBSTRUCTED);
        register("gtceu.issue.no_lubricant", WidgetIcons.IDLE_INPUT_SHORT);
        register("gtceu.issue.vent_blocked", WidgetIcons.STATUS_OBSTRUCTED);
        register("gtceu.issue.steam_short", WidgetIcons.IDLE_NO_FUEL);
        register("gtceu.issue.no_sunlight", WidgetIcons.IDLE_WAITING);
        register("gtceu.issue.not_applicable", WidgetIcons.IDLE_NO_RECIPE);
        register("gtceu.issue.no_energy_hatch", WidgetIcons.IDLE_NO_CAPABILITY);
        register("gtceu.issue.miner_done", WidgetIcons.STATUS_INFO);
        register("gtceu.issue.low_power", WidgetIcons.IDLE_NO_POWER);
    }

    private IdleReasonIcons() {}

    public static void register(String translationKey, IGuiTexture icon) {
        ICONS.put(translationKey, icon);
        cached = null;
    }

    public static IGuiTexture iconFor(int status, @Nullable Component reason) {
        if (status == RecipeLogic.SUSPEND) return WidgetIcons.STATUS_DISABLED;
        if (IdleReasonInfo.isNone(reason)) return status == RecipeLogic.WAITING ? WidgetIcons.IDLE_WAITING : WidgetIcons.IDLE_NO_RECIPE;
        return iconFor(reason);
    }

    public static IGuiTexture iconFor(@Nullable Component reason) {
        if (reason == null) return WidgetIcons.IDLE_WAITING;
        var last = cached;
        if (last != null && last.reason == reason) return last.icon;
        var icon = resolve(reason);
        cached = new Cached(reason, icon);
        return icon;
    }

    private static IGuiTexture resolve(Component reason) {
        var key = IdleReasonInfo.keyOf(reason);
        if (key == null) return WidgetIcons.IDLE_WAITING;
        if (IdleReasonInfo.isEnergyShort(reason)) return WidgetIcons.IDLE_NO_POWER;
        var icon = ICONS.get(key);
        if (icon != null) return icon;
        var entry = IdleReasonInfo.get(key);
        if (entry == null) return WidgetIcons.IDLE_WAITING;
        return switch (entry.severity()) {
            case INFO -> WidgetIcons.STATUS_INFO;
            case WARNING -> WidgetIcons.IDLE_WAITING;
            case BLOCKING -> WidgetIcons.IDLE_CONDITION;
        };
    }

    private record Cached(Component reason, IGuiTexture icon) {}
}
