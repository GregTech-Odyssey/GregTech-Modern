package com.gregtechceu.gtceu.client;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.client.Minecraft;

/**
 * 模型/贴图缓存的「资源代际号」。
 * <p>
 * 需要它的原因：LDLib 的 {@code IModelRenderer} 会在渲染线程上惰性 bake 并缓存 {@code BakedModel}
 * （{@code blockModels} / {@code modelCaches} / {@code itemModel}），GTM 的机器渲染器、overlay 图层、
 * 管道精灵同样是「首次用时按当时那张图集取」。这些缓存在重载窗口里被填上时会固化旧图集的
 * {@code TextureAtlasSprite}（UV 已经写进 {@code BakedQuad}），而 LDLib 只在图集 stitch
 * <b>之前</b>清一次缓存，于是重载结束后缓存永远带着旧 UV：按旧 UV 去新图集取样，
 * 命中别人就是「串成别的方块的纹理」，落进空白区就是「紫黑格 / 变透明」。
 * <p>
 * 代际号只在 {@code TextureAtlas#reload} 返回时自增（见 {@code TextureAtlasMixin}）——
 * 那一时刻图集一定已经换成新的，不存在「先推进、后换图集」把旧 sprite 钉死的问题。
 * 之前试过的两种信号都不可靠：
 * <ul>
 * <li>{@code reloadResourcePacks} 返回的 future：实测它完成时 {@code ModelBakery} 还在建模型；</li>
 * <li>拿某个精灵的实例身份当令牌：1.20 的 {@code SpriteLoader} 会复用内容未变的 sprite，
 * 换包后实例可能一个都不变。</li>
 * </ul>
 * <p>
 * 另外，光让缓存在下次访问时重建还不够：已经建好的区块网格里存的是旧 quad，
 * 不重建就会继续按旧 UV 显示（表现为「切完包错乱，打掉重放才恢复」，物品本身每帧重算所以只闪一下）。
 * 所以换代之后还要让 {@code LevelRenderer} 重刷一次世界网格。
 */
public final class ResourceReloadEpoch {

    private static volatile int epoch = 0;
    private static volatile boolean needsRemesh = false;

    private ResourceReloadEpoch() {}

    public static int get() {
        return epoch;
    }

    /** 由 {@code TextureAtlasMixin} 在图集重载完成时调用（渲染线程）。 */
    public static void onAtlasReloaded() {
        epoch++;
        needsRemesh = true;
        GTCEu.LOGGER.info("[GTCEu] block atlas reloaded, model cache epoch -> {}", epoch);
    }

    /** 由客户端 tick（渲染线程、每帧渲染之前）调用：代际变了就重建一次世界网格。 */
    public static void tick() {
        if (!needsRemesh) return;
        needsRemesh = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null && minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }
}
