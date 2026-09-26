package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreVeinSavedData;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record VeinInfo(Component name, int weight, List<Entry> entries, FluidStack fluid, ResourceLocation rock, List<Spec> specs,
                       @Nullable List<DimensionMarker> dimensions) {

    private static final String WEIGHT = "gtceu.jei.ore_vein_diagram.weight";
    private static final String HEIGHT = "gtceu.jei.vein.height";
    private static final String DENSITY = "gtceu.jei.vein.density";
    private static final String SIZE = "gtceu.jei.vein.size";
    private static final String YIELD = "gtceu.jei.vein.yield";
    private static final String DEPLETED_YIELD = "gtceu.jei.vein.depleted_yield";
    private static final String RESERVE = "gtceu.jei.vein.reserve";
    private static final String OPERATIONS = "gtceu.jei.vein.operations";
    private static final String OPERATIONS_ABOUT = "gtceu.jei.vein.operations_about";
    private static final String INFINITE = "gtceu.jei.vein.infinite";
    private static final String CHUNKS = "gtceu.jei.vein.chunks";
    private static final String RANGE = "%s ~ %s";
    private static final int BLOCK_COLOR = 0x8B8B8B;
    private static final ResourceLocation STONE = new ResourceLocation("block/stone");
    private static final ResourceLocation BEDROCK = new ResourceLocation("block/bedrock");

    public record Entry(ItemStack stack, int weight, int color) {}

    public record Spec(String labelKey, Component value) {}

    public Component weightText() {
        return Component.translatable(WEIGHT, FormattingUtil.formatNumbers(weight));
    }

    public int totalWeight() {
        int total = 0;
        for (var entry : entries) total += entry.weight();
        return total;
    }

    public static VeinInfo of(GTOreDefinition definition) {
        var entries = new ArrayList<Entry>();
        var weights = new Object2IntLinkedOpenHashMap<Object>();
        for (var entry : definition.veinGenerator().getAllEntries()) {
            weights.addTo(entry.vein().<Object>map(BlockState::getBlock, material -> material), entry.chance());
        }
        for (var entry : weights.object2IntEntrySet()) {
            if (entry.getKey() instanceof Material material) {
                entries.add(new Entry(ChemicalHelper.get(TagPrefix.rawOre, material), entry.getIntValue(), material.getMaterialRGB()));
            } else if (entry.getKey() instanceof Block block) {
                entries.add(new Entry(block.asItem().getDefaultInstance(), entry.getIntValue(), blockColor(block.defaultBlockState())));
            }
        }
        var specs = new ArrayList<Spec>();
        specs.add(new Spec(HEIGHT, height(definition.range())));
        specs.add(new Spec(DENSITY, Component.literal(FormattingUtil.formatNumbers(Math.round(definition.density() * 100)) + "%")));
        var size = definition.clusterSize();
        specs.add(new Spec(SIZE, range(size.getMinValue(), size.getMaxValue())));
        var name = Component.translatable("gtceu.jei.ore_vein." + GTOreVeinWidget.getOreName(definition));
        return new VeinInfo(name, definition.weight(), entries, FluidStack.EMPTY, STONE, specs, dimensions(definition.dimensionFilter()));
    }

    public static VeinInfo of(BedrockFluidDefinition definition) {
        var specs = new ArrayList<Spec>();
        int min = definition.getMinimumYield();
        int max = Math.max(min, definition.getMaximumYield() - 1);
        specs.add(new Spec(YIELD, Component.literal(rangeText(min, max) + " mB")));
        specs.add(new Spec(DEPLETED_YIELD, Component.literal(FormattingUtil.formatNumbers(definition.getDepletedYield()) + " mB")));
        specs.add(new Spec(RESERVE, reserve(BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS, definition.getDepletionAmount(), definition.getDepletionChance())));
        var name = Component.translatable("gtceu.jei.bedrock_fluid." + GTOreVeinWidget.getFluidName(definition));
        var fluid = new FluidStack(definition.getStoredFluid().get(), 1000);
        return new VeinInfo(name, definition.getWeight(), List.of(), fluid, BEDROCK, specs, dimensions(definition.getDimensionFilter()));
    }

    public static VeinInfo of(BedrockOreDefinition definition) {
        var weights = new Object2IntLinkedOpenHashMap<Material>();
        for (var material : definition.materials()) weights.addTo(material.material(), material.weight());
        var entries = new ArrayList<Entry>();
        for (var entry : weights.object2IntEntrySet()) {
            entries.add(new Entry(ChemicalHelper.get(TagPrefix.rawOre, entry.getKey()), entry.getIntValue(), entry.getKey().getMaterialRGB()));
        }
        var specs = new ArrayList<Spec>();
        var yield = definition.yield();
        specs.add(new Spec(YIELD, range(yield.getMinValue(), yield.getMaxValue())));
        specs.add(new Spec(DEPLETED_YIELD, Component.literal(FormattingUtil.formatNumbers(definition.depletedYield()))));
        specs.add(new Spec(RESERVE, reserve(BedrockOreVeinSavedData.MAXIMUM_VEIN_OPERATIONS, definition.depletionAmount(), definition.depletionChance())));
        specs.add(new Spec(SIZE, Component.translatable(CHUNKS, FormattingUtil.formatNumbers(definition.size()))));
        var name = Component.translatable("gtceu.jei.bedrock_ore." + GTOreVeinWidget.getBedrockOreName(definition));
        return new VeinInfo(name, definition.weight(), entries, FluidStack.EMPTY, BEDROCK, specs, dimensions(definition.dimensionFilter()));
    }

    private static int blockColor(BlockState state) {
        try {
            return state.getBlock().defaultMapColor().col;
        } catch (Throwable ignored) {
            return BLOCK_COLOR;
        }
    }

    private static Component range(int min, int max) {
        return Component.literal(rangeText(min, max));
    }

    private static String rangeText(int min, int max) {
        if (min == max) return FormattingUtil.formatNumbers(min);
        return RANGE.formatted(FormattingUtil.formatNumbers(min), FormattingUtil.formatNumbers(max));
    }

    private static Component reserve(int maxOperations, int amount, int chance) {
        if (ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids || amount <= 0 || chance <= 0) {
            return Component.translatable(INFINITE);
        }
        long operations = (maxOperations + amount - 1L) / amount;
        if (chance >= 100) return Component.translatable(OPERATIONS, FormattingUtil.formatNumbers(operations));
        return Component.translatable(OPERATIONS_ABOUT, FormattingUtil.formatNumbers(operations * 100 / chance));
    }

    private static Component height(HeightRangePlacement placement) {
        HeightProvider height = placement.height;
        if (height instanceof UniformHeight uniform) {
            var min = absolute(uniform.minInclusive);
            var max = absolute(uniform.maxInclusive);
            if (min != null && max != null) return range(min, max);
        }
        var json = HeightProvider.CODEC.encodeStart(JsonOps.INSTANCE, height).result().orElse(null);
        if (json instanceof JsonObject object) {
            var min = absolute(object.get("min_inclusive"));
            var max = absolute(object.get("max_inclusive"));
            if (min != null && max != null) return range(min, max);
        }
        return Component.literal("-");
    }

    @Nullable
    private static Integer absolute(VerticalAnchor anchor) {
        return absolute(VerticalAnchor.CODEC.encodeStart(JsonOps.INSTANCE, anchor).result().orElse(null));
    }

    @Nullable
    private static Integer absolute(@Nullable JsonElement json) {
        if (json instanceof JsonObject object && object.has("absolute")) return object.get("absolute").getAsInt();
        return null;
    }

    @Nullable
    private static List<DimensionMarker> dimensions(@Nullable Set<ResourceKey<Level>> filter) {
        if (filter == null) return null;
        return filter.stream().map(ResourceKey::location)
                .map(loc -> GTRegistries.DIMENSION_MARKERS.getOrDefault(loc, new DimensionMarker(DimensionMarker.MAX_TIER, () -> Blocks.BARRIER, loc.toString())))
                .sorted(Comparator.comparingInt(DimensionMarker::getTier))
                .toList();
    }
}
