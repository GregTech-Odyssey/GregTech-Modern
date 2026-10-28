package com.gregtechceu.gtceu.client.renderer.block;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.client.ResourceReloadEpoch;
import com.gregtechceu.gtceu.common.block.LampBlock;

import com.lowdragmc.lowdraglib.client.model.custommodel.ICTMPredicate;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class LampRenderer extends IModelRenderer implements ICTMPredicate {

    /** 见 {@link CTMModelRenderer#gtceu$ensureFresh()}：直接继承 IModelRenderer，需要自己兜这一层。 */
    private int gtceu$cacheEpoch = -1;

    public LampRenderer(LampBlock lamp, BlockState state) {
        super(GTCEu.id("block/%s%s_lamp%s%s".formatted(
                lamp.color,
                lamp.bordered ? "" : "_borderless",
                state.getValue(LampBlock.LIGHT) ? "" : "_off",
                state.getValue(LampBlock.LIGHT) && state.getValue(LampBlock.BLOOM) ? "_bloom" : "")));
    }

    @OnlyIn(Dist.CLIENT)
    private void gtceu$ensureFresh() {
        int current = ResourceReloadEpoch.get();
        if (current == gtceu$cacheEpoch) return;
        gtceu$cacheEpoch = current;
        if (blockModels != null) blockModels.clear();
        if (modelCaches != null) modelCaches.clear();
        itemModel = null;
    }

    @Override
    public BakedModel getRotatedModel(Direction frontFacing) {
        gtceu$ensureFresh();
        return super.getRotatedModel(frontFacing);
    }

    @Override
    protected BakedModel getItemBakedModel() {
        gtceu$ensureFresh();
        return super.getItemBakedModel();
    }

    @Override
    public boolean isConnected(BlockAndTintGetter level, BlockState state, BlockPos pos,
                               BlockState sourceState, BlockPos sourcePos, Direction side) {
        var stateAppearance = state.getAppearance(level, pos, side, sourceState, sourcePos);
        var sourceStateAppearance = sourceState.getAppearance(level, sourcePos, side, state, pos);
        return stateAppearance.getBlock() == sourceStateAppearance.getBlock();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean useAO() {
        return true;
    }

    @Override
    public boolean reBakeCustomQuads() {
        return true;
    }

    @Override
    public float reBakeCustomQuadsOffset() {
        return 0.0f;
    }
}
