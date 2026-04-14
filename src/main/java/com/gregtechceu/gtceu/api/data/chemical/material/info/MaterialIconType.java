package com.gregtechceu.gtceu.api.data.chemical.material.info;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib.utils.ResourceHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import com.google.common.base.CaseFormat;
import it.unimi.dsi.fastutil.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MaterialIconType {

    private static List<MaterialIconType> VALUES = new ArrayList<>();

    public static final MaterialIconType dustTiny = new MaterialIconType("dustTiny");
    public static final MaterialIconType dustSmall = new MaterialIconType("dustSmall");
    public static final MaterialIconType dust = new MaterialIconType("dust");
    public static final MaterialIconType dustImpure = new MaterialIconType("dustImpure");
    public static final MaterialIconType dustPure = new MaterialIconType("dustPure");

    public static final MaterialIconType rawOre = new MaterialIconType("rawOre");
    public static final MaterialIconType rawOreBlock = new MaterialIconType("rawOreBlock");
    public static final MaterialIconType crushed = new MaterialIconType("crushed");
    public static final MaterialIconType crushedPurified = new MaterialIconType("crushedPurified");
    public static final MaterialIconType crushedRefined = new MaterialIconType("crushedRefined");

    public static final MaterialIconType gem = new MaterialIconType("gem");
    public static final MaterialIconType gemChipped = new MaterialIconType("gemChipped");
    public static final MaterialIconType gemFlawed = new MaterialIconType("gemFlawed");
    public static final MaterialIconType gemFlawless = new MaterialIconType("gemFlawless");
    public static final MaterialIconType gemExquisite = new MaterialIconType("gemExquisite");

    public static final MaterialIconType nugget = new MaterialIconType("nugget");

    public static final MaterialIconType ingot = new MaterialIconType("ingot");
    public static final MaterialIconType ingotHot = new MaterialIconType("ingotHot");
    public static final MaterialIconType ingotDouble = new MaterialIconType("ingotDouble");
    public static final MaterialIconType ingotTriple = new MaterialIconType("ingotTriple");
    public static final MaterialIconType ingotQuadruple = new MaterialIconType("ingotQuadruple");
    public static final MaterialIconType ingotQuintuple = new MaterialIconType("ingotQuintuple");

    public static final MaterialIconType plate = new MaterialIconType("plate");
    public static final MaterialIconType plateDouble = new MaterialIconType("plateDouble");
    public static final MaterialIconType plateTriple = new MaterialIconType("plateTriple");
    public static final MaterialIconType plateQuadruple = new MaterialIconType("plateQuadruple");
    public static final MaterialIconType plateQuintuple = new MaterialIconType("plateQuintuple");
    public static final MaterialIconType plateDense = new MaterialIconType("plateDense");

    public static final MaterialIconType rod = new MaterialIconType("rod");
    public static final MaterialIconType lens = new MaterialIconType("lens");
    public static final MaterialIconType round = new MaterialIconType("round");
    public static final MaterialIconType bolt = new MaterialIconType("bolt");
    public static final MaterialIconType screw = new MaterialIconType("screw");
    public static final MaterialIconType ring = new MaterialIconType("ring");
    public static final MaterialIconType wireFine = new MaterialIconType("wireFine");
    public static final MaterialIconType gearSmall = new MaterialIconType("gearSmall");
    public static final MaterialIconType rotor = new MaterialIconType("rotor");
    public static final MaterialIconType rodLong = new MaterialIconType("rodLong");
    public static final MaterialIconType springSmall = new MaterialIconType("springSmall");
    public static final MaterialIconType spring = new MaterialIconType("spring");
    public static final MaterialIconType gear = new MaterialIconType("gear");
    public static final MaterialIconType foil = new MaterialIconType("foil");

    public static final MaterialIconType toolHeadSword = new MaterialIconType("toolHeadSword");
    public static final MaterialIconType toolHeadPickaxe = new MaterialIconType("toolHeadPickaxe");
    public static final MaterialIconType toolHeadShovel = new MaterialIconType("toolHeadShovel");
    public static final MaterialIconType toolHeadAxe = new MaterialIconType("toolHeadAxe");
    public static final MaterialIconType toolHeadHoe = new MaterialIconType("toolHeadHoe");
    public static final MaterialIconType toolHeadHammer = new MaterialIconType("toolHeadHammer");
    public static final MaterialIconType toolHeadFile = new MaterialIconType("toolHeadFile");
    public static final MaterialIconType toolHeadSaw = new MaterialIconType("toolHeadSaw");
    public static final MaterialIconType toolHeadBuzzSaw = new MaterialIconType("toolHeadBuzzSaw");
    public static final MaterialIconType toolHeadDrill = new MaterialIconType("toolHeadDrill");
    public static final MaterialIconType toolHeadChainsaw = new MaterialIconType("toolHeadChainsaw");
    public static final MaterialIconType toolHeadScythe = new MaterialIconType("toolHeadScythe");
    public static final MaterialIconType toolHeadScrewdriver = new MaterialIconType("toolHeadScrewdriver");
    public static final MaterialIconType toolHeadWrench = new MaterialIconType("toolHeadWrench");
    public static final MaterialIconType toolHeadWireCutter = new MaterialIconType("toolHeadWireCutter");

    public static final MaterialIconType turbineBlade = new MaterialIconType("turbineBlade");
    public static final MaterialIconType turbineRotor = new MaterialIconType("turbineRotor");

    // BLOCK TEXTURES
    public static final MaterialIconType liquid = new MaterialIconType("liquid");
    public static final MaterialIconType gas = new MaterialIconType("gas");
    public static final MaterialIconType plasma = new MaterialIconType("plasma");
    public static final MaterialIconType molten = new MaterialIconType("molten");
    public static final MaterialIconType block = new MaterialIconType("block");
    public static final MaterialIconType ore = new MaterialIconType("ore");
    public static final MaterialIconType oreSmall = new MaterialIconType("oreSmall");
    public static final MaterialIconType frameGt = new MaterialIconType("frameGt");
    public static final MaterialIconType wire = new MaterialIconType("wire");

    // USED FOR GREGIFICATION ADDON
    public static final MaterialIconType seed = new MaterialIconType("seed");
    public static final MaterialIconType crop = new MaterialIconType("crop");
    public static final MaterialIconType essence = new MaterialIconType("essence");

    public Map<MaterialIconSet, ResourceLocation> item_model_cache = new ConcurrentHashMap<>();

    public final Map<MaterialIconSet, ResourceLocation> item_texture_cache = new ConcurrentHashMap<>();
    private final Map<MaterialIconSet, ResourceLocation> item_texture_cache_secondary = new ConcurrentHashMap<>();

    private Map<MaterialIconSet, ResourceLocation> block_model_cache = new ConcurrentHashMap<>();

    private final Map<MaterialIconSet, ResourceLocation> block_texture_cache = new ConcurrentHashMap<>();
    private final Map<MaterialIconSet, ResourceLocation> block_texture_cache_secondary = new ConcurrentHashMap<>();

    private final String name;

    public MaterialIconType(String name) {
        this.name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
        synchronized (VALUES) {
            VALUES.add(this);
        }
    }

    public static void init() {}

    public static void clear() {
        VALUES.forEach(t -> {
            t.block_model_cache = null;
            t.item_model_cache = null;
        });
        VALUES = null;
    }

    private ResourceLocation getCache(Map<MaterialIconSet, ResourceLocation> map, MaterialIconSet materialIconSet, Function<MaterialIconSet, ResourceLocation> function) {
        return map.computeIfAbsent(materialIconSet, function);
    }

    @Nullable
    public ResourceLocation getBlockTexturePath(@NotNull MaterialIconSet materialIconSet, boolean doReadCache) {
        return getBlockTexturePath(materialIconSet, null, doReadCache);
    }

    @Nullable // Safe: only null on registration on fabric, and no "required" textures are resolved at that point.
    public ResourceLocation getBlockTexturePath(@NotNull MaterialIconSet materialIconSet, String suffix,
                                                boolean doReadCache) {
        boolean isBlank = suffix == null || suffix.isBlank();
        return getCache(isBlank ? block_texture_cache : block_texture_cache_secondary, materialIconSet, k -> {
            var fs = isBlank ? "" : "_" + suffix;

            MaterialIconSet iconSet = materialIconSet;
            // noinspection ConstantConditions
            if (!GTCEu.isClientSide() || Minecraft.getInstance() == null ||
                    Minecraft.getInstance().getResourceManager() == null)
                return null; // check minecraft for null for CI environments
            if (!iconSet.isRootIconset) {
                while (!iconSet.isRootIconset) {
                    ResourceLocation location = GTCEu
                            .id(String.format("textures/block/material_sets/%s/%s%s.png", iconSet.name, this.name, fs));
                    if (ResourceHelper.isResourceExist(location))
                        break;
                    iconSet = iconSet.parentIconset;
                }
            }

            ResourceLocation location = GTCEu
                    .id(String.format("textures/block/material_sets/%s/%s%s.png", iconSet.name, this.name, fs));
            if (!fs.isEmpty() && !ResourceHelper.isResourceExist(location)) {
                return null;
            }
            return GTCEu.id(String.format("block/material_sets/%s/%s%s", iconSet.name, this.name, fs));
        });
    }

    @NotNull
    public ResourceLocation getBlockModelPath(@NotNull MaterialIconSet materialIconSet) {
        return getCache(block_model_cache, materialIconSet, k -> {
            MaterialIconSet iconSet = materialIconSet;
            // noinspection ConstantConditions
            if (!iconSet.isRootIconset && GTCEu.isClientSide() && Minecraft.getInstance() != null &&
                    Minecraft.getInstance().getResourceManager() != null) { // check minecraft for null for CI
                                                                            // environments
                while (!iconSet.isRootIconset) {
                    ResourceLocation location = GTCEu
                            .id(String.format("models/block/material_sets/%s/%s.json", iconSet.name, this.name));
                    if (ResourceHelper.isResourceExist(location))
                        break;
                    iconSet = iconSet.parentIconset;
                }
            }
            return GTCEu.id(String.format("block/material_sets/%s/%s", iconSet.name, this.name));
        });
    }

    @NotNull
    public ResourceLocation getItemModelPath(@NotNull MaterialIconSet materialIconSet) {
        return getCache(item_model_cache, materialIconSet, k -> {
            MaterialIconSet iconSet = materialIconSet;
            // noinspection ConstantConditions
            if (!iconSet.isRootIconset && GTCEu.isClientSide() && Minecraft.getInstance() != null &&
                    Minecraft.getInstance().getResourceManager() != null) { // check minecraft for null for CI
                                                                            // environments
                while (!iconSet.isRootIconset) {
                    ResourceLocation location = GTCEu.id(String.format("models/item/material_sets/%s/%s.json", iconSet.name, this.name));
                    if (ResourceHelper.isResourceExist(location))
                        break;
                    iconSet = iconSet.parentIconset;
                }
            }

            return GTCEu.id(String.format("item/material_sets/%s/%s", iconSet.name, this.name));
        });
    }

    @Nullable
    public ResourceLocation getItemTexturePath(@NotNull MaterialIconSet materialIconSet, boolean doReadCache) {
        return getItemTexturePath(materialIconSet, null, doReadCache);
    }

    @Nullable
    public ResourceLocation getItemTexturePath(@NotNull MaterialIconSet materialIconSet, String suffix,
                                               boolean doReadCache) {
        boolean isBlank = suffix == null || suffix.isBlank();
        return getCache(isBlank ? item_texture_cache : item_texture_cache_secondary, materialIconSet, k -> {
            var fs = isBlank ? "" : "_" + suffix;

            MaterialIconSet iconSet = materialIconSet;
            // noinspection ConstantConditions
            if (!iconSet.isRootIconset && GTCEu.isClientSide() && Minecraft.getInstance() != null &&
                    Minecraft.getInstance().getResourceManager() != null) { // check minecraft for null for CI
                                                                            // environments
                while (!iconSet.isRootIconset) {
                    ResourceLocation location = GTCEu
                            .id(String.format("textures/item/material_sets/%s/%s%s.png", iconSet.name, this.name, fs));
                    if (ResourceHelper.isResourceExist(location))
                        break;
                    iconSet = iconSet.parentIconset;
                }
            }

            ResourceLocation location = GTCEu
                    .id(String.format("textures/item/material_sets/%s/%s%s.png", iconSet.name, this.name, fs));
            if (!fs.isEmpty() && !ResourceHelper.isResourceExist(location)) {
                return null;
            }
            return GTCEu.id(String.format("item/material_sets/%s/%s%s", iconSet.name, this.name, fs));
        });
    }

    @Override
    public String toString() {
        return this.name;
    }
}
