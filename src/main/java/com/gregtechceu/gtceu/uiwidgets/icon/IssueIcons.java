package com.gregtechceu.gtceu.uiwidgets.icon;

import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.issue.IssueCategory;
import com.gregtechceu.gtceu.api.machine.issue.IssueLines;
import com.gregtechceu.gtceu.api.machine.issue.IssueSeverity;
import com.gregtechceu.gtceu.api.machine.issue.IssueSnapshot;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IssueIcons {

    private static final Map<IssueType, IGuiTexture> ICONS = new ConcurrentHashMap<>();
    private static final Map<String, IGuiTexture> CUSTOM_ICONS = new ConcurrentHashMap<>();
    @Nullable
    private static volatile CustomCache customCache;

    static {
        register(GTIssues.NO_RECIPE, WidgetIcons.IDLE_NO_RECIPE);
        register(GTIssues.NOT_APPLICABLE, WidgetIcons.IDLE_NO_RECIPE);
        register(GTIssues.NO_CAPABILITIES, WidgetIcons.IDLE_NO_CAPABILITY);
        register(GTIssues.LOW_TIER, WidgetIcons.IDLE_LOW_TIER);
        register(GTIssues.CONDITION, WidgetIcons.IDLE_CONDITION);
        register(GTIssues.INSUFFICIENT_TEMPERATURE, WidgetIcons.IDLE_LOW_TEMPERATURE);
        register(GTIssues.MAINTENANCE, WidgetIcons.STATUS_MAINTENANCE);
        register(GTIssues.MUFFLER_OBSTRUCTED, WidgetIcons.STATUS_OBSTRUCTED);
        register(GTIssues.MUFFLER_INSUFFICIENT, WidgetIcons.STATUS_OBSTRUCTED);
        register(GTIssues.ROTOR_OBSTRUCTED, WidgetIcons.STATUS_OBSTRUCTED);
        register(GTIssues.ROTOR_MISSING, WidgetIcons.IDLE_NO_CAPABILITY);
        register(GTIssues.LOW_VOLTAGE, WidgetIcons.IDLE_LOW_TIER);
        register(GTIssues.LOW_POWER, WidgetIcons.IDLE_NO_POWER);
        register(GTIssues.EU_SHORT, WidgetIcons.IDLE_NO_POWER);
        register(GTIssues.ENERGY_FULL, WidgetIcons.IDLE_OUTPUT_FULL);
        register(GTIssues.NO_CWU, WidgetIcons.IDLE_NO_COMPUTATION);
        register(GTIssues.INPUT_SHORT, WidgetIcons.IDLE_INPUT_SHORT);
        register(GTIssues.INSUFFICIENT_FUEL, WidgetIcons.IDLE_NO_FUEL);
        register(GTIssues.ORDERED_INPUT, WidgetIcons.IDLE_INPUT_SHORT);
        register(GTIssues.OUTPUT_FULL, WidgetIcons.IDLE_OUTPUT_FULL);
        register(GTIssues.OUTPUT_VOIDED, WidgetIcons.IDLE_OUTPUT_FULL);
        register(GTIssues.NO_INPUT_HATCH, WidgetIcons.IDLE_NO_CAPABILITY);
        register(GTIssues.NO_OUTPUT_HATCH, WidgetIcons.IDLE_NO_CAPABILITY);
        register(GTIssues.NO_ENERGY_HATCH, WidgetIcons.IDLE_NO_CAPABILITY);
        register(GTIssues.OUTPUT_LOST, WidgetIcons.IDLE_OUTPUT_FULL);
        register(GTIssues.EXTENSION_UNMET, WidgetIcons.IDLE_CONDITION);
        register(GTIssues.START_ENERGY_SHORT, WidgetIcons.IDLE_NO_POWER);
        register(GTIssues.START_ENERGY_CAPACITY, WidgetIcons.IDLE_LOW_TIER);
        register(GTIssues.OUTPUT_POWER_LOW, WidgetIcons.IDLE_LOW_TIER);
        register(GTIssues.INTAKE_OBSTRUCTED, WidgetIcons.STATUS_OBSTRUCTED);
        register(GTIssues.NO_LUBRICANT, WidgetIcons.IDLE_INPUT_SHORT);
        register(GTIssues.VENT_BLOCKED, WidgetIcons.STATUS_OBSTRUCTED);
        register(GTIssues.NO_SUNLIGHT, WidgetIcons.IDLE_WAITING);
        register(GTIssues.STEAM_SHORT, WidgetIcons.IDLE_NO_FUEL);
        register(GTIssues.MINER_DONE, WidgetIcons.STATUS_INFO);
        register(GTIssues.NO_VEIN, WidgetIcons.IDLE_CONDITION);
        register(GTIssues.CLEANROOM, WidgetIcons.IDLE_CONDITION);
        register(GTIssues.RESEARCH, WidgetIcons.IDLE_CONDITION);
        register(GTIssues.DIMENSION, WidgetIcons.IDLE_CONDITION);
        register(GTIssues.UNFORMED, WidgetIcons.IDLE_NO_CAPABILITY);
        register(GTIssues.PAUSED, WidgetIcons.STATUS_DISABLED);
        register(GTIssues.DISABLED, WidgetIcons.STATUS_DISABLED);

        registerCustom("gtceu.recipe_logic.no_recipe_found", WidgetIcons.IDLE_NO_RECIPE);
        registerCustom("behavior.prospector.not_enough_energy", WidgetIcons.IDLE_NO_POWER);
        registerCustom("gtceu.recipe_logic.insufficient_tier", WidgetIcons.IDLE_LOW_TIER);
        registerCustom("gtceu.recipe_logic.no_capabilities", WidgetIcons.IDLE_NO_CAPABILITY);
        registerCustom("gtceu.recipe_logic.condition_fails", WidgetIcons.IDLE_CONDITION);
        registerCustom("gtceu.top.maintenance_broken", WidgetIcons.STATUS_MAINTENANCE);
        registerCustom("gtceu.multiblock.universal.muffler_obstructed", WidgetIcons.STATUS_OBSTRUCTED);
        registerCustom("gtceu.multiblock.universal.rotor_obstructed", WidgetIcons.STATUS_OBSTRUCTED);
        registerCustom("gtceu.multiblock.universal.muffler_insufficient", WidgetIcons.STATUS_OBSTRUCTED);
        registerCustom("gtceu.recipe_logic.insufficient_in", WidgetIcons.IDLE_INPUT_SHORT);
        registerCustom("gtceu.recipe_logic.amount_not_enough", WidgetIcons.IDLE_INPUT_SHORT);
        registerCustom("gtceu.recipe_logic.unable_handle", WidgetIcons.IDLE_INPUT_SHORT);
        registerCustom("gtceu.recipe_logic.ordered_item", WidgetIcons.IDLE_INPUT_SHORT);
        registerCustom("gtceu.recipe_logic.ordered_fluid", WidgetIcons.IDLE_INPUT_SHORT);
        registerCustom("gtceu.recipe_logic.insufficient_out", WidgetIcons.IDLE_OUTPUT_FULL);
        registerCustom("gtceu.recipe_logic.insufficient_fuel", WidgetIcons.IDLE_NO_FUEL);
        registerCustom("gtceu.multiblock.computation.not_enough_computation", WidgetIcons.IDLE_NO_COMPUTATION);
    }

    private IssueIcons() {}

    public static void register(IssueType type, IGuiTexture icon) {
        ICONS.put(type, icon);
    }

    public static void registerCustom(String translationKey, IGuiTexture icon) {
        CUSTOM_ICONS.put(translationKey, icon);
        customCache = null;
    }

    public static IGuiTexture iconFor(IssueType type) {
        return iconFor(type, type.severity);
    }

    public static IGuiTexture iconFor(IssueType type, IssueSeverity severity) {
        var icon = ICONS.get(type);
        if (icon != null) return icon;
        return switch (severity) {
            case BLOCKING -> categoryIcon(type.category);
            case WARNING -> warningIcon(type.category);
            case INFO -> WidgetIcons.IDLE_WAITING;
        };
    }

    public static IGuiTexture warningIcon(IssueCategory category) {
        return switch (category) {
            case INPUT -> WidgetIcons.IDLE_INPUT_SHORT;
            case OUTPUT -> WidgetIcons.IDLE_OUTPUT_FULL;
            case COMPUTATION -> WidgetIcons.IDLE_NO_COMPUTATION;
            default -> WidgetIcons.IDLE_WAITING;
        };
    }

    public static IGuiTexture categoryIcon(IssueCategory category) {
        return switch (category) {
            case STRUCTURE, PART, MACHINE -> WidgetIcons.IDLE_NO_CAPABILITY;
            case CONTROL -> WidgetIcons.STATUS_DISABLED;
            case RECIPE, NOT_APPLICABLE -> WidgetIcons.IDLE_NO_RECIPE;
            case TIER -> WidgetIcons.IDLE_LOW_TIER;
            case CONDITION, ENVIRONMENT, TOOL -> WidgetIcons.IDLE_CONDITION;
            case INPUT -> WidgetIcons.IDLE_INPUT_SHORT;
            case OUTPUT -> WidgetIcons.IDLE_OUTPUT_FULL;
            case ENERGY -> WidgetIcons.IDLE_NO_POWER;
            case COMPUTATION -> WidgetIcons.IDLE_NO_COMPUTATION;
        };
    }

    public static IGuiTexture iconFor(@Nullable MachineIssue issue) {
        if (issue == null) return WidgetIcons.IDLE_NO_RECIPE;
        var custom = issue.custom();
        if (custom != null) return iconFor(custom.get());
        return iconFor(issue.type(), issue.severity());
    }

    public static IGuiTexture iconFor(IssueSnapshot snapshot) {
        var issue = IssueLines.shown(snapshot);
        return issue == null ? WidgetIcons.IDLE_WAITING : iconFor(issue);
    }

    public static IGuiTexture iconFor(@Nullable Component customReason) {
        if (customReason == null) return WidgetIcons.IDLE_WAITING;
        var last = customCache;
        if (last != null && last.reason == customReason) return last.icon;
        var icon = resolveCustom(customReason);
        customCache = new CustomCache(customReason, icon);
        return icon;
    }

    private static IGuiTexture resolveCustom(Component reason) {
        if (!(reason.getContents() instanceof TranslatableContents contents)) return WidgetIcons.IDLE_WAITING;
        var key = contents.getKey();
        if ("gtceu.recipe_logic.insufficient_in".equals(key) && mentionsEnergy(reason)) return WidgetIcons.IDLE_NO_POWER;
        return CUSTOM_ICONS.getOrDefault(key, WidgetIcons.IDLE_WAITING);
    }

    private static boolean mentionsEnergy(Component component) {
        for (var sibling : component.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents contents && "recipe.capability.eu.name".equals(contents.getKey())) return true;
            if (mentionsEnergy(sibling)) return true;
        }
        return false;
    }

    private record CustomCache(Component reason, IGuiTexture icon) {}
}
