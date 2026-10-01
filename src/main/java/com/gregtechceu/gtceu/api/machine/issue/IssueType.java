package com.gregtechceu.gtceu.api.machine.issue;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器运行问题的类型注册表：mod 加载期注册，加载完成时冻结并按 id 排序分配网络编号。
 */
public final class IssueType {

    private static final Object LOCK = new Object();
    private static final Map<ResourceLocation, IssueType> BY_ID = new HashMap<>();
    @Nullable
    private static volatile IssueType[] byNetworkId;

    public final ResourceLocation id;
    public final IssueCategory category;
    public final IssueSeverity severity;
    public final IssueStage stage;
    public final String titleKey;
    public final String descKey;
    @Nullable
    public final Args args;
    private final MachineIssue bare;
    private int networkId = -1;

    private IssueType(Builder builder) {
        this.id = builder.id;
        this.category = builder.category;
        this.severity = builder.severity;
        this.stage = builder.stage;
        this.titleKey = builder.titleKey;
        this.descKey = builder.descKey;
        this.args = builder.args;
        this.bare = new MachineIssue(this, severity, stage, IssueSubject.NONE, 0, 0, null, null);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public MachineIssue bare() {
        return bare;
    }

    public boolean hasArgs() {
        return args != null;
    }

    public boolean isNotApplicable() {
        return category == IssueCategory.NOT_APPLICABLE;
    }

    public int networkId() {
        if (byNetworkId == null) freeze();
        return networkId;
    }

    public static void freeze() {
        synchronized (LOCK) {
            if (byNetworkId != null) return;
            var list = new ArrayList<>(BY_ID.values());
            list.sort(Comparator.comparing(t -> t.id));
            var array = new IssueType[list.size()];
            for (int i = 0; i < array.length; i++) {
                var type = list.get(i);
                type.networkId = i;
                array[i] = type;
            }
            byNetworkId = array;
        }
    }

    @Nullable
    public static IssueType byNetworkId(int id) {
        var array = byNetworkId;
        if (array == null) {
            freeze();
            array = byNetworkId;
        }
        return id >= 0 && id < array.length ? array[id] : null;
    }

    @Nullable
    public static IssueType byId(ResourceLocation id) {
        if (byNetworkId != null) return BY_ID.get(id);
        synchronized (LOCK) {
            return BY_ID.get(id);
        }
    }

    public static List<IssueType> values() {
        var array = byNetworkId;
        if (array != null) return List.of(array);
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
        }
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @FunctionalInterface
    public interface Args {

        Object[] get(MachineIssue issue);
    }

    public static final class Builder {

        private final ResourceLocation id;
        private IssueCategory category = IssueCategory.MACHINE;
        private IssueSeverity severity = IssueSeverity.BLOCKING;
        private IssueStage stage = IssueStage.SEARCH;
        private String titleKey;
        private String descKey;
        @Nullable
        private Args args;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder category(IssueCategory category) {
            this.category = category;
            return this;
        }

        public Builder severity(IssueSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder stage(IssueStage stage) {
            this.stage = stage;
            return this;
        }

        public Builder keys(String titleKey, String descKey) {
            this.titleKey = titleKey;
            this.descKey = descKey;
            return this;
        }

        public Builder args(Args args) {
            this.args = args;
            return this;
        }

        public IssueType register() {
            if (titleKey == null || descKey == null) throw new IllegalStateException("Issue type " + id + " has no translation keys");
            var type = new IssueType(this);
            synchronized (LOCK) {
                if (byNetworkId != null) throw new IllegalStateException("Issue type " + id + " registered after the registry was frozen");
                if (BY_ID.putIfAbsent(id, type) != null) throw new IllegalStateException("Duplicate issue type " + id);
            }
            return type;
        }
    }
}
