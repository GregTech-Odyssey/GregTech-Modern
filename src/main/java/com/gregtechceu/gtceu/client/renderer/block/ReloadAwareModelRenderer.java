package com.gregtechceu.gtceu.client.renderer.block;

import com.gregtechceu.gtceu.client.ResourceReloadEpoch;

import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

/**
 * 按 {@link ResourceReloadEpoch} 失效模型缓存的 {@link IModelRenderer}。LDLib 只在图集 stitch 之前清缓存，
 * 重载窗口内惰性 bake 的模型会固化旧 sprite；GT 的模型渲染器（含 overlay 内层实例）都应使用本类。
 */
@SuppressWarnings("removal")
public class ReloadAwareModelRenderer extends IModelRenderer {

    private volatile int cacheEpoch = -1;

    public ReloadAwareModelRenderer(ResourceLocation modelLocation) {
        super(modelLocation);
    }

    @OnlyIn(Dist.CLIENT)
    protected final void invalidateStaleCaches() {
        int current = ResourceReloadEpoch.get();
        if (current == cacheEpoch) return;
        itemModel = null;
        if (blockModels != null) blockModels.clear();
        if (modelCaches != null) modelCaches.clear();
        onCachesInvalidated();
        cacheEpoch = current;
    }

    @OnlyIn(Dist.CLIENT)
    protected void onCachesInvalidated() {}

    @Override
    @Nullable
    @OnlyIn(Dist.CLIENT)
    protected BakedModel getItemBakedModel() {
        invalidateStaleCaches();
        return super.getItemBakedModel();
    }

    @Override
    @Nullable
    @OnlyIn(Dist.CLIENT)
    protected BakedModel getBlockBakedModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                                            @Nullable BlockState state) {
        invalidateStaleCaches();
        return super.getBlockBakedModel(level, pos, state);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BakedModel getRotatedModel(Direction frontFacing) {
        invalidateStaleCaches();
        return super.getRotatedModel(frontFacing);
    }
}
