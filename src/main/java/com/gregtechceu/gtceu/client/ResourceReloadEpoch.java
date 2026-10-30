package com.gregtechceu.gtceu.client;

/**
 * 模型/贴图缓存的资源代际号。LDLib 只在图集 stitch 之前清一次缓存，而缓存是渲染时惰性 bake 填的，
 * 重载窗口内填进去的会固化旧图集的 sprite；本代际号在方块图集 {@code upload} 返回时推进。
 */
public final class ResourceReloadEpoch {

    private static volatile int epoch = 0;

    private ResourceReloadEpoch() {}

    public static int get() {
        return epoch;
    }

    public static void onAtlasReloaded() {
        epoch++;
    }
}
