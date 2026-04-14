package com.gregtechceu.gtceu.integration.map.xaeros.minimap.bedrockore;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.xaeros.XaerosRenderer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.common.minimap.info.render.compile.InfoDisplayCompiler;

public class BedrockOreChunkHighlighter extends ChunkHighlighter {

    public BedrockOreChunkHighlighter() {
        super(false);
    }

    private boolean isEnabled() {
        return GroupingMapRenderer.getInstance().doShowLayer("bedrock_ore_veins");
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return isEnabled();
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!isEnabled()) return null;

        var dimensionMap = XaerosRenderer.bedrockOreElements.get(dimension);
        ProspectorMode.OreInfo[] ores = dimensionMap.get(new ChunkPos(chunkX, chunkZ));
        if (ores == null || ores.length == 0) return null;

        var topOre = dimensionMap.get(new ChunkPos(chunkX, chunkZ - 1));
        var rightOre = dimensionMap.get(new ChunkPos(chunkX + 1, chunkZ));
        var bottomOre = dimensionMap.get(new ChunkPos(chunkX, chunkZ + 1));
        var leftOre = dimensionMap.get(new ChunkPos(chunkX - 1, chunkZ));

        Material material = ores[0].material();
        int color = material.getMaterialARGB();
        color = (color & 0xFF) << 24 | (color >> 8 & 0xFF) << 16 | (color >> 16 & 0xFF) << 8;

        int fillOpacity = 25;
        int borderOpacity = 50;
        int centerColor = color | 255 * fillOpacity / 100;
        int sideColor = color | 255 * borderOpacity / 100;

        this.resultStore[0] = centerColor;
        this.resultStore[1] = topOre == null ? sideColor : centerColor;
        this.resultStore[2] = rightOre == null ? sideColor : centerColor;
        this.resultStore[3] = bottomOre == null ? sideColor : centerColor;
        this.resultStore[4] = leftOre == null ? sideColor : centerColor;
        return this.resultStore;
    }

    @Override
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX,
                                          int chunkZ, int width) {}

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ProspectorMode.OreInfo[] ores = XaerosRenderer.bedrockOreElements.get(dimension, new ChunkPos(chunkX, chunkZ));
        return isEnabled() && ores != null && ores.length > 0;
    }
}
