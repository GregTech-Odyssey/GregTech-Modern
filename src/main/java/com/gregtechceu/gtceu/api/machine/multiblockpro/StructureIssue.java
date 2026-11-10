package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateAbilities;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构检查失败的原因：类型、位置与期望/实际，由服务端生成并描述成显示行。
 */
public final class StructureIssue {

    public static final Type MISMATCH = new Type("gtceu.structure.issue.mismatch", true);
    public static final Type TOO_FEW = new Type("gtceu.structure.issue.too_few", false);
    public static final Type TOO_MANY = new Type("gtceu.structure.issue.too_many", true);
    public static final Type TOO_FEW_LAYER = new Type("gtceu.structure.issue.too_few_layer", false);
    public static final Type TOO_MANY_LAYER = new Type("gtceu.structure.issue.too_many_layer", true);
    public static final Type MIXED = new Type("gtceu.structure.issue.mixed", true);
    public static final Type SIZE = new Type("gtceu.structure.issue.size", false);
    public static final Type UNLOADED = new Type("gtceu.structure.issue.unloaded", true);
    public static final Type SHARED = new Type("gtceu.structure.issue.shared", true);
    public static final Type FORBIDDEN = new Type("gtceu.structure.issue.forbidden", true);
    public static final Type CONDITION = new Type("gtceu.structure.issue.condition", true);

    public static final long NO_POS = Long.MIN_VALUE;
    public static final Component UNCHECKED = Component.translatable("gtceu.structure.issue.unchecked");

    private static final int MAX_LABELS = 3;
    private static final StructureIssue MISMATCH_ISSUE = new StructureIssue(MISMATCH, null, null, null, 0, 0, 0);
    private static final StructureIssue UNLOADED_ISSUE = new StructureIssue(UNLOADED, null, null, null, 0, 0, 0);
    private static final StructureIssue SHARED_ISSUE = new StructureIssue(SHARED, null, null, null, 0, 0, 0);
    private static final StructureIssue FORBIDDEN_ISSUE = new StructureIssue(FORBIDDEN, null, null, null, 0, 0, 0);

    private final Type type;
    @Nullable
    private final SimplePredicate counted;
    @Nullable
    private final Component subject;
    @Nullable
    private final ParamKey param;
    private final int limit;
    private final int high;
    private final int actual;
    @Nullable
    private BlockPos pos;
    @Nullable
    private TraceabilityPredicate expected;
    @Nullable
    private BlockState found;

    private StructureIssue(Type type, @Nullable SimplePredicate counted, @Nullable Component subject, @Nullable ParamKey param,
                           int limit, int high, int actual) {
        this.type = type;
        this.counted = counted;
        this.subject = subject;
        this.param = param;
        this.limit = limit;
        this.high = high;
        this.actual = actual;
    }

    public static StructureIssue mismatch() {
        return MISMATCH_ISSUE;
    }

    public static StructureIssue unloaded() {
        return UNLOADED_ISSUE;
    }

    public static StructureIssue shared() {
        return SHARED_ISSUE;
    }

    public static StructureIssue forbidden() {
        return FORBIDDEN_ISSUE;
    }

    public static StructureIssue tooFew(SimplePredicate predicate, int min, int actual) {
        return new StructureIssue(TOO_FEW, predicate, null, null, min, 0, actual);
    }

    public static StructureIssue tooMany(SimplePredicate predicate, int max) {
        return new StructureIssue(TOO_MANY, predicate, null, null, max, 0, max + 1);
    }

    public static StructureIssue tooFewInLayer(SimplePredicate predicate, int min, int actual) {
        return new StructureIssue(TOO_FEW_LAYER, predicate, null, null, min, 0, actual);
    }

    public static StructureIssue tooManyInLayer(SimplePredicate predicate, int max) {
        return new StructureIssue(TOO_MANY_LAYER, predicate, null, null, max, 0, max + 1);
    }

    public static StructureIssue mixed(Component what) {
        return new StructureIssue(MIXED, null, what, null, 0, 0, 0);
    }

    public static StructureIssue condition(Component message) {
        return new StructureIssue(CONDITION, null, message, null, 0, 0, 0);
    }

    public static StructureIssue size(ParamKey param, int measured, int min, int max) {
        return new StructureIssue(SIZE, null, null, param, min, max, measured);
    }

    public static StructureIssue sizeCombination() {
        return new StructureIssue(SIZE, null, null, null, 0, 0, 0);
    }

    public StructureIssue locate(MultiblockState state) {
        if (!type.located() || pos != null || state.pos == null) return this;
        var located = new StructureIssue(type, counted, subject, param, limit, high, actual);
        located.pos = state.pos.immutable();
        located.expected = state.predicate;
        located.found = state.getBlockState();
        return located;
    }

    public Type getType() {
        return type;
    }

    @Nullable
    public BlockPos getPos() {
        return pos;
    }

    public long fingerprint() {
        long hash = type.key().hashCode();
        hash = hash * 31 + (pos == null ? 0 : pos.asLong());
        hash = hash * 31 + (found == null ? 0 : Block.getId(found));
        hash = hash * 31 + actual;
        return hash * 31 + (subject == null ? 0 : subject.getString().hashCode());
    }

    @Nullable
    SimplePredicate counted() {
        return counted;
    }

    @Nullable
    Component subject() {
        return subject;
    }

    @Nullable
    ParamKey param() {
        return param;
    }

    int limit() {
        return limit;
    }

    int high() {
        return high;
    }

    int actual() {
        return actual;
    }

    @Nullable
    TraceabilityPredicate expected() {
        return expected;
    }

    @Nullable
    BlockState found() {
        return found;
    }

    public static List<Component> lines(@Nullable StructureIssue issue) {
        var lines = new ArrayList<Component>();
        if (issue == null) lines.add(UNCHECKED);
        else issue.describe(lines);
        return lines;
    }

    public long getPosLong() {
        return pos == null ? NO_POS : pos.asLong();
    }

    public void describe(List<Component> lines) {
        lines.add(headline());
        if (pos != null) lines.add(Component.translatable("gtceu.structure.issue.position", pos.getX(), pos.getY(), pos.getZ()));
        if (type == MISMATCH) {
            var wanted = expected == null ? null : label(expected);
            if (wanted != null) lines.add(Component.translatable("gtceu.structure.issue.expected", wanted));
        }
        if (type == TOO_FEW || type == TOO_FEW_LAYER) {
            lines.add(Component.translatable("gtceu.structure.issue.at_least", limit, actual));
        } else if (type == TOO_MANY || type == TOO_MANY_LAYER) {
            lines.add(Component.translatable("gtceu.structure.issue.at_most", limit));
        } else if (type == SIZE) {
            if (param == null) {
                lines.add(Component.translatable("gtceu.structure.issue.size.combination"));
            } else if (actual < 0) {
                lines.add(Component.translatable("gtceu.structure.issue.size.unmeasured", param.getName(), high));
            } else {
                lines.add(Component.translatable("gtceu.structure.issue.size.range", param.getName(), limit, high, actual));
            }
        }
        if (found != null && type != UNLOADED && type != SHARED) {
            lines.add(Component.translatable("gtceu.structure.issue.found", found.getBlock().getName()));
        }
    }

    private Component headline() {
        if (type == CONDITION && subject != null) return subject;
        if (type == MIXED) return Component.translatable(type.key(), subject == null ? Component.empty() : subject);
        if (counted != null) {
            var name = label(counted);
            return Component.translatable(type.key(), name == null ? Component.empty() : name);
        }
        return Component.translatable(type.key());
    }

    @Nullable
    static Component label(TraceabilityPredicate predicate) {
        var labels = new ArrayList<Component>();
        for (int rank = 0; rank < 3; rank++) {
            for (var simple : predicate.common) addLabel(labels, simple, rank);
            for (var simple : predicate.limited) addLabel(labels, simple, rank);
        }
        if (labels.isEmpty()) return null;
        var result = Component.empty();
        int shown = Math.min(labels.size(), MAX_LABELS);
        for (int i = 0; i < shown; i++) {
            if (i > 0) result.append(" / ");
            result.append(labels.get(i));
        }
        if (labels.size() > MAX_LABELS) return Component.translatable("gtceu.structure.issue.more", result, labels.size());
        return result;
    }

    @Nullable
    static Component compactLabel(TraceabilityPredicate predicate) {
        var labels = new ArrayList<Component>();
        SimplePredicate first = null;
        for (int rank = 0; rank < 3; rank++) {
            for (var simple : predicate.common) {
                int before = labels.size();
                addLabel(labels, simple, rank);
                if (first == null && labels.size() > before) first = simple;
            }
            for (var simple : predicate.limited) {
                int before = labels.size();
                addLabel(labels, simple, rank);
                if (first == null && labels.size() > before) first = simple;
            }
        }
        if (first == null) return null;
        if (labels.size() == 1) return labels.get(0);
        return Component.translatable("gtceu.structure.diagnosis.alternatives", primaryName(first), labels.size() - 1);
    }

    private static Component primaryName(SimplePredicate simple) {
        if (simple instanceof PredicateAbilities abilities) return PartAbility.join(abilities.getAbilities());
        if (simple.candidates != null) {
            Block[] blocks = simple.candidates.get();
            if (blocks.length > 0) return blocks[0].getName();
        }
        var label = label(simple);
        return label == null ? Component.empty() : label;
    }

    private static void addLabel(List<Component> labels, SimplePredicate simple, int rank) {
        if (rank(simple) != rank) return;
        var label = label(simple);
        if (label != null && !labels.contains(label)) labels.add(label);
    }

    static int rank(SimplePredicate simple) {
        if (simple instanceof PredicateAbilities) return 2;
        if (simple.candidates == null) return 0;
        Block[] blocks = simple.candidates.get();
        return blocks.length > 0 && blocks[0] instanceof MetaMachineBlock ? 1 : 0;
    }

    @Nullable
    static Component label(SimplePredicate simple) {
        if (simple.is(SimplePredicate.AIR)) return Component.translatable("block.minecraft.air");
        if (simple instanceof PredicateAbilities abilities) return PartAbility.join(abilities.getAbilities());
        if (simple.candidates == null) return null;
        Block[] blocks = simple.candidates.get();
        if (blocks.length == 0) return null;
        if (blocks.length == 1) return blocks[0].getName();
        return Component.translatable("gtceu.structure.issue.variants", blocks[0].getName(), blocks.length);
    }

    public record Type(String key, boolean located) {}
}
