package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class IssueText {

    private IssueText() {}

    public static Component title(MachineIssue issue) {
        var custom = issue.custom();
        if (custom != null) {
            var text = custom.get();
            return text == null ? Component.empty() : text;
        }
        return Component.translatable(issue.type().titleKey);
    }

    public static Component titleOffThread(MachineIssue issue) {
        return Component.translatable(issue.custom() != null ? GTIssues.CUSTOM.titleKey : issue.type().titleKey);
    }

    @Nullable
    public static Component detail(MachineIssue issue) {
        if (issue.custom() != null) return null;
        var type = issue.type();
        var args = type.args;
        return args == null ? Component.translatable(type.descKey) : Component.translatable(type.descKey, args.get(issue));
    }

    public static Component summary(MachineIssue issue) {
        if (issue.custom() != null || !issue.type().hasArgs()) return title(issue);
        return Component.translatable("gtceu.issue.summary", title(issue), detail(issue));
    }

    public static Component summary(@Nullable IssueSnapshot snapshot) {
        var primary = snapshot == null ? null : snapshot.primary();
        return summary(primary == null ? GTIssues.NO_RECIPE.bare() : primary);
    }

    public static Component number(long value) {
        return value < 0 ? dash() : Component.literal(FormattingUtil.formatNumbers(value));
    }

    public static Component tierName(long tier) {
        return tier < 0 || tier >= GTValues.VN.length ? dash() : Component.literal(GTValues.VN[(int) tier]);
    }

    public static Component capabilityName(MachineIssue issue) {
        var capability = issue.subject().capability();
        if (capability == ItemRecipeInfo.INSTANCE) return Component.translatable("gtceu.issue.subject.items");
        if (capability == FluidRecipeInfo.INSTANCE) return Component.translatable("gtceu.issue.subject.fluids");
        if (capability != null) return capability.getName();
        return Component.translatable("gtceu.issue.subject.products");
    }

    @Nullable
    public static Content<?> content(@Nullable GTRecipeDefinition recipe, IssueSubject subject) {
        if (recipe == null || subject.index() < 0) return null;
        List<? extends Content<?>> list = null;
        var capability = subject.capability();
        if (capability == ItemRecipeInfo.INSTANCE) list = subject.io() == IO.OUT ? recipe.itemOutputs : recipe.itemInputs;
        else if (capability == FluidRecipeInfo.INSTANCE) list = subject.io() == IO.OUT ? recipe.fluidOutputs : recipe.fluidInputs;
        if (list == null || subject.index() >= list.size()) return null;
        return list.get(subject.index());
    }

    public static Component conditionText(MachineIssue issue) {
        var recipe = issue.recipe();
        int index = issue.subject().index();
        if (recipe == null || index < 0 || index >= recipe.conditions.length) return dash();
        var condition = recipe.conditions[index];
        if (issue.a() != 1) return tooltip(condition);
        MutableComponent text = null;
        for (var other : recipe.conditions) {
            if (!other.isOr() || other.getClass() != condition.getClass()) continue;
            if (text == null) text = Component.empty().append(tooltip(other));
            else text.append(Component.translatable("gtceu.issue.or")).append(tooltip(other));
        }
        return text == null ? tooltip(condition) : text;
    }

    private static Component dash() {
        return Component.literal("—");
    }

    private static Component tooltip(RecipeCondition condition) {
        var tooltip = condition.getTooltips();
        return tooltip == null ? dash() : tooltip;
    }
}
