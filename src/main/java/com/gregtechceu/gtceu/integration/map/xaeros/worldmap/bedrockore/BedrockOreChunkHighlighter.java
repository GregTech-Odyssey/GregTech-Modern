package com.gregtechceu.gtceu.integration.map.xaeros.worldmap.bedrockore;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.builtin.BedrockOreRenderLayer;
import com.gregtechceu.gtceu.integration.map.xaeros.XaerosRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

public class BedrockOreChunkHighlighter extends ChunkHighlighter {

    private static final int FILL_OPACITY = 25;
    private static final int BORDER_OPACITY = 50;

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

        int centerColor = color | 255 * FILL_OPACITY / 100;
        int sideColor = color | 255 * BORDER_OPACITY / 100;

        this.resultStore[0] = centerColor;
        this.resultStore[1] = topOre == null ? sideColor : centerColor;
        this.resultStore[2] = rightOre == null ? sideColor : centerColor;
        this.resultStore[3] = bottomOre == null ? sideColor : centerColor;
        this.resultStore[4] = leftOre == null ? sideColor : centerColor;
        return this.resultStore;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!isEnabled()) return 0;

        long accumulator = FILL_OPACITY;

        for (int x = regionX << 5; x < (regionX + 1) << 5; x++) {
            for (int z = regionZ << 5; z < (regionZ + 1) << 5; z++) {
                ProspectorMode.OreInfo[] ores = XaerosRenderer.bedrockOreElements.get(dimension, new ChunkPos(x, z));
                if (ores == null || ores.length == 0) {
                    accumulator *= 37L;
                    accumulator = accumulator * 37L + x;
                    accumulator = accumulator * 37L + z;
                    continue;
                }

                for (ProspectorMode.OreInfo ore : ores) {
                    accumulator += ore.material().getName().hashCode();
                    accumulator *= 37L;
                    accumulator += ore.yield();
                    accumulator *= 37L;
                    accumulator += ore.weight();
                    accumulator *= 37L;
                    accumulator += ore.left();
                    accumulator *= 37L;
                }
                accumulator = accumulator * 37L + x;
                accumulator = accumulator * 37L + z;
            }
        }
        accumulator = accumulator * 37L + (long) BORDER_OPACITY;

        return (int) (accumulator >> 32) * 37 + (int) (accumulator);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ProspectorMode.OreInfo[] ores = XaerosRenderer.bedrockOreElements.get(dimension, new ChunkPos(chunkX, chunkZ));
        return isEnabled() && ores != null && ores.length > 0;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int x, int z) {
        return null;
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int x, int z) {
        if (!isEnabled()) {
            return null;
        }
        var ores = XaerosRenderer.bedrockOreElements.get(dimension, new ChunkPos(x, z));
        if (ores == null || ores.length == 0) {
            return null;
        }
        return BedrockOreRenderLayer.getTooltip(ores).stream().reduce(Component.empty(), (c1, c2) -> {
            if (c1.getString().isEmpty()) {
                return c2;
            }
            if (c2.getString().isEmpty()) {
                return c1;
            }
            // Xaeros requires spaces before/after newlines (see xaero.map.misc.TextSplitter)
            return ((MutableComponent) c1).append(" \n ").append(c2);
        });
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX,
                                                 int blockZ, int width) {}
}
