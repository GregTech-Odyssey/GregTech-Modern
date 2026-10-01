package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 一轮搜索中的可复用候选原因：只存基本类型与引用，按选择规则保留一条，发布时才生成 {@link MachineIssue}。
 */
public final class IssueDraft {

    @Nullable
    private IssueType type;
    private IssueStage stage = IssueStage.SEARCH;
    private IO io = IO.NONE;
    @Nullable
    private RecipeInfo capability;
    private int index = -1;
    private long a, b;
    @Nullable
    private GTRecipeDefinition recipe;
    @Nullable
    private Supplier<Component> custom;
    private boolean focused;
    private boolean empty = true;

    public void reset() {
        empty = true;
    }

    public boolean isEmpty() {
        return empty;
    }

    public boolean offer(IssueType type, IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b,
                         @Nullable GTRecipeDefinition recipe, @Nullable Supplier<Component> custom, boolean focused) {
        if (!empty) {
            var current = this.type;
            if (type.isNotApplicable()) return false;
            if (!current.isNotApplicable()) {
                if (this.focused != focused) {
                    if (!focused) return false;
                } else if (stage.ordinal() <= this.stage.ordinal()) {
                    return false;
                }
            }
        }
        set(type, stage, io, capability, index, a, b, recipe, custom, focused);
        return true;
    }

    public void set(IssueType type, IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b,
                    @Nullable GTRecipeDefinition recipe, @Nullable Supplier<Component> custom, boolean focused) {
        if (this.type != type) this.type = type;
        if (this.stage != stage) this.stage = stage;
        if (this.io != io) this.io = io;
        if (this.capability != capability) this.capability = capability;
        this.index = index;
        this.a = a;
        this.b = b;
        if (this.recipe != recipe) this.recipe = recipe;
        if (this.custom != custom) this.custom = custom;
        this.focused = focused;
        this.empty = type == null;
    }

    public boolean matches(@Nullable MachineIssue issue) {
        if (issue == null) return empty;
        if (empty) return false;
        var t = type;
        return t == issue.type() && issue.severity() == t.severity && stage == issue.stage() && a == issue.a() && b == issue.b() &&
                recipe == issue.recipe() && custom == issue.custom() && issue.subject().matches(io, capability, index);
    }

    @Nullable
    public MachineIssue toIssue() {
        if (empty) return null;
        var t = type;
        if (custom == null && recipe == null && a == 0 && b == 0 && stage == t.stage && io == IO.NONE && capability == null && index < 0) return t.bare();
        return new MachineIssue(t, t.severity, stage, IssueSubject.of(io, capability, index), a, b, recipe, custom);
    }

    @Nullable
    public IssueType type() {
        return empty ? null : type;
    }
}
