package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datastream.codec.ValueOps;

public interface IAssemblyLine extends IWorkableMultiController {

    RenderState getRenderState();

    @Override
    default boolean handleRecipeOutput(GTRecipe recipe) {
        getRenderState().markAsChanged();
        self().requestSync();
        return IWorkableMultiController.super.handleRecipeOutput(recipe);
    }

    @Override
    default boolean handleTickRecipe(GTRecipe recipe) {
        getRenderState().markAsChanged();
        self().requestSync();
        return IWorkableMultiController.super.handleTickRecipe(recipe);
    }

    @OnlyIn(Dist.CLIENT)
    default float getRendererLineCount(float partialTick) {
        var renderState = getRenderState();
        if (renderState.duration <= 0) return 0;
        float progress = renderState.progress + Math.max(0, GTValues.CLIENT_TIME - renderState.syncTime) + partialTick;
        return Mth.clamp(progress / renderState.duration, 0, 1) * renderState.inputs;
    }

    final class RenderState extends AbstractDataSerializable {

        private final RecipeLogic recipeLogic;

        public int progress;
        public int duration;
        public int inputs;
        public int syncTime;

        public RenderState(RecipeLogic recipeLogic) {
            this.recipeLogic = recipeLogic;
        }

        @Override
        public void writeBuffer(LogicalSide side, FriendlyByteBuf buffer) {
            progress = recipeLogic.getProgress();
            duration = recipeLogic.getMaxProgress();
            var recipe = recipeLogic.getLastRecipe();
            inputs = recipe == null ? 0 : Math.min(17, Math.max(recipe.itemInputs.size(), recipe.fluidInputs.size()));
            buffer.writeVarInt(progress);
            buffer.writeVarInt(duration);
            buffer.writeVarInt(inputs);
        }

        @Override
        public void readBuffer(LogicalSide side, FriendlyByteBuf buffer) {
            progress = buffer.readVarInt();
            duration = buffer.readVarInt();
            inputs = buffer.readVarInt();
            syncTime = GTValues.CLIENT_TIME;
        }

        @Override
        public Object writeValue(ValueOps ops) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void readValue(Object data, ValueOps ops) {
            throw new UnsupportedOperationException();
        }
    }
}
