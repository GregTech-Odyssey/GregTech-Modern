package com.gregtechceu.gtceu.api.data.chemical.material.info;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib.utils.ResourceHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import com.google.common.base.CaseFormat;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class MaterialIconType {

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

    public static Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> ITEM_MODEL_CACHE = new Reference2ReferenceOpenHashMap<>();

    public static final Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> ITEM_TEXTURE_CACHE = new Reference2ReferenceOpenHashMap<>();
    private static final Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> ITEM_TEXTURE_CACHE_SECONDARY = new Reference2ReferenceOpenHashMap<>();

    private static Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> BLOCK_MODEL_CACHE = new Reference2ReferenceOpenHashMap<>();

    private static final Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> BLOCK_TEXTURE_CACHE = new Reference2ReferenceOpenHashMap<>();
    private static final Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> BLOCK_TEXTURE_CACHE_SECONDARY = new Reference2ReferenceOpenHashMap<>();

    private final String name;

    public MaterialIconType(String name) {
        this.name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
    }

    public static void init() {}

    public static void clear() {
        ITEM_MODEL_CACHE = null;
        BLOCK_MODEL_CACHE = null;
    }

    private static final Function<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> FUNCTION = k -> new Reference2ReferenceOpenHashMap<>();

    private ResourceLocation getCache(Map<MaterialIconType, Map<MaterialIconSet, ResourceLocation>> map, MaterialIconSet materialIconSet, Function<MaterialIconSet, ResourceLocation> function) {
        return map.computeIfAbsent(this, FUNCTION).computeIfAbsent(materialIconSet, function);
    }

    @Nullable
    public ResourceLocation getBlockTexturePath(@NotNull MaterialIconSet materialIconSet, boolean doReadCache) {
        return getBlockTexturePath(materialIconSet, null, doReadCache);
    }

    @Nullable // Safe: only null on registration on fabric, and no "required" textures are resolved at that point.
    public ResourceLocation getBlockTexturePath(@NotNull MaterialIconSet materialIconSet, String suffix,
                                                boolean doReadCache) {
        boolean isBlank = suffix == null || suffix.isBlank();
        return getCache(isBlank ? BLOCK_TEXTURE_CACHE : BLOCK_TEXTURE_CACHE_SECONDARY, materialIconSet, k -> {
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
        return getCache(BLOCK_MODEL_CACHE, materialIconSet, k -> {
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
        return getCache(ITEM_MODEL_CACHE, materialIconSet, k -> {
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
        return getCache(isBlank ? ITEM_TEXTURE_CACHE : ITEM_TEXTURE_CACHE_SECONDARY, materialIconSet, k -> {
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
