package com.gregtechceu.gtceu.uiwidgets.icon;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

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
    }

    private IdleReasonIcons() {}

    public static void register(String translationKey, IGuiTexture icon) {
        ICONS.put(translationKey, icon);
        cached = null;
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
        if (!(reason.getContents() instanceof TranslatableContents contents)) return WidgetIcons.IDLE_WAITING;
        var key = contents.getKey();
        if ("gtceu.recipe_logic.insufficient_in".equals(key) && mentionsEnergy(reason)) return WidgetIcons.IDLE_NO_POWER;
        return ICONS.getOrDefault(key, WidgetIcons.IDLE_WAITING);
    }

    private static boolean mentionsEnergy(Component component) {
        for (var sibling : component.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents contents && "recipe.capability.eu.name".equals(contents.getKey())) return true;
            if (mentionsEnergy(sibling)) return true;
        }
        return false;
    }

    private record Cached(Component reason, IGuiTexture icon) {}
}
