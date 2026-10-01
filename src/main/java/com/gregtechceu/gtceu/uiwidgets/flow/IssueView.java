package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.api.machine.issue.DiagnosisEntry;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisResult;
import com.gregtechceu.gtceu.api.machine.issue.IssueSeverity;
import com.gregtechceu.gtceu.api.machine.issue.IssueText;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.uipro.flow.FlowState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

public record IssueView(RecipeIssue issue, @Nullable Component label, @Nullable FlowState stateOverride, @Nullable MachineIssue source) {

    public static final ByteStreamCodec<IssueView> CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, IssueView value) {
            buf.writeVarInt(value.issue.ordinal());
            buf.writeBoolean(value.label != null);
            if (value.label != null) StreamCodecs.COMPONENT_CODEC.encode(buf, value.label);
            buf.writeByte(value.stateOverride == null ? -1 : value.stateOverride.ordinal());
            MachineIssue.NULLABLE_STREAM_CODEC.encode(buf, value.source);
        }

        @Override
        public IssueView decode(FriendlyByteBuf buf) {
            var issue = RecipeIssue.of(buf.readVarInt());
            var label = buf.readBoolean() ? StreamCodecs.COMPONENT_CODEC.decode(buf) : null;
            int state = buf.readByte();
            var source = MachineIssue.NULLABLE_STREAM_CODEC.decode(buf);
            if (label == null && state < 0 && source == null) return issue.view();
            return new IssueView(issue, label, state < 0 ? null : FlowState.of(state), source);
        }
    };

    public IssueView(RecipeIssue issue, @Nullable Component label, @Nullable FlowState stateOverride) {
        this(issue, label, stateOverride, null);
    }

    public static IssueView of(RecipeIssue issue, @Nullable Component label) {
        return label == null ? issue.view() : new IssueView(issue, label, null, null);
    }

    public static IssueView of(RecipeIssue issue, @Nullable Component label, FlowState state) {
        return new IssueView(issue, label, state, null);
    }

    public static IssueView of(MachineIssue source) {
        var issue = RecipeIssue.from(source);
        var state = source.severity() == issue.severity() ? null : source.severity().flowState();
        return new IssueView(issue, null, state, source);
    }

    public static IssueView of(DiagnosisEntry entry, @Nullable GTRecipeDefinition recipe) {
        var issue = RecipeIssue.from(entry);
        var source = entry.toIssue(recipe);
        if (source == null) return issue.view();
        var state = entry.severity() == issue.severity() ? null : entry.state().flowState();
        return new IssueView(issue, null, state, source);
    }

    public static IssueView of(DiagnosisResult result, IssueType type, IssueView fallback) {
        var issue = result.issue(type);
        if (issue != null) return of(issue);
        var entry = result.entry(type);
        return entry != null ? of(entry, result.recipe()) : fallback;
    }

    public static IssueView primary(DiagnosisResult result, IssueView fallback) {
        var primary = result.primary();
        return primary == null ? fallback : of(primary);
    }

    public FlowState state() {
        return stateOverride != null ? stateOverride : issue.state();
    }

    public IssueSeverity severity() {
        return source != null ? source.severity() : issue.severity();
    }

    public boolean isProblem() {
        return severity() == IssueSeverity.BLOCKING;
    }

    public Component text() {
        if (label != null) return label;
        if (source != null) return IssueText.title(source);
        return Component.translatable(issue.key());
    }

    public Component description() {
        if (source != null) {
            var detail = IssueText.detail(source);
            if (detail != null) return detail;
        }
        return Component.translatable(issue.descriptionKey());
    }
}
