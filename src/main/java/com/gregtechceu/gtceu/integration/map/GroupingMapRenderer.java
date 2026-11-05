package com.gregtechceu.gtceu.integration.map;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.map.xaeros.XaerosRenderer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import lombok.Getter;

import java.util.Map;

/**
 * A map renderer that groups together multiple map mods' renderers for supporting multiple simultaneous mods.
 */
public class GroupingMapRenderer extends GenericMapRenderer {

    private static final GroupingMapRenderer instance;

    /** 勘探结果变化后置脏，由客户端 tick 末尾统一刷新一次，见 {@link #markRefreshPending()}。 */
    private static boolean refreshPending;

    static {
        Map<String, GenericMapRenderer> renderers = new O2OOpenCacheHashMap<>();
        var toggle = ConfigHolder.INSTANCE.compat.minimap.toggle;
        if (toggle.xaerosMapIntegration && GTCEu.isModLoaded(GTValues.MODID_XAEROS_MINIMAP)) {
            renderers.put(GTValues.MODID_XAEROS_MINIMAP, new XaerosRenderer());
        }
        instance = new GroupingMapRenderer(renderers);
    }

    @Getter
    private final Map<String, GenericMapRenderer> renderers;
    private final GenericMapRenderer[] rendererList;

    public GroupingMapRenderer(Map<String, GenericMapRenderer> renderers) {
        super(false);
        this.renderers = renderers;
        this.rendererList = renderers.values().toArray(GenericMapRenderer[]::new);
    }

    @Override
    public boolean addMarker(String name, String id, ResourceKey<Level> dim, ChunkPos pos, ProspectorMode.FluidInfo fluid) {
        boolean value = false;
        for (GenericMapRenderer renderer : rendererList) {
            value |= renderer.addMarker(name, id, dim, pos, fluid);
        }
        return value;
    }

    @Override
    public boolean addMarker(String name, ResourceKey<Level> dim, GeneratedVeinMetadata vein, String id) {
        boolean value = false;
        for (GenericMapRenderer renderer : rendererList) {
            value |= renderer.addMarker(name, dim, vein, id);
        }
        return value;
    }

    @Override
    public boolean addMarker(String name, String id, ResourceKey<Level> dim, ChunkPos pos,
                             ProspectorMode.OreInfo[] ores) {
        boolean value = false;
        for (GenericMapRenderer renderer : rendererList) {
            value |= renderer.addMarker(name, id, dim, pos, ores);
        }
        return value;
    }

    @Override
    public boolean removeMarker(ResourceKey<Level> dim, String id) {
        boolean value = false;
        for (GenericMapRenderer renderer : rendererList) {
            value |= renderer.removeMarker(dim, id);
        }
        return value;
    }

    @Override
    public boolean doShowLayer(String name) {
        boolean value = false;
        for (GenericMapRenderer renderer : rendererList) {
            value |= renderer.doShowLayer(name);
        }
        return value;
    }

    @Override
    public void setLayerActive(String name, boolean active) {
        for (GenericMapRenderer renderer : rendererList) {
            renderer.setLayerActive(name, active);
        }
    }

    @Override
    public void refreshMarkers() {
        for (GenericMapRenderer renderer : rendererList) {
            renderer.refreshMarkers();
        }
    }

    @Override
    public void clear() {
        for (GenericMapRenderer renderer : rendererList) {
            renderer.clear();
        }
        // 元素表清空后，地图 mod 里按旧内容算出的高亮哈希仍在，一起丢掉
        refreshMarkers();
    }

    /**
     * 勘探逐区块写入缓存，这里只置脏，由客户端 tick 末尾统一刷新一次；否则一次勘探里每个变动的区块
     * 都会触发一遍全维度 {@code clearCachedHashes()}。
     */
    public static void markRefreshPending() {
        refreshPending = true;
    }

    /** 客户端 tick 末尾调用；没有置脏时只是一次字段读。 */
    public static void flushPendingRefresh() {
        if (!refreshPending) return;
        refreshPending = false;
        instance.refreshMarkers();
    }

    public static GroupingMapRenderer getInstance() {
        return GroupingMapRenderer.instance;
    }
}
