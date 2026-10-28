package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * 资源重载（游戏内启用/禁用材质包，等价于 F3+T）时，在第一时机丢弃按资源集缓存的贴图路径。
 * <p>
 * 为什么必须在这里而不是 {@code ModelManager#reload}：一次重载的顺序是
 * <ol>
 * <li>重建 ResourceManager（收集 pack 列表）</li>
 * <li>{@code TextureManager} 重载 → 图集 stitch → {@code TextureStitchEvent.Pre}，
 * 机器渲染器在这里用 {@code ResourceHelper.isResourceExist} 和材质贴图路径缓存决定注册哪些 sprite</li>
 * <li>{@code ModelManager#reload} → 模型 bake</li>
 * </ol>
 * 第 2 步早于第 3 步，若把失效放在第三步，本次重载已经用旧路径注册完 sprite 了，
 * 表现就是「第一次 F3+T 不管用、再按一次才对」。{@code reloadResourcePacks} 的 HEAD 是整条链路上
 * 唯一保证早于第 1 步的位置，上游同名修复（PR #571）也是挂在这里。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "reloadResourcePacks(Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void gtceu$invalidateMaterialTextureCachesOnReload(boolean force,
                                                              CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MaterialIconType.invalidateTextureCaches();
    }
}
