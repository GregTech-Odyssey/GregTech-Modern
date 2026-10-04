package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;

public class CleanroomCondition extends RecipeCondition {

    private static final Int2ReferenceOpenHashMap<CleanroomCondition> CACHE = new Int2ReferenceOpenHashMap<>();
    public final int minTier;

    public CleanroomCondition(boolean isReverse, int cleanroom) {
        super(isReverse);
        this.minTier = cleanroom;
    }

    public static CleanroomCondition get(int cleanroom) {
        return CACHE.computeIfAbsent(cleanroom, k -> new CleanroomCondition(false, cleanroom));
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("gtceu.recipe.cleanroom", ICleanroomProvider.getCleanroomTooltip(minTier));
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (!ConfigHolder.INSTANCE.machines.enableCleanroom) return true;
        MetaMachine machine = holder.self();
        if (machine instanceof ICleanroomReceiver receiver && this.minTier > 0) {
            if (ConfigHolder.INSTANCE.machines.cleanMultiblocks && machine instanceof IMultiController) return true;
            ICleanroomProvider provider = receiver.getCleanroom();
            if (provider == null) return false;
            return provider.isClean() && provider.getCleanroomTier() >= this.minTier;
        }
        return true;
    }
}
