package com.gregtechceu.gtceu.integration.map.xaeros.worldmap.ore;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.WaypointManager;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import lombok.Getter;

@Getter
public class OreVeinElement {

    private GeneratedVeinMetadata vein;
    private final String name;
    private final int cachedNameLength;
    private final OreRenderLayer.Icon icon;

    public OreVeinElement(GeneratedVeinMetadata vein, String name, OreRenderLayer.Icon icon) {
        this.vein = vein;
        this.name = name;
        this.cachedNameLength = Minecraft.getInstance().font.width(this.getName());
        this.icon = icon;
    }

    public void onMouseSelect() {
        // TODO generalize to all possible layer types
        BlockPos center = vein.center();
        WaypointManager.toggleWaypoint("ore_veins", name, icon.color(), null, center.getX(), center.getY(), center.getZ());
    }

    public void toggleDepleted() {
        vein.depleted(!vein.depleted());
    }
}
