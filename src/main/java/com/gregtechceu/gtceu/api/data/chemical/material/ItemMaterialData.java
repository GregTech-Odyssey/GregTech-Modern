package com.gregtechceu.gtceu.api.data.chemical.material;

import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.data.recipe.misc.RecyclingRecipes;
import com.gregtechceu.gtceu.data.recipe.misc.StoneMachineRecipes;
import com.gregtechceu.gtceu.data.recipe.misc.WoodMachineRecipes;
import com.gregtechceu.gtceu.data.tags.TagsHandler;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.gregtechceu.gtceu.utils.collection.O2OOpenCustomCacheHashMap;
import com.gregtechceu.gtceu.utils.memoization.MemoizedBlockSupplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.RegistryObject;

import com.mojang.datafixers.util.Pair;
import com.tterrag.registrate.util.entry.RegistryEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class ItemMaterialData {

    /** Used for custom material data for items that do not fall into the normal "prefix, material" pair */
    public static final Reference2ObjectOpenHashMap<Item, ItemMaterialInfo> ITEM_MATERIAL_INFO = new Reference2ObjectOpenHashMap<>();
    /** Mapping of an item to a "prefix, material" pair */
    public static final ObjectArrayList<Pair<Supplier<? extends Item>, MaterialEntry>> ITEM_MATERIAL_ENTRY = new ObjectArrayList<>();
    public static final Reference2ObjectOpenHashMap<Item, MaterialEntry> ITEM_MATERIAL_ENTRY_COLLECTED = new Reference2ObjectOpenHashMap<>();
    /** Mapping of a fluid to a material */
    public static final Reference2ReferenceOpenHashMap<Fluid, Material> FLUID_MATERIAL = new Reference2ReferenceOpenHashMap<>();
    /** Mapping of all items that represent a "prefix, material" pair */
    public static final HashMap<MaterialEntry, List<Supplier<? extends Item>>> MATERIAL_ENTRY_ITEM_MAP = new HashMap<>();
    public static final HashMap<MaterialEntry, List<Item>> MATERIAL_ENTRY_ITEM_LIKE_MAP = new HashMap<>();
    public static final HashMap<MaterialEntry, List<Supplier<? extends Block>>> MATERIAL_ENTRY_BLOCK_MAP = new HashMap<>();
    public static final HashMap<MaterialEntry, List<Block>> MATERIAL_ENTRY_BLOCK_LIKE_MAP = new HashMap<>();
    /** Mapping of stone type blockState to "prefix, material" */
    public static final Reference2ReferenceOpenHashMap<Supplier<BlockState>, TagPrefix> ORES_INVERSE = new Reference2ReferenceOpenHashMap<>();

    public static final O2OOpenCustomCacheHashMap<ItemStack, List<ItemStack>> UNRESOLVED_ITEM_MATERIAL_INFO = new O2OOpenCustomCacheHashMap<>(
            ItemStackHashStrategy.ITEM_AND_TAG);

    public static void registerMaterialInfo(ItemLike item, ItemMaterialInfo materialInfo) {
        ITEM_MATERIAL_INFO.put(item.asItem(), materialInfo);
    }

    public static ItemMaterialInfo getMaterialInfo(ItemLike item) {
        return ITEM_MATERIAL_INFO.get(item.asItem());
    }

    public static void clearMaterialInfo(ItemLike item) {
        ITEM_MATERIAL_INFO.remove(item.asItem());
    }

    /**
     * Register Material Entry for an item
     *
     * @param supplier      a supplier to the item
     * @param materialEntry the entry to register
     */
    public static void registerMaterialEntry(@NotNull Supplier<? extends ItemLike> supplier,
                                             @NotNull MaterialEntry materialEntry) {
        registerItemEntry(supplier, materialEntry);
        ITEM_MATERIAL_ENTRY.add(Pair.of(() -> supplier.get().asItem(), materialEntry));
        var blockSupplier = convertToBlock(supplier);
        if (blockSupplier != null) {
            registerBlockEntry(blockSupplier, materialEntry);
        }
    }

    /**
     * @see #registerMaterialEntry(Supplier, MaterialEntry)
     */
    public static void registerMaterialEntries(@NotNull Collection<Supplier<? extends ItemLike>> items,
                                               @NotNull TagPrefix tagPrefix, @NotNull Material material) {
        if (!items.isEmpty()) {
            MaterialEntry entry = new MaterialEntry(tagPrefix, material);
            for (var supplier : items) {
                registerMaterialEntry(supplier, entry);
            }
        }
    }

    /**
     * @see #registerMaterialEntry(Supplier, MaterialEntry)
     */
    public static void registerMaterialEntry(@NotNull Supplier<? extends ItemLike> item,
                                             @NotNull TagPrefix tagPrefix, @NotNull Material material) {
        registerMaterialEntry(item, new MaterialEntry(tagPrefix, material));
    }

    /**
     * @see #registerMaterialEntry(Supplier, MaterialEntry)
     */
    public static void registerMaterialEntry(@NotNull ItemLike item,
                                             @NotNull TagPrefix tagPrefix, @NotNull Material material) {
        registerMaterialEntry(() -> item, new MaterialEntry(tagPrefix, material));
    }

    private static void registerItemEntry(@NotNull Supplier<? extends ItemLike> supplier,
                                          @NotNull MaterialEntry materialEntry) {
        MATERIAL_ENTRY_ITEM_MAP.computeIfAbsent(materialEntry, k -> new ObjectArrayList<>())
                .add(() -> supplier.get().asItem());
        if (TagPrefix.ORES.containsKey(materialEntry.tagPrefix()) &&
                !ORES_INVERSE.containsValue(materialEntry.tagPrefix())) {
            ORES_INVERSE.put(TagPrefix.ORES.get(materialEntry.tagPrefix()).stoneType(), materialEntry.tagPrefix());
        }
    }

    private static void registerBlockEntry(@NotNull Supplier<? extends Block> supplier,
                                           @NotNull MaterialEntry materialEntry) {
        MATERIAL_ENTRY_BLOCK_MAP.computeIfAbsent(materialEntry, k -> new ObjectArrayList<>())
                .add(supplier);
    }

    @SuppressWarnings("unchecked")
    public static @Nullable Supplier<? extends Block> convertToBlock(@NotNull Supplier<? extends ItemLike> supplier) {
        if (supplier instanceof RegistryObject<? extends ItemLike> registryObject) {
            var key = registryObject.getKey();
            if (key != null && key.isFor(Registries.BLOCK)) {
                return (Supplier<? extends Block>) registryObject;
            }
        } else if (supplier instanceof RegistryEntry<? extends ItemLike> entry) {
            var key = entry.getKey();
            if (key.isFor(Registries.BLOCK)) {
                return (Supplier<? extends Block>) entry;
            }
        } else if (supplier instanceof MemoizedBlockSupplier<?> blockSupplier) {
            return blockSupplier;
        }
        return null;
    }

    public static void reinitializeMaterialData() {
        // Clear old data
        MATERIAL_ENTRY_ITEM_MAP.clear();
        MATERIAL_ENTRY_BLOCK_MAP.clear();
        ITEM_MATERIAL_ENTRY.clear();
        FLUID_MATERIAL.clear();

        // Load new data
        TagsHandler.initExtraUnificationEntries();
        for (TagPrefix prefix : TagPrefix.values()) {
            prefix.getIgnored().forEach((mat, items) -> registerMaterialEntries(Arrays.asList(items), prefix, mat));
        }
        GTMaterialItems.toUnify
                .forEach((materialEntry, supplier) -> registerMaterialEntry(supplier, materialEntry));
        WoodMachineRecipes.registerMaterialInfo();
        StoneMachineRecipes.registerMaterialInfo();
    }

    @ApiStatus.Internal
    public static void resolveItemMaterialInfos() {
        for (var entry : UNRESOLVED_ITEM_MATERIAL_INFO.entrySet()) {
            List<MaterialStack> stacks = new ObjectArrayList<>();
            var stack = entry.getKey();
            var count = stack.getCount();
            for (var input : entry.getValue()) {
                var matStack = getMaterialInfo(input.getItem());
                if (matStack != null) {
                    matStack.getMaterials()
                            .forEach(ms -> stacks.add(new MaterialStack(ms.material(), ms.amount() / count)));
                }
            }
            if (stacks.isEmpty()) continue;
            var matInfo = ITEM_MATERIAL_INFO.get(stack.getItem());
            if (matInfo == null) {
                matInfo = new ItemMaterialInfo(stacks);
                ITEM_MATERIAL_INFO.put(stack.getItem(), matInfo);
            } else {
                matInfo.addMaterialStacks(stacks);
            }
            RecyclingRecipes.registerRecyclingRecipes(entry.getKey().copyWithCount(1),
                    matInfo.getMaterials(), false, null);
        }
        UNRESOLVED_ITEM_MATERIAL_INFO.clear();
    }
}
