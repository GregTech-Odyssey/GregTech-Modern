package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.issue.IssueStage;
import com.gregtechceu.gtceu.api.machine.issue.IssueText;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.Nullable;

public class CleanroomCondition extends RecipeCondition {

    private static final Reference2ReferenceOpenHashMap<CleanroomType, CleanroomCondition> CACHE = new Reference2ReferenceOpenHashMap<>();
    public final CleanroomType cleanroom;

    public CleanroomCondition(boolean isReverse, CleanroomType cleanroom) {
        super(isReverse);
        this.cleanroom = cleanroom;
    }

    public static CleanroomCondition get(CleanroomType cleanroom) {
        return CACHE.computeIfAbsent(cleanroom, k -> new CleanroomCondition(false, cleanroom));
    }

    @Override
    public Component getTooltips() {
        return cleanroom == null ? null : Component.translatable("gtceu.recipe.cleanroom", Component.translatable(cleanroom.getTranslationKey()));
    }

    @Override
    public IssueType getIssueType() {
        return GTIssues.CLEANROOM;
    }

    @Override
    public void reportFailure(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe, int index) {
        holder.reportIssue(GTIssues.CLEANROOM, IssueStage.CONDITION, IO.NONE, null, index, 0, isReverse ? 0 : failureCode(holder), recipe);
    }

    @Override
    public @Nullable Component describeCurrent(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (isReverse || cleanroom == null) return null;
        int code = failureCode(holder);
        return code == 0 ? null : reasonText(code, cleanroom);
    }

    private int failureCode(IRecipeHandlerHolder holder) {
        if (!(holder.self() instanceof ICleanroomReceiver receiver)) return 0;
        ICleanroomProvider provider = receiver.getCleanroom();
        if (provider == null) return 1;
        if (!provider.isClean()) return 2;
        if (cleanroom != null && !provider.getTypes().contains(cleanroom)) return 3;
        return 0;
    }

    public static Component reasonText(MachineIssue issue) {
        var recipe = issue.recipe();
        int index = issue.subject().index();
        CleanroomType type = null;
        if (recipe != null && index >= 0 && index < recipe.conditions.length && recipe.conditions[index] instanceof CleanroomCondition condition) type = condition.cleanroom;
        if (type == null || issue.b() == 0) return IssueText.conditionText(issue);
        return reasonText((int) issue.b(), type);
    }

    private static Component reasonText(int code, CleanroomType type) {
        var name = Component.translatable(type.getTranslationKey());
        return switch (code) {
            case 1 -> Component.translatable("gtceu.issue.cleanroom.missing", name);
            case 2 -> Component.translatable("gtceu.issue.cleanroom.dirty");
            default -> Component.translatable("gtceu.issue.cleanroom.type", name);
        };
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (!ConfigHolder.INSTANCE.machines.enableCleanroom) return true;
        MetaMachine machine = holder.self();
        if (machine instanceof ICleanroomReceiver receiver && this.cleanroom != null) {
            if (ConfigHolder.INSTANCE.machines.cleanMultiblocks && machine instanceof IMultiController) return true;
            ICleanroomProvider provider = receiver.getCleanroom();
            if (provider == null) return false;
            return provider.isClean() && provider.getTypes().contains(this.cleanroom);
        }
        return true;
    }
}
