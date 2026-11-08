package com.gregtechceu.gtceu.integration.map.xaeros;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.gto.fastcollection.fastutil.O2ZOpenCacheHashMap;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.common.minimap.write.MinimapWriter;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.map.WorldMapSession;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

public class XaerosMapPlugin {

    public static boolean isActive = false;

    public static final O2ZOpenCacheHashMap<String> OPTIONS = new O2ZOpenCacheHashMap<>();

    public static void init() {
        isActive = true;
    }

    public static void toggleOption(String name, boolean active) {
        OPTIONS.put(name, active);
        requestHighlightRefresh();
    }

    /**
     * Drops the cached highlight hashes so markers whose data changed after the map cached them are repainted.
     */
    public static void requestHighlightRefresh() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session != null) {
            MinimapWriter write = session.getProcessor().getMinimapWriter();
            DimensionHighlighterHandler dimHighlightHandler = write == null ? null : write.getDimensionHighlightHandler();
            if (dimHighlightHandler != null) {
                dimHighlightHandler.requestRefresh();
            }
        }

        if (ConfigHolder.INSTANCE.compat.minimap.toggle.xaerosMapIntegration &&
                GTCEu.isModLoaded(GTValues.MODID_XAEROS_WORLDMAP)) {
            WorldMapSession worldSession = WorldMapSession.getCurrentSession();
            if (worldSession == null) return;
            MapWorld world = worldSession.getMapProcessor().getMapWorld();
            if (world == null) return;
            for (MapDimension mapDim : world.getDimensionsList()) {
                mapDim.getHighlightHandler().clearCachedHashes();
            }
        }
    }

    public static boolean getOptionValue(String name) {
        return OPTIONS.getBoolean(name);
    }
}
