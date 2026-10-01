package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.uipro.flow.FlowState;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import org.jetbrains.annotations.Nullable;

public enum RecipeIssue {

    OK(null, FlowState.READY, "gtceu.flow.issue.ok", "gtceu.flow.issue.ok.desc"),
    RUNNING(null, FlowState.ACTIVE, "gtceu.flow.issue.running", "gtceu.flow.issue.running.desc"),
    IDLE(null, FlowState.IDLE, "gtceu.flow.issue.idle", "gtceu.flow.issue.idle.desc"),
    OFFLINE(null, FlowState.IDLE, "gtceu.flow.issue.offline", "gtceu.flow.issue.offline.desc"),
    INPUT_STOCKED(null, FlowState.READY, "gtceu.flow.issue.input_stocked", "gtceu.flow.issue.input_stocked.desc"),
    INPUT_NEXT_OK(null, FlowState.ACTIVE, "gtceu.flow.issue.input_next_ok", "gtceu.flow.issue.input_next_ok.desc"),
    OUTPUT_ACTIVE(null, FlowState.ACTIVE, "gtceu.flow.issue.output_active", "gtceu.flow.issue.output_active.desc"),
    OUTPUT_CLEAR(null, FlowState.READY, "gtceu.flow.issue.output_clear", "gtceu.flow.issue.output_clear.desc"),
    OUTPUT_VOID_OVERFLOW_ACTIVE(null, FlowState.ACTIVE, "gtceu.flow.issue.output_void_overflow", "gtceu.flow.issue.output_void_overflow.desc"),
    OUTPUT_VOID_OVERFLOW(null, FlowState.READY, "gtceu.flow.issue.output_void_overflow", "gtceu.flow.issue.output_void_overflow.desc"),
    OUTPUT_VOIDED(null, FlowState.WARNING, "gtceu.flow.issue.output_voided", "gtceu.flow.issue.output_voided.desc"),
    ENERGY_ACTIVE(null, FlowState.ACTIVE, "gtceu.flow.issue.energy_active", "gtceu.flow.issue.energy_active.desc"),
    ENERGY_READY(null, FlowState.READY, "gtceu.flow.issue.energy_ready", "gtceu.flow.issue.energy_ready.desc"),
    NO_INPUT_HATCH(WidgetIcons.IDLE_NO_CAPABILITY, FlowState.MISSING, "gtceu.flow.issue.no_input_hatch", "gtceu.flow.issue.no_input_hatch.desc"),
    INPUT_SHORT(WidgetIcons.IDLE_INPUT_SHORT, FlowState.MISSING, "gtceu.flow.issue.input_short", "gtceu.flow.issue.input_short.desc"),
    NEXT_SHORT(WidgetIcons.IDLE_INPUT_SHORT, FlowState.WARNING, "gtceu.flow.issue.next_short", "gtceu.flow.issue.next_short.desc"),
    NO_OUTPUT_HATCH(WidgetIcons.IDLE_NO_CAPABILITY, FlowState.MISSING, "gtceu.flow.issue.no_output_hatch", "gtceu.flow.issue.no_output_hatch.desc"),
    OUTPUT_FULL(WidgetIcons.IDLE_OUTPUT_FULL, FlowState.MISSING, "gtceu.flow.issue.output_full", "gtceu.flow.issue.output_full.desc"),
    NO_ENERGY_HATCH(WidgetIcons.IDLE_NO_CAPABILITY, FlowState.MISSING, "gtceu.flow.issue.no_energy_hatch", "gtceu.flow.issue.no_energy_hatch.desc"),
    LOW_VOLTAGE(WidgetIcons.IDLE_LOW_TIER, FlowState.MISSING, "gtceu.flow.issue.low_voltage", "gtceu.flow.issue.low_voltage.desc"),
    LOW_POWER(WidgetIcons.IDLE_NO_POWER, FlowState.MISSING, "gtceu.flow.issue.low_power", "gtceu.flow.issue.low_power.desc"),
    LOW_BUFFER(WidgetIcons.IDLE_WAITING, FlowState.WARNING, "gtceu.flow.issue.low_buffer", "gtceu.flow.issue.low_buffer.desc"),
    UNFORMED(WidgetIcons.IDLE_CONDITION, FlowState.MISSING, "gtceu.flow.issue.unformed", "gtceu.flow.issue.unformed.desc"),
    DISABLED(WidgetIcons.STATUS_DISABLED, FlowState.DISABLED, "gtceu.flow.issue.disabled", "gtceu.flow.issue.disabled.desc"),
    CONDITION(WidgetIcons.IDLE_CONDITION, FlowState.MISSING, "gtceu.flow.issue.condition", "gtceu.flow.issue.condition.desc"),
    WAITING(WidgetIcons.IDLE_WAITING, FlowState.WARNING, "gtceu.flow.issue.waiting", "gtceu.flow.issue.waiting.desc");

    private static final RecipeIssue[] VALUES = values();

    @Nullable
    private final IGuiTexture icon;
    private final FlowState state;
    private final String key;
    private final String descriptionKey;
    private final IssueView view;

    RecipeIssue(@Nullable IGuiTexture icon, FlowState state, String key, String descriptionKey) {
        this.icon = icon;
        this.state = state;
        this.key = key;
        this.descriptionKey = descriptionKey;
        this.view = new IssueView(this, null, null);
    }

    public static RecipeIssue of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
    }

    @Nullable
    public IGuiTexture icon() {
        return icon;
    }

    public FlowState state() {
        return state;
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
        return icon != null;
    }
}
