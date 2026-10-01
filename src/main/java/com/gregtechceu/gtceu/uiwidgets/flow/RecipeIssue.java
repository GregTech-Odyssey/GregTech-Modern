package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.api.machine.issue.DiagnosisEntry;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.issue.IssueSeverity;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.CWURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.uipro.flow.FlowState;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import org.jetbrains.annotations.Nullable;

public enum RecipeIssue {

    OK(null, FlowState.READY, IssueSeverity.INFO, "gtceu.flow.issue.ok", "gtceu.flow.issue.ok.desc"),
    RUNNING(null, FlowState.ACTIVE, IssueSeverity.INFO, "gtceu.flow.issue.running", "gtceu.flow.issue.running.desc"),
    IDLE(null, FlowState.IDLE, IssueSeverity.INFO, "gtceu.flow.issue.idle", "gtceu.flow.issue.idle.desc"),
    OFFLINE(null, FlowState.IDLE, IssueSeverity.INFO, "gtceu.flow.issue.offline", "gtceu.flow.issue.offline.desc"),
    INPUT_STOCKED(null, FlowState.READY, IssueSeverity.INFO, "gtceu.flow.issue.input_stocked", "gtceu.flow.issue.input_stocked.desc"),
    INPUT_NEXT_OK(null, FlowState.ACTIVE, IssueSeverity.INFO, "gtceu.flow.issue.input_next_ok", "gtceu.flow.issue.input_next_ok.desc"),
    OUTPUT_ACTIVE(null, FlowState.ACTIVE, IssueSeverity.INFO, "gtceu.flow.issue.output_active", "gtceu.flow.issue.output_active.desc"),
    OUTPUT_CLEAR(null, FlowState.READY, IssueSeverity.INFO, "gtceu.flow.issue.output_clear", "gtceu.flow.issue.output_clear.desc"),
    OUTPUT_VOID_OVERFLOW_ACTIVE(null, FlowState.ACTIVE, IssueSeverity.INFO, "gtceu.flow.issue.output_void_overflow", "gtceu.flow.issue.output_void_overflow.desc"),
    OUTPUT_VOID_OVERFLOW(null, FlowState.READY, IssueSeverity.INFO, "gtceu.flow.issue.output_void_overflow", "gtceu.flow.issue.output_void_overflow.desc"),
    OUTPUT_VOIDED(null, FlowState.WARNING, IssueSeverity.WARNING, "gtceu.flow.issue.output_voided", "gtceu.flow.issue.output_voided.desc"),
    ENERGY_ACTIVE(null, FlowState.ACTIVE, IssueSeverity.INFO, "gtceu.flow.issue.energy_active", "gtceu.flow.issue.energy_active.desc"),
    ENERGY_READY(null, FlowState.READY, IssueSeverity.INFO, "gtceu.flow.issue.energy_ready", "gtceu.flow.issue.energy_ready.desc"),
    NO_INPUT_HATCH(WidgetIcons.IDLE_NO_CAPABILITY, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.no_input_hatch", "gtceu.flow.issue.no_input_hatch.desc"),
    INPUT_SHORT(WidgetIcons.IDLE_INPUT_SHORT, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.input_short", "gtceu.flow.issue.input_short.desc"),
    NEXT_SHORT(WidgetIcons.IDLE_INPUT_SHORT, FlowState.WARNING, IssueSeverity.BLOCKING, "gtceu.flow.issue.next_short", "gtceu.flow.issue.next_short.desc"),
    NO_OUTPUT_HATCH(WidgetIcons.IDLE_NO_CAPABILITY, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.no_output_hatch", "gtceu.flow.issue.no_output_hatch.desc"),
    OUTPUT_FULL(WidgetIcons.IDLE_OUTPUT_FULL, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.output_full", "gtceu.flow.issue.output_full.desc"),
    NO_ENERGY_HATCH(WidgetIcons.IDLE_NO_CAPABILITY, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.no_energy_hatch", "gtceu.flow.issue.no_energy_hatch.desc"),
    LOW_VOLTAGE(WidgetIcons.IDLE_LOW_TIER, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.low_voltage", "gtceu.flow.issue.low_voltage.desc"),
    LOW_POWER(WidgetIcons.IDLE_NO_POWER, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.low_power", "gtceu.flow.issue.low_power.desc"),
    LOW_BUFFER(WidgetIcons.IDLE_WAITING, FlowState.WARNING, IssueSeverity.BLOCKING, "gtceu.flow.issue.low_buffer", "gtceu.flow.issue.low_buffer.desc"),
    UNFORMED(WidgetIcons.IDLE_CONDITION, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.unformed", "gtceu.flow.issue.unformed.desc"),
    DISABLED(WidgetIcons.STATUS_DISABLED, FlowState.DISABLED, IssueSeverity.BLOCKING, "gtceu.flow.issue.disabled", "gtceu.flow.issue.disabled.desc"),
    CONDITION(WidgetIcons.IDLE_CONDITION, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.condition", "gtceu.flow.issue.condition.desc"),
    WAITING(WidgetIcons.IDLE_WAITING, FlowState.WARNING, IssueSeverity.BLOCKING, "gtceu.flow.issue.waiting", "gtceu.flow.issue.waiting.desc"),
    POWER_LIMITED(WidgetIcons.IDLE_NO_POWER, FlowState.WARNING, IssueSeverity.WARNING, "gtceu.flow.issue.power_limited", "gtceu.flow.issue.power_limited.desc"),
    VOLTAGE_LIMITED(WidgetIcons.IDLE_LOW_TIER, FlowState.WARNING, IssueSeverity.WARNING, "gtceu.flow.issue.voltage_limited", "gtceu.flow.issue.voltage_limited.desc"),
    ENERGY_FULL(WidgetIcons.IDLE_OUTPUT_FULL, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.energy_full", "gtceu.flow.issue.energy_full.desc"),
    NO_CWU(WidgetIcons.IDLE_NO_COMPUTATION, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.no_cwu", "gtceu.flow.issue.no_cwu.desc"),
    COMPUTATION_READY(null, FlowState.READY, IssueSeverity.INFO, "gtceu.flow.issue.computation_ready", "gtceu.flow.issue.computation_ready.desc"),
    NO_RECIPE(WidgetIcons.IDLE_NO_RECIPE, FlowState.IDLE, IssueSeverity.INFO, "gtceu.flow.issue.no_recipe", "gtceu.flow.issue.no_recipe.desc"),
    PAUSED(WidgetIcons.STATUS_DISABLED, FlowState.DISABLED, IssueSeverity.INFO, "gtceu.flow.issue.paused", "gtceu.flow.issue.paused.desc"),
    FAULT(WidgetIcons.IDLE_WAITING, FlowState.MISSING, IssueSeverity.BLOCKING, "gtceu.flow.issue.fault", "gtceu.flow.issue.fault.desc");

    private static final RecipeIssue[] VALUES = values();

    @Nullable
    private final IGuiTexture icon;
    private final FlowState state;
    private final IssueSeverity severity;
    private final String key;
    private final String descriptionKey;
    private final IssueView view;

    RecipeIssue(@Nullable IGuiTexture icon, FlowState state, IssueSeverity severity, String key, String descriptionKey) {
        this.icon = icon;
        this.state = state;
        this.severity = severity;
        this.key = key;
        this.descriptionKey = descriptionKey;
        this.view = new IssueView(this, null, null, null);
    }

    public static RecipeIssue of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
    }

    public static RecipeIssue from(IssueType type) {
        if (type == GTIssues.NO_RECIPE || type == GTIssues.NOT_APPLICABLE) return NO_RECIPE;
        if (type == GTIssues.LOW_TIER || type == GTIssues.LOW_VOLTAGE) return LOW_VOLTAGE;
        if (type == GTIssues.LOW_POWER) return LOW_POWER;
        if (type == GTIssues.EU_SHORT) return LOW_BUFFER;
        if (type == GTIssues.ENERGY_FULL) return ENERGY_FULL;
        if (type == GTIssues.NO_CWU) return NO_CWU;
        if (type == GTIssues.OUTPUT_FULL) return OUTPUT_FULL;
        if (type == GTIssues.OUTPUT_VOIDED) return OUTPUT_VOIDED;
        if (type == GTIssues.NO_INPUT_HATCH || type == GTIssues.NO_CAPABILITIES) return NO_INPUT_HATCH;
        if (type == GTIssues.NO_OUTPUT_HATCH) return NO_OUTPUT_HATCH;
        if (type == GTIssues.NO_ENERGY_HATCH) return NO_ENERGY_HATCH;
        if (type == GTIssues.UNFORMED) return UNFORMED;
        if (type == GTIssues.PAUSED) return PAUSED;
        if (type == GTIssues.DISABLED) return DISABLED;
        if (type == GTIssues.OUTPUT_LOST) return OUTPUT_VOIDED;
        if (type == GTIssues.OUTPUT_POWER_LOW) return LOW_VOLTAGE;
        if (type == GTIssues.START_ENERGY_SHORT || type == GTIssues.STEAM_SHORT) return LOW_BUFFER;
        if (type == GTIssues.START_ENERGY_CAPACITY) return FAULT;
        if (type == GTIssues.NO_LUBRICANT) return INPUT_SHORT;
        if (type == GTIssues.MINER_DONE || type == GTIssues.NO_SUNLIGHT) return IDLE;
        if (type == GTIssues.EXTENSION_UNMET || type == GTIssues.VENT_BLOCKED || type == GTIssues.NO_VEIN ||
                type == GTIssues.CLEANROOM || type == GTIssues.RESEARCH || type == GTIssues.DIMENSION)
            return CONDITION;
        return switch (type.category) {
            case STRUCTURE -> UNFORMED;
            case CONTROL -> DISABLED;
            case RECIPE, NOT_APPLICABLE -> NO_RECIPE;
            case TIER, CONDITION, ENVIRONMENT -> CONDITION;
            case INPUT -> INPUT_SHORT;
            case OUTPUT -> OUTPUT_FULL;
            case ENERGY -> LOW_BUFFER;
            case COMPUTATION -> NO_CWU;
            default -> type.severity == IssueSeverity.BLOCKING ? FAULT : WAITING;
        };
    }

    public static RecipeIssue from(MachineIssue issue) {
        var base = from(issue.type());
        if (issue.severity() == IssueSeverity.WARNING) {
            if (base == LOW_POWER) return POWER_LIMITED;
            if (base == LOW_VOLTAGE) return VOLTAGE_LIMITED;
        }
        return base;
    }

    public static RecipeIssue from(DiagnosisEntry entry) {
        var subject = entry.subject();
        var type = entry.type();
        return switch (entry.state()) {
            case OK -> {
                if (subject.capability() == EURecipeInfo.INSTANCE) yield ENERGY_READY;
                if (subject.capability() == CWURecipeInfo.INSTANCE) yield COMPUTATION_READY;
                if (subject.io() == IO.IN && subject.capability() != null) yield INPUT_STOCKED;
                if (subject.io() == IO.OUT) yield OUTPUT_CLEAR;
                yield OK;
            }
            case SKIPPED -> IDLE;
            case VOIDED -> OUTPUT_VOIDED;
            case LIMITED -> type == GTIssues.LOW_POWER ? POWER_LIMITED : type == GTIssues.LOW_VOLTAGE ? VOLTAGE_LIMITED : WAITING;
            case MISSING_HANDLER -> {
                if (type != null) yield from(type);
                yield subject.io() == IO.OUT ? NO_OUTPUT_HATCH : NO_INPUT_HATCH;
            }
            default -> type == null ? CONDITION : from(type);
        };
    }

    @Nullable
    public IGuiTexture icon() {
        return icon;
    }

    public FlowState state() {
        return state;
    }

    public IssueSeverity severity() {
        return severity;
    }

    public String key() {
        return key;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public IssueView view() {
        return view;
    }

    public boolean isProblem() {
        return severity == IssueSeverity.BLOCKING;
    }
}
