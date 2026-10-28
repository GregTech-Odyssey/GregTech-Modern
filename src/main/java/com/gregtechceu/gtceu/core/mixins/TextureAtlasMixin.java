package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.client.ResourceReloadEpoch;

import net.minecraft.client.renderer.texture.TextureAtlas;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块图集换成新的一批 sprite 之后，推进模型缓存代际号，见 {@link ResourceReloadEpoch}。
 * <p>
 * 1.20.1 里图集重载的落点是 {@code TextureAtlas#upload(SpriteLoader.Preparations)}：
 * 它同时替换 {@code texturesByName} 和 {@code animatedTextures}（动画驱动表）。
 * 也就是说这个 RETURN 之后，旧的 {@code TextureAtlasSprite} 实例既不指向新图集、
 * 也不再被帧动画驱动 —— 世界网格里存着旧实例的方块就会「贴图正常但动画卡住」，
 * 要打掉重放（触发重建）才恢复。
 * <p>
 * 之前试过的两种信号都不可靠：{@code reloadResourcePacks} 的 future 完成得太早
 * （那时 ModelBakery 还在建模型）；用某个精灵的实例身份当令牌又会被 {@code SpriteLoader}
 * 的 sprite 复用骗过去。
 */
@Mixin(TextureAtlas.class)
public class TextureAtlasMixin {

    @Inject(method = "upload", at = @At("RETURN"))
    private void gtceu$bumpEpochOnAtlasUpload(CallbackInfo ci) {
        // 一次重载会给十多个图集（粒子、床、箱子、告示牌……）各调一次 upload。
        // 只认方块图集，否则代际号会被推几十次，跟着触发几十次全量重建网格，卡顿明显。
        if (TextureAtlas.LOCATION_BLOCKS.equals(((TextureAtlas) (Object) this).location())) {
            ResourceReloadEpoch.onAtlasReloaded();
        }
    }
}
