package com.gregtechceu.gtceu.integration.map.layer.builtin;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.integration.map.GenericMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.MapRenderLayer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BedrockOreRenderLayer extends MapRenderLayer {

    public BedrockOreRenderLayer(String key, GenericMapRenderer renderer) {
        super(key, renderer);
    }

    public static String getId(ProspectorMode.OreInfo[] ores, ChunkPos pos) {
        return "bedrock_ores_veins@[" + pos.x + "," + pos.z + "]";
    }

    public static Component getName(ProspectorMode.OreInfo[] ores) {
        if (ores == null || ores.length == 0) {
            return Component.translatable("gtceu.minimap.bedrock_ore.unknown");
        }
        return Component.translatable(ores[0].material().getUnlocalizedName());
    }

    public static List<Component> getTooltip(ProspectorMode.OreInfo[] ores) {
        final List<Component> tooltip = new ArrayList<>();
        if (ores == null || ores.length == 0) {
            return tooltip;
        }

        tooltip.add(Component.translatable("gtceu.minimap.bedrock_ore.title").append("\n"));

        int totalWeight = Arrays.stream(ores).mapToInt(ProspectorMode.OreInfo::weight).sum();
        for (ProspectorMode.OreInfo ore : ores) {
            float chance = (float) ore.weight() / totalWeight * 100;
            tooltip.add(Component.translatable(ore.material().getUnlocalizedName())
                    .append(" (%.1f%%)".formatted(chance))
                    .append(" --- %s (%s%%)".formatted(ore.yield(), ore.left()))
                    .append("\n"));
        }

        return tooltip;
    }
}
