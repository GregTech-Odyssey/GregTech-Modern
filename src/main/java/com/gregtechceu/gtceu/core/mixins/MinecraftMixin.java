package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * 资源重载时在第一时机失效贴图路径缓存。一次重载是 图集 stitch → 模型 bake，
 * 失效只要晚于 stitch，本次就已经按旧路径注册完 sprite 了。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "reloadResourcePacks(Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void gtceu$invalidateMaterialTextureCachesOnReload(boolean force,
                                                              CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MaterialIconType.invalidateTextureCaches();
    }
}
