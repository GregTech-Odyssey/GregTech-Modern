package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.client.ResourceReloadEpoch;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块图集上传完成后推进模型缓存代际号。{@code upload} 同时替换 {@code texturesByName} 与
 * {@code animatedTextures}，其 RETURN 之后旧 sprite 实例既不指向新图集、也不再被动画驱动。
 */
@Mixin(TextureAtlas.class)
public class TextureAtlasMixin {

    @Shadow
    @Final
    private ResourceLocation location;

    @Inject(method = "upload", at = @At("RETURN"))
    private void gtceu$bumpEpochOnAtlasUpload(CallbackInfo ci) {
        // 一次重载会给十多个图集各调一次 upload，只认方块图集，否则会重复触发全量重建。
        if (TextureAtlas.LOCATION_BLOCKS.equals(this.location)) {
            ResourceReloadEpoch.onAtlasReloaded();
        }
    }
}
