package com.gregtechceu.gtceu.integration.map;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.map.xaeros.XaerosRenderer;
import com.gregtechceu.gtceu.utils.collection.O2OOpenCacheHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * A map renderer that groups together multiple map mods' renderers for supporting multiple simultaneous mods.
 */
public class GroupingMapRenderer extends GenericMapRenderer {

    private static final GroupingMapRenderer instance;

    static {
        Map<String, GenericMapRenderer> renderers = new O2OOpenCacheHashMap<>();
        var toggle = ConfigHolder.INSTANCE.compat.minimap.toggle;
        if (toggle.xaerosMapIntegration && GTCEu.isModLoaded(GTValues.MODID_XAEROS_MINIMAP)) {
            renderers.put(GTValues.MODID_XAEROS_MINIMAP, new XaerosRenderer());
        }
        instance = new GroupingMapRenderer(renderers);
    }

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
    public void clear() {
        for (GenericMapRenderer renderer : rendererList) {
            renderer.clear();
        }
    }

    public static GroupingMapRenderer getInstance() {
        return GroupingMapRenderer.instance;
    }

    public Map<String, GenericMapRenderer> getRenderers() {
        return this.renderers;
    }
}
