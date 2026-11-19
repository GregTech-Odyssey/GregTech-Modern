package com.gregtechceu.gtceu.integration.map.layer.builtin;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.map.GenericMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.MapRenderLayer;
import com.gregtechceu.gtceu.integration.xei.widgets.GTOreVeinWidget;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OreRenderLayer extends MapRenderLayer {

    public OreRenderLayer(String key, GenericMapRenderer renderer) {
        super(key, renderer);
    }

    public static String getId(GeneratedVeinMetadata vein) {
        BlockPos center = vein.center();
        return "ore_veins@[" + center.getX() + "," + center.getY() + "," + center.getZ() + "]";
    }

    public static MutableComponent getName(GeneratedVeinMetadata vein) {
        // noinspection ConstantValue IDK, it crashed
        if (vein == null || vein.definition() == null ||
                GTRegistries.ORE_VEINS.getKey(vein.definition()) == null) {
            return Component.translatable("gtceu.minimap.ore_vein.depleted");
        }
        return Component.translatable("gtceu.jei.ore_vein." + GTOreVeinWidget.getOreName(vein.definition()));
    }

    public static @Nullable Material getMaterial(@NotNull GeneratedVeinMetadata vein) {
        Material firstMaterial = null;
        if (!vein.definition().indicatorGenerators().isEmpty()) {
            var blockOrMaterial = vein.definition().indicatorGenerators().getFirst().block();
            firstMaterial = blockOrMaterial == null ? null : blockOrMaterial.map(
                    state -> {
                        var matStack = ChemicalHelper.getMaterialStack(state.getBlock());
                        return matStack.isEmpty() ? null : matStack.material();
                    },
                    Function.identity());
        }
        if (firstMaterial == null) {
            var materials = vein.definition().veinGenerator().getAllMaterials();
            firstMaterial = materials.isEmpty() ? null : materials.getFirst();
        }
        return firstMaterial;
    }

    /** Selects the first material, or the first block for a vein without materials. */
    public static Icon getIcon(GeneratedVeinMetadata vein) {
        var generator = vein.definition().veinGenerator();
        var materials = generator.getAllMaterials();
        if (!materials.isEmpty()) {
            var material = materials.getFirst();
            return new Icon(material, null, material.getMaterialARGB());
        }
        var block = generator.getAllEntries().getFirst().mapToBlockState();
        return new Icon(null, block, block.getBlock().defaultMapColor().col | 0xFF000000);
    }

    /** Exactly one of material and block is present; color is ARGB. */
    public record Icon(@Nullable Material material, @Nullable BlockState block, int color) {}

    public static List<Component> getTooltip(String name, GeneratedVeinMetadata vein) {
        final List<Component> tooltip = new ArrayList<>();
        var title = Component.literal(name);
        if (vein.depleted()) {
            title.append(" (").append(Component.translatable("gtceu.minimap.ore_vein.depleted")).append(")");
        }
        tooltip.add(title);

        for (var filler : vein.definition().veinGenerator().getAllEntries()) {
            filler.vein().ifLeft(state -> tooltip.add(Component.literal(ConfigHolder.INSTANCE.compat.minimap.oreNamePrefix)
                    .append(state.getBlock().getName()))).ifRight(material -> tooltip.add(Component.literal(ConfigHolder.INSTANCE.compat.minimap.oreNamePrefix)
                            .append(TagPrefix.ore.getLocalizedName(material))));
        }
        return tooltip;
    }
}
