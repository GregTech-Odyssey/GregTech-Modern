package com.gregtechceu.gtceu.api.data.chemical;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.tag.TagUtil;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.GTValues.M;
import static com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ChemicalHelper {

    public static @Nullable ItemMaterialInfo getMaterialInfo(@Nullable Object object) {
        if (object instanceof ItemMaterialInfo materialInfo) {
            return materialInfo;
        } else if (object instanceof MaterialStack materialStack) {
            return new ItemMaterialInfo(materialStack);
        } else if (object instanceof ItemStack itemStack) {
            return ItemMaterialData.getMaterialInfo(itemStack.getItem());
        } else if (object instanceof ItemLike item) {
            return ItemMaterialData.getMaterialInfo(item);
        } else if (object instanceof MaterialEntry entry) {
            var items = getItems(entry);
            if (!items.isEmpty()) {
                return ItemMaterialData.getMaterialInfo(items.get(0));
            }
        } else if (object instanceof Ingredient ing) {
            for (var stack : ing.getItems()) {
                var ms = ItemMaterialData.getMaterialInfo(stack.getItem());
                if (ms != null) return ms;
            }
        }
        return null;
    }

    public static MaterialStack getMaterialStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) return MaterialStack.EMPTY;
        return getMaterialStack(itemStack.getItem());
    }

    public static MaterialStack getMaterialStack(@NotNull MaterialEntry entry) {
        Material entryMaterial = entry.material();
        if (!entryMaterial.isNull()) {
            return new MaterialStack(entryMaterial, entry.tagPrefix().getMaterialAmount(entryMaterial));
        }
        return MaterialStack.EMPTY;
    }

    public static MaterialStack getMaterialStack(ItemLike itemLike) {
        var entry = getMaterialEntry(itemLike);
        if (!entry.isEmpty()) {
            Material entryMaterial = entry.material();
            return new MaterialStack(entryMaterial, entry.tagPrefix().getMaterialAmount(entryMaterial));
        }
        ItemMaterialInfo info = ITEM_MATERIAL_INFO.get(itemLike.asItem());
        if (info == null) return MaterialStack.EMPTY;
        if (info.getMaterial().isEmpty()) {
            GTCEu.LOGGER.error("ItemMaterialInfo for {} is empty!", itemLike);
            return MaterialStack.EMPTY;
        }
        return info.getMaterial();
    }

    public static Material getMaterial(Fluid fluid) {
        if (FLUID_MATERIAL.isEmpty()) {
            Set<TagKey<Fluid>> allFluidTags = BuiltInRegistries.FLUID.getTagNames().collect(Collectors.toSet());
            for (final Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
                if (material.hasProperty(PropertyKey.FLUID)) {
                    FluidProperty property = material.getProperty(PropertyKey.FLUID);
                    FluidStorageKey.allKeys().stream()
                            .map(property::get)
                            .filter(Objects::nonNull)
                            .map(f -> Pair.of(f, TagUtil.createFluidTag(GTUtil.FLUID_ID.apply(f).getPath())))
                            .filter(pair -> allFluidTags.contains(pair.getSecond()))
                            .forEach(pair -> {
                                allFluidTags.remove(pair.getSecond());
                                FLUID_MATERIAL.put(pair.getFirst(), material);
                            });
                }
            }
        }
        return FLUID_MATERIAL.getOrDefault(fluid, GTMaterials.NULL);
    }

    public static TagPrefix getPrefix(ItemLike itemLike) {
        MaterialEntry entry = getMaterialEntry(itemLike);
        if (!entry.isEmpty()) return entry.tagPrefix();
        return TagPrefix.NULL_PREFIX;
    }

    public static ItemStack getDust(Material material, long materialAmount) {
        if (!material.hasProperty(PropertyKey.DUST) || materialAmount <= 0) {
            return ItemStack.EMPTY;
        }
        if (materialAmount % M == 0 || materialAmount >= M * 16) {
            return get(TagPrefix.dust, material, (int) (materialAmount / M));
        } else if ((materialAmount * 4) % M == 0 || materialAmount >= M * 8) {
            return get(TagPrefix.dustSmall, material, (int) ((materialAmount * 4) / M));
        } else if ((materialAmount * 9) >= M) {
            return get(TagPrefix.dustTiny, material, (int) ((materialAmount * 9) / M));
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack getDust(MaterialStack materialStack) {
        return getDust(materialStack.material(), materialStack.amount());
    }

    public static ItemStack getIngot(Material material, long materialAmount) {
        if (!material.hasProperty(PropertyKey.INGOT) || materialAmount <= 0)
            return ItemStack.EMPTY;
        if (materialAmount % (M * 9) == 0)
            return get(TagPrefix.block, material, (int) (materialAmount / (M * 9)));
        if (materialAmount % M == 0 || materialAmount >= M * 16)
            return get(TagPrefix.ingot, material, (int) (materialAmount / M));
        else if ((materialAmount * 9) >= M)
            return get(TagPrefix.nugget, material, (int) ((materialAmount * 9) / M));
        return ItemStack.EMPTY;
    }

    /**
     * Returns an Ingot of the material if it exists. Otherwise it returns a Dust.
     * Returns ItemStack.EMPTY if neither exist.
     */
    public static ItemStack getIngotOrDust(Material material, long materialAmount) {
        ItemStack ingotStack = getIngot(material, materialAmount);
        if (ingotStack != ItemStack.EMPTY) return ingotStack;
        return getDust(material, materialAmount);
    }

    public static ItemStack getIngotOrDust(MaterialStack materialStack) {
        return getIngotOrDust(materialStack.material(), materialStack.amount());
    }

    public static ItemStack getGem(MaterialStack materialStack) {
        if (materialStack.material().hasProperty(PropertyKey.GEM) &&
                !TagPrefix.gem.isIgnored(materialStack.material()) &&
                materialStack.amount() == TagPrefix.gem.getMaterialAmount(materialStack.material())) {
            return get(TagPrefix.gem, materialStack.material(), (int) (materialStack.amount() / M));
        }
        return getDust(materialStack);
    }

    public static MaterialEntry getMaterialEntry(ItemLike itemLike) {
        var itemKey = itemLike.asItem();
        if (itemKey instanceof IGTTool tool) {
            return new MaterialEntry(TagPrefix.nugget, tool.getMaterial());
        }
        var materialEntry = MaterialEntry.NULL_ENTRY;
        var entry = ITEM_MATERIAL_ENTRY_COLLECTED.get(itemKey);
        if (entry != null) {
            materialEntry = entry;
        } else if (!ITEM_MATERIAL_ENTRY.isEmpty()) {
            for (var e : ITEM_MATERIAL_ENTRY) {
                ITEM_MATERIAL_ENTRY_COLLECTED.put(e.getFirst().get().asItem(), e.getSecond());
            }
            ITEM_MATERIAL_ENTRY.clear();
            entry = ITEM_MATERIAL_ENTRY_COLLECTED.get(itemKey);
            if (entry != null) return entry;
        }
        return materialEntry;
    }

    public static List<Item> getItems(MaterialEntry MaterialEntry) {
        return getItems(MaterialEntry.tagPrefix(), MaterialEntry.material());
    }

    public static List<Item> getItems(TagPrefix tagPrefix, Material material) {
        if (material.isNull()) return Collections.emptyList();
        return material.MATERIAL_ENTRY_ITEM_LIKE_MAP.computeIfAbsent(tagPrefix, entry -> {
            var items = material.MATERIAL_ENTRY_ITEM_MAP.get(tagPrefix);
            if (items != null) {
                return (List<Item>) items.stream().map(Supplier::get).toList();
            }
            items = new ObjectArrayList<>();
            for (TagKey<Item> tag : tagPrefix.getItemTags(material)) {
                for (Holder<Item> itemHolder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    items.add(itemHolder::value);
                }
            }
            if (items.isEmpty() && tagPrefix.hasItemTable() && tagPrefix.doGenerateItem(material)) {
                return Collections.singletonList(tagPrefix.getItemFromTable(material).get().asItem());
            }
            return (List<Item>) items.stream().map(Supplier::get).toList();
        });
    }

    public static Item getItem(MaterialEntry MaterialEntry) {
        return getItem(MaterialEntry.tagPrefix(), MaterialEntry.material());
    }

    public static Item getItem(TagPrefix tagPrefix, Material material) {
        var list = getItems(tagPrefix, material);
        if (list.isEmpty()) return Items.AIR;
        return list.getFirst().asItem();
    }

    public static ItemStack get(MaterialEntry materialEntry, int size) {
        return get(materialEntry.tagPrefix(), materialEntry.material(), size);
    }

    public static ItemStack get(TagPrefix orePrefix, Material material, int stackSize) {
        var list = getItems(orePrefix, material);
        if (list.isEmpty()) return ItemStack.EMPTY;
        var stack = list.get(0).asItem().getDefaultInstance();
        stack.setCount(stackSize);
        return stack;
    }

    public static ItemStack get(TagPrefix orePrefix, Material material) {
        return get(orePrefix, material, 1);
    }

    public static List<Block> getBlocks(MaterialEntry materialEntry) {
        return getBlocks(materialEntry.tagPrefix(), materialEntry.material());
    }

    public static List<Block> getBlocks(TagPrefix tagPrefix, Material material) {
        if (material.isNull()) return Collections.emptyList();
        return material.MATERIAL_ENTRY_BLOCK_LIKE_MAP.computeIfAbsent(tagPrefix, entry -> {
            var blocks = material.MATERIAL_ENTRY_BLOCK_MAP.get(tagPrefix);
            if (blocks != null) {
                return (List<Block>) blocks.stream().map(Supplier::get).toList();
            }
            blocks = new ObjectArrayList<>();
            for (TagKey<Block> tag : tagPrefix.getBlockTags(material)) {
                for (Holder<Block> itemHolder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    blocks.add(itemHolder::value);
                }
            }
            if (blocks.isEmpty() && tagPrefix.hasItemTable() && tagPrefix.doGenerateBlock(material)) {
                var blockSupplier = ItemMaterialData.convertToBlock(tagPrefix.getItemFromTable(material));
                if (blockSupplier != null) {
                    return Collections.singletonList(blockSupplier.get());
                }
            }
            return (List<Block>) blocks.stream().map(Supplier::get).toList();
        });
    }

    @Nullable
    public static Block getBlock(MaterialEntry materialEntry) {
        return getBlock(materialEntry.tagPrefix(), materialEntry.material());
    }

    @Nullable
    public static Block getBlock(TagPrefix orePrefix, Material material) {
        var list = getBlocks(orePrefix, material);
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    @Nullable
    public static TagKey<Block> getBlockTag(TagPrefix orePrefix, @NotNull Material material) {
        var tags = orePrefix.getBlockTags(material);
        if (tags.length > 0) {
            return tags[0];
        }
        return null;
    }

    @Nullable
    public static TagKey<Item> getTag(TagPrefix orePrefix, @NotNull Material material) {
        var tags = orePrefix.getItemTags(material);
        if (tags.length > 0) {
            return tags[0];
        }
        return null;
    }

    public static List<Pair<ItemStack, ItemMaterialInfo>> getAllItemInfos() {
        List<Pair<ItemStack, ItemMaterialInfo>> f = new ObjectArrayList<>(ITEM_MATERIAL_INFO.size());
        for (var entry : ITEM_MATERIAL_INFO.entrySet()) {
            f.add(Pair.of(new ItemStack(entry.getKey()), entry.getValue()));
        }
        return f;
    }

    public static Optional<TagPrefix> getOrePrefix(BlockState state) {
        return ORES_INVERSE.entrySet().stream()
                .filter(entry -> entry.getKey().get().equals(state))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
