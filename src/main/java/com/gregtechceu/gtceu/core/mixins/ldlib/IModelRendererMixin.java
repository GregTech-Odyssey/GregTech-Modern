package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.client.ResourceReloadEpoch;

import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

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
 * 给 LDLib 的 {@link IModelRenderer} 统一加一层「资源代际失效」。
 * <p>
 * LDLib 只在 {@code onPrepareTextureAtlas}（图集 stitch <b>之前</b>）清 {@code blockModels} /
 * {@code modelCaches} / {@code itemModel}，而这三份缓存是渲染时惰性 bake 填的。两者之间隔着整个重载过程，
 * 中间任何一次渲染都会把旧图集的 {@code TextureAtlasSprite}（UV 已固化进 quad）写进缓存，此后不再失效。
 * <p>
 * 为什么必须打在基类上：GTM 和 GTOCore 里有大量 {@code overlayModel = new IModelRenderer(...)}
 * 这样的 overlay 图层（整面的 hatch / bus 覆盖层），它们不是 {@code CTMModelRenderer} 的子类，
 * 因此拿不到我们之前给机器主模型加的那层保护。表现就是「方块本体正常，但某几个面是别的机器的贴图」，
 * 物品栏里尤其明显 —— GUI 的每个物品都会走 overlay，命中污染窗口的概率最高。
 * <p>
 * 让缓存按 {@link ResourceReloadEpoch} 作废，就能保证重载结束后第一次访问必定重新 bake。
 */
@Mixin(value = IModelRenderer.class, remap = false)
public abstract class IModelRendererMixin {

    @Shadow(remap = false)
    protected BakedModel itemModel;

    @Shadow(remap = false)
    protected Map<Direction, BakedModel> blockModels;

    @Shadow(remap = false)
    protected Map<ModelState, BakedModel> modelCaches;

    @Unique
    private int gtceu$cacheEpoch = -1;

    @Unique
    private void gtceu$invalidateIfStale() {
        int current = ResourceReloadEpoch.get();
        if (current == gtceu$cacheEpoch) return;
        gtceu$cacheEpoch = current;
        if (blockModels != null) blockModels.clear();
        if (modelCaches != null) modelCaches.clear();
        itemModel = null;
    }

    @Inject(method = "getRotatedModel", at = @At("HEAD"), remap = false)
    private void gtceu$onGetRotatedModel(Direction direction, CallbackInfoReturnable<BakedModel> cir) {
        gtceu$invalidateIfStale();
    }

    @Inject(method = "getItemBakedModel()Lnet/minecraft/client/resources/model/BakedModel;", at = @At("HEAD"), remap = false)
    private void gtceu$onGetItemBakedModel(CallbackInfoReturnable<BakedModel> cir) {
        gtceu$invalidateIfStale();
    }

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void gtceu$onRenderModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                                     @Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                     CallbackInfoReturnable<List<net.minecraft.client.renderer.block.model.BakedQuad>> cir) {
        gtceu$invalidateIfStale();
    }
}
