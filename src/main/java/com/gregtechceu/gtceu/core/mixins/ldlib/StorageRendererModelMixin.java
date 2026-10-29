package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.feature.IStorageAppearance;
import com.gregtechceu.gtceu.client.renderer.machine.StorageMachineRenderer;

import com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = LDLRendererModel.RendererBakedModel.class, remap = false)
public abstract class StorageRendererModelMixin implements BakedModel {

    private static final ChunkRenderTypeSet TRANSLUCENT = ChunkRenderTypeSet.of(RenderType.translucent());
    private static final ChunkRenderTypeSet TRIPWIRE = ChunkRenderTypeSet.of(RenderType.tripwire());
    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutout());
    private static final ChunkRenderTypeSet CUTOUT_MIPPED = ChunkRenderTypeSet.of(RenderType.cutoutMipped());

    @Override
    @SuppressWarnings("removal")
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, ModelData data) {
        var level = data.get(LDLRendererModel.RendererBakedModel.WORLD);
        var pos = data.get(LDLRendererModel.RendererBakedModel.POS);
        if (level != null && pos != null && state.getBlock() instanceof MetaMachineBlock machineBlock &&
                machineBlock.getRenderer(state) instanceof StorageMachineRenderer &&
                machineBlock.getMachine(level, pos) instanceof IStorageAppearance storage) {
            var block = storage.getStorageAppearance().getBlock();
            if (block != Blocks.AIR && !(block instanceof MetaMachineBlock)) {
                var sample = block.defaultBlockState();
                var types = Minecraft.getInstance().getBlockRenderer().getBlockModel(sample)
                        .getRenderTypes(sample, random, ModelData.EMPTY);
                // The storage model copies one sprite per face, so render it once even for a layered sample model.
                if (types.contains(RenderType.translucent())) return TRANSLUCENT;
                if (types.contains(RenderType.tripwire())) return TRIPWIRE;
                if (types.contains(RenderType.cutout())) return CUTOUT;
                if (types.contains(RenderType.cutoutMipped())) return CUTOUT_MIPPED;
            }
        }
        return ItemBlockRenderTypes.getRenderLayers(state);
    }
}
