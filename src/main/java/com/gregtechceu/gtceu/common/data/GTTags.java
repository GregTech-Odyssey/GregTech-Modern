package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.OreProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.api.fluids.GTFluid;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.unification.material.MaterialRegistryManager;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import com.gto.fastcollection.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@UtilityClass
@SuppressWarnings("deprecation")
public class GTTags {

    private final Reference2ReferenceFunction<ResourceKey<?>, O2OOpenCacheHashMap<ResourceLocation, List<TagLoader.EntryWithSource>>> REGISTRY_MAP = k -> new O2OOpenCacheHashMap<>();
    private final Object2ObjectFunction<ResourceLocation, List<TagLoader.EntryWithSource>> ENTRY_MAP = k -> new ArrayList<>();

    private final Reference2ReferenceOpenHashMap<ResourceKey<?>, O2OOpenCacheHashMap<ResourceLocation, List<TagLoader.EntryWithSource>>> DYNAMIC_TAG_CACHE = new Reference2ReferenceOpenHashMap<>();

    public <T> void generateGTDynamicTags(Map<ResourceLocation, List<TagLoader.EntryWithSource>> tagMap, ResourceKey<T> registry) {
        if (tagMap == GTUtil.EMPTY_MAP) return;
        var tags = DYNAMIC_TAG_CACHE.get(registry);
        if (tags == null) return;
        tags.object2ObjectEntrySet().fastForEach(entry -> tagMap.computeIfAbsent(entry.getKey(), path -> new ArrayList<>()).addAll(entry.getValue()));
        DYNAMIC_TAG_CACHE.remove(registry);
    }

    public void registryGTDynamicTags() {
        var itemTags = DYNAMIC_TAG_CACHE.computeIfAbsent(Registries.ITEM, path -> new O2OOpenCacheHashMap<>());
        MaterialRegistryManager.getInstance().getAll().forEach(material -> {
            if (material.isNull()) return;
            material.MATERIAL_ENTRY_ITEM_MAP.forEach((tagPrefix, itemLikes) -> {
                if (itemLikes.isEmpty()) return;
                var entries = itemLikes.stream().map(GTTags::makeItemEntry).collect(toArrayList());

                var materialTags = tagPrefix.getAllItemTags(material);
                for (TagKey<Item> materialTag : materialTags) {
                    itemTags.computeIfAbsent(materialTag.location(), path -> new ArrayList<>()).addAll(entries);
                }

                if (tagPrefix == TagPrefix.crushed && material.hasProperty(PropertyKey.ORE)) {
                    OreProperty ore = material.getProperty(PropertyKey.ORE);
                    Material washedIn = ore.getWashedIn().first();
                    if (washedIn.isNull()) return;
                    ResourceLocation generalTag = CustomTags.CHEM_BATH_WASHABLE.location();
                    ResourceLocation specificTag = generalTag.withSuffix("/" + washedIn.getName());

                    itemTags.computeIfAbsent(generalTag, path -> new ArrayList<>()).addAll(entries);
                    itemTags.computeIfAbsent(specificTag, path -> new ArrayList<>()).addAll(entries);
                }
            });
        });

        GTMaterialItems.TOOL_ITEMS.rowMap().forEach((material, map) -> map.values().forEach(item -> {
            if (item == null) return;
            var entry = makeItemEntry(item);
            for (TagKey<Item> tag : item.get().getToolType().itemTags) {
                itemTags.computeIfAbsent(tag.location(), path -> new ArrayList<>()).add(entry);
            }
        }));

        // If AE2 is loaded, add the Fluid P2P attunement tag to all the buckets
        var p2pFluidAttunements = GTUtil.getResourceLocation(GTValues.MODID_APPENG, "p2p_attunements/fluid_p2p_tunnel");
        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            FluidProperty property = material.getProperty(PropertyKey.FLUID);
            if (property == null) {
                continue;
            }
            for (FluidStorageKey key : FluidStorageKey.allKeys()) {
                Fluid fluid = property.get(key);
                if (fluid == null || fluid.getBucket() == Items.AIR) {
                    continue;
                }
                var entry = makeItemEntry(fluid.getBucket());
                itemTags.computeIfAbsent(p2pFluidAttunements, path -> new ArrayList<>()).add(entry);
            }
        }
        var blockTags = DYNAMIC_TAG_CACHE.computeIfAbsent(Registries.BLOCK, path -> new O2OOpenCacheHashMap<>());
        MaterialRegistryManager.getInstance().getAll().forEach(material -> {
            if (material.isNull()) return;
            material.MATERIAL_ENTRY_BLOCK_MAP.forEach((tagPrefix, blocks) -> {
                if (blocks.isEmpty()) return;
                var entries = blocks.stream().map(GTTags::makeBlockEntry).collect(toArrayList());
                var materialTags = tagPrefix.getAllBlockTags(material);
                for (TagKey<Block> materialTag : materialTags) {
                    blockTags.computeIfAbsent(materialTag.location(), path -> new ArrayList<>()).addAll(entries);
                }
                // Add tool tags
                if (!tagPrefix.isIgnored(material) && !tagPrefix.miningToolTag().isEmpty()) {
                    blockTags.computeIfAbsent(CustomTags.TOOL_TIERS[material.getBlockHarvestLevel()].location(),
                            path -> new ArrayList<>()).addAll(entries);
                    if (material.hasProperty(PropertyKey.WOOD)) {
                        blockTags.computeIfAbsent(BlockTags.MINEABLE_WITH_AXE.location(), path -> new ArrayList<>())
                                .addAll(entries);
                    } else {
                        for (var tag : tagPrefix.miningToolTag()) {
                            blockTags.computeIfAbsent(tag.location(), path -> new ArrayList<>()).addAll(entries);
                        }
                    }
                }
            });
        });

        GTRegistries.MACHINES.forEach(machine -> blockTags.computeIfAbsent(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH.location(),
                path -> new ArrayList<>()).add(makeBlockEntry(machine.get())));

        // if config is NOT enabled, add the "configurable" mineability tags to the pickaxe tag
        if (!ConfigHolder.INSTANCE.machines.requireGTToolsForBlocks) {
            var tagList = blockTags.computeIfAbsent(BlockTags.MINEABLE_WITH_PICKAXE.location(),
                    path -> new ArrayList<>());

            tagList.add(makeTagEntry(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH));
            tagList.add(makeTagEntry(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WIRE_CUTTER));
        }
        var fluidTags = DYNAMIC_TAG_CACHE.computeIfAbsent(Registries.FLUID, path -> new O2OOpenCacheHashMap<>());
        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            FluidProperty property = material.getProperty(PropertyKey.FLUID);
            if (property == null) continue;
            for (FluidStorageKey key : FluidStorageKey.allKeys()) {
                Fluid fluid = property.get(key);
                if (fluid == null) continue;
                ItemMaterialData.FLUID_MATERIAL.put(fluid, material);
                TagLoader.EntryWithSource entry = makeFluidEntry(fluid);
                FluidState state;
                if (fluid instanceof GTFluid gtFluid) {
                    state = gtFluid.getState();
                } else {
                    state = key.getDefaultFluidState();
                }
                fluidTags.computeIfAbsent(state.getTagKey().location(), path -> new ArrayList<>()).add(entry);
                if (key.getExtraTag() != null) {
                    fluidTags.computeIfAbsent(key.getExtraTag().location(), path -> new ArrayList<>()).add(entry);
                }
            }
        }
    }

    public void addItemEntry(ItemLike item, ResourceLocation tagLocation) {
        DYNAMIC_TAG_CACHE.computeIfAbsent(Registries.ITEM, REGISTRY_MAP).computeIfAbsent(tagLocation, ENTRY_MAP).add(GTTags.makeItemEntry(item));
    }

    public void addBlockEntry(Block block, ResourceLocation tagLocation) {
        DYNAMIC_TAG_CACHE.computeIfAbsent(Registries.BLOCK, REGISTRY_MAP).computeIfAbsent(tagLocation, ENTRY_MAP).add(GTTags.makeBlockEntry(block));
    }

    public void addFluidEntry(Fluid fluid, ResourceLocation tagLocation) {
        DYNAMIC_TAG_CACHE.computeIfAbsent(Registries.FLUID, REGISTRY_MAP).computeIfAbsent(tagLocation, ENTRY_MAP).add(GTTags.makeFluidEntry(fluid));
    }

    public void addEntry(ResourceLocation id, TagKey<? extends Registry<?>> tag) {
        DYNAMIC_TAG_CACHE.computeIfAbsent(tag.registry(), REGISTRY_MAP).computeIfAbsent(tag.location(), ENTRY_MAP).add(GTTags.makeElementEntry(id));
    }

    public void addEntry(ResourceLocation id, ResourceKey<?> registry, ResourceLocation tagLocation) {
        DYNAMIC_TAG_CACHE.computeIfAbsent(registry, REGISTRY_MAP).computeIfAbsent(tagLocation, ENTRY_MAP).add(GTTags.makeElementEntry(id));
    }

    private <T> Collector<T, ?, ArrayList<T>> toArrayList() {
        return Collectors.toCollection(ArrayList::new);
    }

    public TagLoader.EntryWithSource makeItemEntry(Supplier<? extends Item> item) {
        return makeItemEntry(item.get());
    }

    public TagLoader.EntryWithSource makeItemEntry(ItemLike item) {
        return makeElementEntry(item.asItem().builtInRegistryHolder().key().location());
    }

    public TagLoader.EntryWithSource makeBlockEntry(Supplier<? extends Block> block) {
        return makeBlockEntry(block.get());
    }

    public TagLoader.EntryWithSource makeBlockEntry(Block block) {
        return makeElementEntry(block.builtInRegistryHolder().key().location());
    }

    public TagLoader.EntryWithSource makeFluidEntry(Fluid fluid) {
        return makeElementEntry(fluid.builtInRegistryHolder().key().location());
    }

    public TagLoader.EntryWithSource makeElementEntry(ResourceLocation id) {
        return new TagLoader.EntryWithSource(TagEntry.element(id), GTValues.CUSTOM_TAG_SOURCE);
    }

    public TagLoader.EntryWithSource makeTagEntry(TagKey<?> tag) {
        return new TagLoader.EntryWithSource(TagEntry.tag(tag.location()), GTValues.CUSTOM_TAG_SOURCE);
    }
}
