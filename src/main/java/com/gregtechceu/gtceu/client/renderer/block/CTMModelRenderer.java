package com.gregtechceu.gtceu.client.renderer.block;

import com.gregtechceu.gtceu.client.ResourceReloadEpoch;

import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class CTMModelRenderer extends IModelRenderer {

    /**
     * 这份缓存对应的资源重载代际号，见 {@link ResourceReloadEpoch}。
     * <p>
     * LDLib 只在 {@code onPrepareTextureAtlas}（图集 stitch 之前）清缓存，而缓存的填充是
     * 渲染时惰性 bake 的。两者之间隔着整个重载过程，中间任何一次渲染都会把旧图集的
     * sprite 固化进缓存，此后不再失效。用代际号兜一层，重载结束后强制重建。
     */
    private int gtceu$cacheEpoch = -1;

    public CTMModelRenderer(ResourceLocation modelLocation) {
        super(modelLocation);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean reBakeCustomQuads() {
        return true;
    }

    @Override
    public float reBakeCustomQuadsOffset() {
        return 0.000f;
    }

    /** 资源代际变了就丢掉按资源集固化的模型缓存。子类可覆盖 {@link #gtceu$invalidateExtraCaches()} 追加清理。 */
    @OnlyIn(Dist.CLIENT)
    protected void gtceu$ensureFresh() {
        int current = ResourceReloadEpoch.get();
        if (current == gtceu$cacheEpoch) return;
        gtceu$cacheEpoch = current;
        if (blockModels != null) blockModels.clear();
        if (modelCaches != null) modelCaches.clear();
        itemModel = null;
        gtceu$invalidateExtraCaches();
    }

    @OnlyIn(Dist.CLIENT)
    protected void gtceu$invalidateExtraCaches() {}
}
