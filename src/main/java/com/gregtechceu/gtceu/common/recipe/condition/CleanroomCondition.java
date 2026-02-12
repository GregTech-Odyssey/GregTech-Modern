package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

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
    public boolean testCondition(@NotNull GTRecipeDefinition recipe, @NotNull RecipeLogic recipeLogic) {
        if (!ConfigHolder.INSTANCE.machines.enableCleanroom) return true;
        MetaMachine machine = recipeLogic.getMachine();
        if (machine instanceof ICleanroomReceiver receiver && this.cleanroom != null) {
            if (ConfigHolder.INSTANCE.machines.cleanMultiblocks && machine instanceof IMultiController) return true;
            ICleanroomProvider provider = receiver.getCleanroom();
            if (provider == null) return false;
            return provider.isClean() && provider.getTypes().contains(this.cleanroom);
        }
        return true;
    }
}
