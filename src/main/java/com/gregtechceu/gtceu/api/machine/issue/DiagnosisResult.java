package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.uipro.flow.FlowState;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 第二级诊断的不可变结果；相等性不含计算时刻与快照版本号，内容未变时诊断服务复用同一实例，界面同步因此不重发。
 */
public final class DiagnosisResult {

    public static final DiagnosisResult EMPTY = new DiagnosisResult(IssueSnapshot.EMPTY, null, DiagnosisTarget.NONE, Collections.emptyList(), Collections.emptyList(), null, 0);

    private static final int MAX_DECODED = 256;

    public static final ByteStreamCodec<DiagnosisResult> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, DiagnosisResult result) {
            IssueSnapshot.STREAM_CODEC.encode(buf, result.snapshot);
            buf.writeBoolean(result.recipe != null);
            if (result.recipe != null) GTRecipeDefinition.STREAM_CODEC.encode(buf, result.recipe);
            buf.writeByte(result.target.ordinal());
            buf.writeVarInt(result.entries.size());
            for (var entry : result.entries) DiagnosisEntry.STREAM_CODEC.encode(buf, entry);
            buf.writeVarInt(result.issues.size());
            for (var issue : result.issues) MachineIssue.STREAM_CODEC.encode(buf, issue);
            MachineIssue.NULLABLE_STREAM_CODEC.encode(buf, result.primary);
        }

        @Override
        public DiagnosisResult decode(FriendlyByteBuf buf) {
            var snapshot = IssueSnapshot.STREAM_CODEC.decode(buf);
            var recipe = buf.readBoolean() ? GTRecipeDefinition.STREAM_CODEC.decode(buf) : null;
            var target = DiagnosisTarget.of(buf.readByte());
            int entryCount = buf.readVarInt();
            List<DiagnosisEntry> entries = entryCount <= 0 ? Collections.emptyList() : new ArrayList<>(Math.min(entryCount, MAX_DECODED));
            for (int i = 0; i < entryCount; i++) {
                var entry = DiagnosisEntry.STREAM_CODEC.decode(buf);
                if (i < MAX_DECODED) entries.add(entry);
            }
            int issueCount = buf.readVarInt();
            List<MachineIssue> issues = issueCount <= 0 ? Collections.emptyList() : new ArrayList<>(Math.min(issueCount, MAX_DECODED));
            for (int i = 0; i < issueCount; i++) {
                var issue = MachineIssue.STREAM_CODEC.decode(buf);
                if (i < MAX_DECODED) issues.add(issue);
            }
            var primary = MachineIssue.NULLABLE_STREAM_CODEC.decode(buf);
            return new DiagnosisResult(snapshot, recipe, target, entries, issues, primary, 0);
        }
    };

    private final IssueSnapshot snapshot;
    @Nullable
    private final GTRecipeDefinition recipe;
    private final DiagnosisTarget target;
    private final List<DiagnosisEntry> entries;
    private final List<MachineIssue> issues;
    @Nullable
    private final MachineIssue primary;
    private int time;
    private int basis;

    public DiagnosisResult(IssueSnapshot snapshot, @Nullable GTRecipeDefinition recipe, DiagnosisTarget target,
                           List<DiagnosisEntry> entries, List<MachineIssue> issues, @Nullable MachineIssue primary, int time) {
        this.snapshot = snapshot;
        this.recipe = recipe;
        this.target = target;
        this.entries = entries.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(entries);
        this.issues = issues.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(issues);
        this.primary = primary;
        this.time = time;
        this.basis = snapshot.version();
    }

    public IssueSnapshot snapshot() {
        return snapshot;
    }

    @Nullable
    public GTRecipeDefinition recipe() {
        return recipe;
    }

    public DiagnosisTarget target() {
        return target;
    }

    public List<DiagnosisEntry> entries() {
        return entries;
    }

    public List<MachineIssue> issues() {
        return issues;
    }

    @Nullable
    public MachineIssue primary() {
        return primary;
    }

    public int time() {
        return time;
    }

    int basis() {
        return basis;
    }

    void restamp(int time, int basis) {
        this.time = time;
        this.basis = basis;
    }

    public boolean isWorking() {
        return snapshot.isWorking();
    }

    @Nullable
    public MachineIssue issue(IssueType type) {
        if (primary != null && primary.type() == type) return primary;
        for (var issue : issues) if (issue.type() == type) return issue;
        return null;
    }

    public List<MachineIssue> issues(IssueCategory category) {
        List<MachineIssue> list = null;
        for (var issue : issues) {
            if (issue.category() != category) continue;
            if (list == null) list = new ArrayList<>();
            list.add(issue);
        }
        return list == null ? Collections.emptyList() : list;
    }

    @Nullable
    public DiagnosisEntry entry(IssueType type) {
        for (var entry : entries) if (entry.type() == type && !entry.isOk()) return entry;
        return null;
    }

    public boolean has(IssueType type) {
        return issue(type) != null || entry(type) != null;
    }

    public List<DiagnosisEntry> entries(IO io, @Nullable RecipeInfo capability) {
        List<DiagnosisEntry> list = null;
        for (var entry : entries) {
            if (!entry.is(io, capability)) continue;
            if (list == null) list = new ArrayList<>();
            list.add(entry);
        }
        return list == null ? Collections.emptyList() : list;
    }

    @Nullable
    public DiagnosisEntry entry(IO io, @Nullable RecipeInfo capability, int index) {
        for (var entry : entries) {
            if (entry.subject().matches(io, capability, index)) return entry;
        }
        return null;
    }

    public boolean satisfied(IO io, @Nullable RecipeInfo capability) {
        for (var entry : entries) {
            if (entry.isProblem() && entry.is(io, capability)) return false;
        }
        return true;
    }

    public boolean hasProblem() {
        for (var entry : entries) if (entry.isProblem()) return true;
        for (var issue : issues) if (issue.isBlocking()) return true;
        return primary != null && primary.isBlocking();
    }

    public IssueSeverity severity() {
        var worst = primary == null ? IssueSeverity.INFO : primary.severity();
        for (var issue : issues) if (issue.severity().ordinal() > worst.ordinal()) worst = issue.severity();
        for (var entry : entries) if (entry.severity().ordinal() > worst.ordinal()) worst = entry.severity();
        return worst;
    }

    public FlowState flowState(IssueType type, FlowState fallback) {
        var issue = issue(type);
        if (issue != null) return issue.severity().flowState();
        var entry = entry(type);
        return entry != null ? entry.state().flowState() : fallback;
    }

    public FlowState flowState(IO io, @Nullable RecipeInfo capability, FlowState fallback) {
        FlowState state = null;
        for (var entry : entries) {
            if (!entry.is(io, capability)) continue;
            var current = entry.state().flowState();
            state = state == null ? current : FlowState.worst(state, current);
        }
        return state == null ? fallback : state;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DiagnosisResult other)) return false;
        return recipe == other.recipe && target == other.target && snapshot.status() == other.snapshot.status() &&
                Objects.equals(snapshot.primary(), other.snapshot.primary()) && Objects.equals(primary, other.primary) &&
                entries.equals(other.entries) && issues.equals(other.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshot.status(), snapshot.primary(), recipe, target, entries, issues, primary);
    }
}
