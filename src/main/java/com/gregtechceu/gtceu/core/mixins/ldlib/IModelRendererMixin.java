package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.client.ResourceReloadEpoch;
import com.gregtechceu.gtceu.client.model.IModelCacheInvalidatable;

import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * LDLib 只在 {@code onPrepareTextureAtlas}（图集 stitch 之前）清一次缓存，而缓存是渲染时惰性 bake 填的，
 * 重载窗口内填进去的会固化旧图集的 sprite。
 */
@Mixin(value = IModelRenderer.class, remap = false)
public abstract class IModelRendererMixin implements IModelCacheInvalidatable {

    @Shadow(remap = false)
    protected BakedModel itemModel;

    @Shadow(remap = false)
    protected Map<Direction, BakedModel> blockModels;

    @Shadow(remap = false)
    protected Map<ModelState, BakedModel> modelCaches;

    @Unique
    private int gtceu$cacheEpoch = -1;

    @Override
    public void invalidateStaleCaches() {
        int current = ResourceReloadEpoch.get();
        if (current == gtceu$cacheEpoch) return;
        gtceu$cacheEpoch = current;
        if (blockModels != null) blockModels.clear();
        if (modelCaches != null) modelCaches.clear();
        itemModel = null;
        onCachesInvalidated();
    }

    @Inject(method = "getRotatedModel", at = @At("HEAD"), remap = false)
    private void gtceu$onGetRotatedModel(Direction direction, CallbackInfoReturnable<BakedModel> cir) {
        invalidateStaleCaches();
    }

    @Inject(method = "getItemBakedModel()Lnet/minecraft/client/resources/model/BakedModel;", at = @At("HEAD"), remap = false)
    private void gtceu$onGetItemBakedModel(CallbackInfoReturnable<BakedModel> cir) {
        invalidateStaleCaches();
    }

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void gtceu$onRenderModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                                     @Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                     CallbackInfoReturnable<List<BakedQuad>> cir) {
        invalidateStaleCaches();
    }
}
