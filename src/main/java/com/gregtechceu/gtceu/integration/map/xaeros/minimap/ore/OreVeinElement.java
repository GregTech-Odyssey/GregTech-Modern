package com.gregtechceu.gtceu.integration.map.xaeros.minimap.ore;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;

import net.minecraft.client.Minecraft;

import lombok.Getter;

@Getter
public class OreVeinElement {

    private GeneratedVeinMetadata vein;
    private final String name;
    private final int cachedNameLength;
    private final OreRenderLayer.Icon icon;

    public OreVeinElement(GeneratedVeinMetadata vein, String name) {
        this.vein = vein;
        this.name = name;
        this.cachedNameLength = Minecraft.getInstance().font.width(this.getName());
        this.icon = OreRenderLayer.getIcon(vein);
    }
}
