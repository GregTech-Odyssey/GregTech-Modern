package com.gregtechceu.gtceu.data.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.MarkerMaterial;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.data.recipe.builder.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

public class VanillaRecipeHelper {

    public static void addSmeltingRecipe(@NotNull String regName, TagKey<Item> input,
                                         ItemStack output) {
        addSmeltingRecipe(GTCEu.id(regName), input, output);
    }

    public static void addSmeltingRecipe(@NotNull ResourceLocation regName,
                                         TagKey<Item> input, ItemStack output) {
        addSmeltingRecipe(regName, input, output, 0.0f);
    }

    public static void addSmeltingRecipe(@NotNull String regName, TagKey<Item> input,
                                         ItemStack output, float experience) {
        addSmeltingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addSmeltingRecipe(@NotNull String regName, Ingredient input,
                                         ItemStack output, float experience) {
        addSmeltingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addSmeltingRecipe(@NotNull ResourceLocation regName,
                                         Ingredient input, ItemStack output, float experience) {
        new SmeltingRecipeBuilder(regName).input(input).output(output).cookingTime(200).experience(experience)
                .save();
    }

    public static void addSmeltingRecipe(@NotNull ResourceLocation regName,
                                         TagKey<Item> input, ItemStack output, float experience) {
        new SmeltingRecipeBuilder(regName).input(input).output(output).cookingTime(200).experience(experience)
                .save();
    }

    public static void addBlastingRecipe(@NotNull String regName, TagKey<Item> input,
                                         ItemStack output, float experience) {
        addBlastingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addBlastingRecipe(@NotNull String regName, Ingredient input,
                                         ItemStack output, float experience) {
        addBlastingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addBlastingRecipe(@NotNull ResourceLocation regName,
                                         Ingredient input, ItemStack output, float experience) {
        new BlastingRecipeBuilder(regName).input(input).output(output).cookingTime(100).experience(experience)
                .save();
    }

    public static void addBlastingRecipe(@NotNull ResourceLocation regName,
                                         TagKey<Item> input, ItemStack output, float experience) {
        new BlastingRecipeBuilder(regName).input(input).output(output).cookingTime(100).experience(experience)
                .save();
    }

    public static void addSmokingRecipe(@NotNull String regName, TagKey<Item> input,
                                        ItemStack output, float experience) {
        addSmokingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addSmokingRecipe(@NotNull String regName, ItemStack input,
                                        ItemStack output, float experience) {
        addSmokingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addSmokingRecipe(@NotNull String regName, TagKey<Item> input,
                                        ItemStack output) {
        addSmokingRecipe(GTCEu.id(regName), input, output, 0);
    }

    public static void addSmokingRecipe(@NotNull String regName, ItemStack input,
                                        ItemStack output) {
        addSmokingRecipe(GTCEu.id(regName), input, output, 0);
    }

    public static void addSmokingRecipe(@NotNull ResourceLocation regName,
                                        TagKey<Item> input, ItemStack output, float experience) {
        new SmokingRecipeBuilder(regName).input(input).output(output).cookingTime(100).experience(experience)
                .save();
    }

    public static void addSmokingRecipe(@NotNull ResourceLocation regName,
                                        ItemStack input, ItemStack output, float experience) {
        new SmokingRecipeBuilder(regName).input(input).output(output).cookingTime(100).experience(experience)
                .save();
    }

    public static void addCampfireRecipe(@NotNull String regName, ItemStack input,
                                         ItemStack output, float experience) {
        addCampfireRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addCampfireRecipe(@NotNull String regName, ItemStack input,
                                         ItemStack output) {
        addCampfireRecipe(GTCEu.id(regName), input, output, 0);
    }

    public static void addCampfireRecipe(@NotNull ResourceLocation regName,
                                         ItemStack input, ItemStack output, float experience) {
        new CampfireRecipeBuilder(regName).input(input).output(output).cookingTime(100).experience(experience)
                .save();
    }

    public static void addCampfireRecipe(@NotNull String regName, TagKey<Item> input,
                                         ItemStack output, float experience) {
        addCampfireRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addCampfireRecipe(@NotNull String regName, TagKey<Item> input,
                                         ItemStack output) {
        addCampfireRecipe(GTCEu.id(regName), input, output, 0);
    }

    public static void addCampfireRecipe(@NotNull ResourceLocation regName,
                                         TagKey<Item> input, ItemStack output, float experience) {
        new CampfireRecipeBuilder(regName).input(input).output(output).cookingTime(100).experience(experience)
                .save();
    }

    public static void addSmeltingRecipe(@NotNull String regName, ItemStack input,
                                         ItemStack output) {
        addSmeltingRecipe(GTCEu.id(regName), input, output, 0.0f);
    }

    public static void addSmeltingRecipe(@NotNull String regName, Item input,
                                         Item output) {
        addSmeltingRecipe(GTCEu.id(regName), input.getDefaultInstance(), output.getDefaultInstance(), 0.0f);
    }

    public static void addSmeltingRecipe(@NotNull String regName, Item input,
                                         Item output, float experience) {
        addSmeltingRecipe(GTCEu.id(regName), input.getDefaultInstance(), output.getDefaultInstance(),
                experience);
    }

    public static void addSmeltingRecipe(@NotNull String regName, ItemStack input,
                                         ItemStack output, float experience) {
        addSmeltingRecipe(GTCEu.id(regName), input, output, experience);
    }

    public static void addSmeltingRecipe(@NotNull ResourceLocation regName,
                                         ItemStack input, ItemStack output, float experience) {
        new SmeltingRecipeBuilder(regName).input(input).output(output).cookingTime(200).experience(experience)
                .save();
    }

    /**
     * Adds a shaped recipe which clears the nbt of the outputs
     *
     * @see #addShapedRecipe(String, ItemStack, Object...)
     */
    public static void addShapedNBTClearingRecipe(String regName, ItemStack result,
                                                  Object... recipe) {
        addStrictShapedRecipe(regName, result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(@NotNull String regName,
                                       @NotNull ItemLike result, @NotNull Object... recipe) {
        addShapedRecipe(GTCEu.id(regName), result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(@NotNull String regName,
                                       @NotNull ItemStack result, @NotNull Object... recipe) {
        addShapedRecipe(GTCEu.id(regName), result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(@NotNull ResourceLocation regName,
                                       @NotNull ItemLike result, @NotNull Object... recipe) {
        addShapedRecipe(false, regName, result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(@NotNull ResourceLocation regName,
                                       @NotNull ItemStack result, @NotNull Object... recipe) {
        addShapedRecipe(false, regName, result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addStrictShapedRecipe(@NotNull String regName,
                                             @NotNull ItemStack result, @NotNull Object... recipe) {
        addStrictShapedRecipe(GTCEu.id(regName), result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addStrictShapedRecipe(@NotNull ResourceLocation regName,
                                             @NotNull ItemStack result, @NotNull Object... recipe) {
        addStrictShapedRecipe(false, regName, result, recipe);
    }

    /**
     * Adds Shaped Crafting Recipes.
     * <p/>
     * For Enums - {@link Enum#name()} is called.
     * <p/>
     * For {@link MaterialEntry} - {@link MaterialEntry#toString()} is called.
     * <p/>
     * Base tool names are as follows:
     * <ul>
     * <li>{@code 'c'} - {@code craftingToolCrowbar}</li>
     * <li>{@code 'd'} - {@code craftingToolScrewdriver}</li>
     * <li>{@code 'f'} - {@code craftingToolFile}</li>
     * <li>{@code 'h'} - {@code craftingToolHardHammer}</li>
     * <li>{@code 'k'} - {@code craftingToolKnife}</li>
     * <li>{@code 'm'} - {@code craftingToolMortar}</li>
     * <li>{@code 'r'} - {@code craftingToolSoftHammer}</li>
     * <li>{@code 's'} - {@code craftingToolSaw}</li>
     * <li>{@code 'w'} - {@code craftingToolWrench}</li>
     * <li>{@code 'x'} - {@code craftingToolWireCutter}</li>
     * </ul>
     *
     * @param setMaterialInfoData whether to add material decomposition information to the recipe output
     * @param regName             the registry name for the recipe
     * @param result              the output for the recipe
     * @param recipe              the contents of the recipe
     */
    public static void addShapedRecipe(boolean setMaterialInfoData, boolean isStrict,
                                       @NotNull ResourceLocation regName, @NotNull ItemStack result,
                                       @NotNull Object... recipe) {
        var builder = new ShapedRecipeBuilder(regName).output(result);
        builder.isStrict(isStrict);
        final CharSet tools = ToolHelper.getToolSymbols();
        CharSet foundTools = new CharArraySet(9);
        for (int i = 0; i < recipe.length; i++) {
            var o = recipe[i];
            if (o instanceof String pattern) {
                builder.pattern(pattern);
                for (char c : pattern.toCharArray()) {
                    if (tools.contains(c)) {
                        foundTools.add(c);
                    }
                }
            }
            if (o instanceof String[] pattern) {
                for (String s : pattern) {
                    builder.pattern(s);
                    for (char c : s.toCharArray()) {
                        if (tools.contains(c)) {
                            foundTools.add(c);
                        }
                    }
                }
            }
            if (o instanceof Character sign) {
                var content = recipe[i + 1];
                i++;
                switch (content) {
                    case Ingredient ingredient -> builder.define(sign, ingredient);
                    case ItemStack itemStack -> builder.define(sign, itemStack);
                    case TagKey<?> key -> builder.define(sign, (TagKey<Item>) key);
                    case TagPrefix prefix -> {
                        if (prefix.getItemParentTags().length > 0) {
                            builder.define(sign, prefix.getItemParentTags()[0]);
                        }
                    }
                    case ItemLike itemLike -> builder.define(sign, itemLike);
                    case MaterialEntry(TagPrefix tagPrefix, Material material) -> {
                        TagKey<Item> tag = ChemicalHelper.getTag(tagPrefix, material);
                        if (tag != null) {
                            builder.define(sign, tag);
                        } else builder.define(sign, ChemicalHelper.getItem(tagPrefix, material));
                    }
                    default -> {}
                }
            }
        }
        for (var it = foundTools.iterator(); it.hasNext();) {
            char c = it.nextChar();
            builder.define(c, ToolHelper.getToolFromSymbol(c).itemTags.getFirst());
        }
        builder.save();

        if (setMaterialInfoData) {
            ItemMaterialData.registerMaterialInfo(result.getItem(), getRecyclingIngredients(result.getCount(), recipe));
        }
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(boolean setMaterialInfoData,
                                       @NotNull String regName, @NotNull ItemLike result, @NotNull Object... recipe) {
        addShapedRecipe(setMaterialInfoData, GTCEu.id(regName), new ItemStack(result), recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(boolean setMaterialInfoData,
                                       @NotNull String regName, @NotNull ItemStack result, @NotNull Object... recipe) {
        addShapedRecipe(setMaterialInfoData, GTCEu.id(regName), result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(boolean setMaterialInfoData,
                                       @NotNull ResourceLocation regName, @NotNull ItemLike result,
                                       @NotNull Object... recipe) {
        addShapedRecipe(setMaterialInfoData, false, regName, new ItemStack(result.asItem()), recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addShapedRecipe(boolean setMaterialInfoData,
                                       @NotNull ResourceLocation regName, @NotNull ItemStack result,
                                       @NotNull Object... recipe) {
        addShapedRecipe(setMaterialInfoData, false, regName, result, recipe);
    }

    /**
     * @see #addShapedRecipe(boolean, boolean, ResourceLocation, ItemStack, Object...)
     */
    public static void addStrictShapedRecipe(boolean setMaterialInfoData,
                                             @NotNull ResourceLocation regName, @NotNull ItemStack result,
                                             @NotNull Object... recipe) {
        addShapedRecipe(setMaterialInfoData, true, regName, result, recipe);
    }

    public static void addShapelessRecipe(@NotNull String regName,
                                          @NotNull ItemStack result, @NotNull Object... recipe) {
        addShapelessRecipe(GTCEu.id(regName), result, recipe);
    }

    public static void addShapedEnergyTransferRecipe(boolean setMaterialInfoData,
                                                     boolean overrideCharge, boolean transferMaxCharge,
                                                     @NotNull ResourceLocation regName,
                                                     @NotNull Ingredient chargeIngredient, @NotNull ItemStack result,
                                                     @NotNull Object... recipe) {
        var builder = new ShapedEnergyTransferRecipeBuilder(regName).output(result);
        builder.chargeIngredient(chargeIngredient).overrideCharge(overrideCharge).transferMaxCharge(transferMaxCharge);
        final CharSet tools = ToolHelper.getToolSymbols();
        CharSet foundTools = new CharArraySet(9);
        for (int i = 0; i < recipe.length; i++) {
            var o = recipe[i];
            if (o instanceof String pattern) {
                builder.pattern(pattern);
                for (char c : pattern.toCharArray()) {
                    if (tools.contains(c)) {
                        foundTools.add(c);
                    }
                }
            }
            if (o instanceof String[] pattern) {
                for (String s : pattern) {
                    builder.pattern(s);
                    for (char c : s.toCharArray()) {
                        if (tools.contains(c)) {
                            foundTools.add(c);
                        }
                    }
                }
            }
            if (o instanceof Character sign) {
                var content = recipe[i + 1];
                i++;
                switch (content) {
                    case Ingredient ingredient -> builder.define(sign, ingredient);
                    case ItemStack itemStack -> builder.define(sign, itemStack);
                    case TagKey<?> key -> builder.define(sign, (TagKey<Item>) key);
                    case ItemLike itemLike -> builder.define(sign, itemLike);
                    case MaterialEntry(TagPrefix tagPrefix, Material material) -> {
                        TagKey<Item> tag = ChemicalHelper.getTag(tagPrefix, material);
                        if (tag != null) {
                            builder.define(sign, tag);
                        } else builder.define(sign, ChemicalHelper.getItem(tagPrefix, material));
                    }
                    default -> {}
                }
            }
        }
        for (var it = foundTools.iterator(); it.hasNext();) {
            char c = it.nextChar();
            builder.define(c, ToolHelper.getToolFromSymbol(c).itemTags.getFirst());
        }
        builder.save();

        if (setMaterialInfoData) {
            ItemMaterialData.registerMaterialInfo(result.getItem(), getRecyclingIngredients(result.getCount(), recipe));
        }
    }

    public static void addShapedEnergyTransferRecipe(boolean setMaterialInfoData,
                                                     boolean overrideCharge, boolean transferMaxCharge,
                                                     @NotNull String regName, @NotNull Ingredient chargeIngredient,
                                                     @NotNull ItemStack result, @NotNull Object... recipe) {
        addShapedEnergyTransferRecipe(setMaterialInfoData, overrideCharge, transferMaxCharge,
                GTCEu.id(regName), chargeIngredient, result, recipe);
    }

    public static void addShapedFluidContainerRecipe(boolean setMaterialInfoData,
                                                     boolean isStrict,
                                                     @NotNull ResourceLocation regName, @NotNull ItemStack result,
                                                     @NotNull Object... recipe) {
        var builder = new ShapedFluidContainerRecipeBuilder(regName).output(result);
        builder.isStrict(isStrict);
        final CharSet tools = ToolHelper.getToolSymbols();
        CharSet foundTools = new CharArraySet(9);
        for (int i = 0; i < recipe.length; i++) {
            var o = recipe[i];
            if (o instanceof String pattern) {
                builder.pattern(pattern);
                for (char c : pattern.toCharArray()) {
                    if (tools.contains(c)) {
                        foundTools.add(c);
                    }
                }
            }
            if (o instanceof String[] pattern) {
                for (String s : pattern) {
                    builder.pattern(s);
                    for (char c : s.toCharArray()) {
                        if (tools.contains(c)) {
                            foundTools.add(c);
                        }
                    }
                }
            }
            if (o instanceof Character sign) {
                var content = recipe[i + 1];
                i++;
                switch (content) {
                    case Ingredient ingredient -> builder.define(sign, ingredient);
                    case ItemStack itemStack -> builder.define(sign, itemStack);
                    case TagKey<?> key -> builder.define(sign, (TagKey<Item>) key);
                    case TagPrefix prefix -> {
                        if (prefix.getItemParentTags().length > 0) {
                            builder.define(sign, prefix.getItemParentTags()[0]);
                        }
                    }
                    case ItemLike itemLike -> builder.define(sign, itemLike);
                    case MaterialEntry(TagPrefix tagPrefix, Material material) -> {
                        TagKey<Item> tag = ChemicalHelper.getTag(tagPrefix, material);
                        if (tag != null) {
                            builder.define(sign, tag);
                        } else builder.define(sign, ChemicalHelper.getItem(tagPrefix, material));
                    }
                    default -> {}
                }
            }
        }
        for (var it = foundTools.iterator(); it.hasNext();) {
            char c = it.nextChar();
            builder.define(c, ToolHelper.getToolFromSymbol(c).itemTags.getFirst());
        }

        builder.save();

        if (setMaterialInfoData) {
            ItemMaterialData.registerMaterialInfo(result.getItem(), getRecyclingIngredients(result.getCount(), recipe));
        }
    }

    public static void addShapedFluidContainerRecipe(boolean setMaterialInfoData,
                                                     @NotNull String regName, @NotNull ItemStack result,
                                                     @NotNull Object... recipe) {
        addShapedFluidContainerRecipe(setMaterialInfoData, GTCEu.id(regName), result, recipe);
    }

    public static void addShapedFluidContainerRecipe(boolean setMaterialInfoData,
                                                     @NotNull ResourceLocation regName, @NotNull ItemStack result,

                                                     @NotNull Object... recipe) {
        addShapedFluidContainerRecipe(setMaterialInfoData, false, regName, result, recipe);
    }

    public static void addShapedFluidContainerRecipe(@NotNull String regName,
                                                     @NotNull ItemStack result,
                                                     @NotNull Object... recipe) {
        addShapedFluidContainerRecipe(GTCEu.id(regName), result, recipe);
    }

    public static void addShapedFluidContainerRecipe(@NotNull ResourceLocation regName,
                                                     @NotNull ItemStack result,
                                                     @NotNull Object... recipe) {
        addShapedFluidContainerRecipe(false, regName, result, recipe);
    }

    /**
     * Adds a shapeless recipe which clears the nbt of the outputs
     *
     * @see #addShapelessRecipe(String, ItemStack, Object...)
     */
    public static void addShapelessNBTClearingRecipe(@NotNull String regName,
                                                     @NotNull ItemStack result,
                                                     @NotNull Object... recipe) {
        addShapelessRecipe(regName, result, recipe);
    }

    public static void addShapelessRecipe(@NotNull ResourceLocation regName,
                                          @NotNull ItemLike result, @NotNull Object... recipe) {
        addShapelessRecipe(regName, new ItemStack(result), recipe);
    }

    public static void addShapelessRecipe(@NotNull ResourceLocation regName,
                                          @NotNull ItemStack result, @NotNull Object... recipe) {
        var builder = new ShapelessRecipeBuilder(regName).output(result);
        for (Object content : recipe) {
            switch (content) {
                case Ingredient ingredient -> builder.requires(ingredient);
                case ItemStack itemStack -> builder.requires(itemStack);
                case TagKey<?> key -> builder.requires((TagKey<Item>) key);
                case ItemLike itemLike -> builder.requires(itemLike);
                case MaterialEntry(TagPrefix tagPrefix, Material material) -> {
                    TagKey<Item> tag = ChemicalHelper.getTag(tagPrefix, material);
                    if (tag != null) {
                        builder.requires(tag);
                    } else builder.requires(ChemicalHelper.getItem(tagPrefix, material));
                }
                case Character c -> builder.requires(ToolHelper.getToolFromSymbol(c).itemTags.getFirst());
                default -> {}
            }
        }
        builder.save();
    }

    /**
     * @param material the material to check
     * @return if the material is a wood
     */
    public static boolean isMaterialWood(@NotNull Material material) {
        return !material.isNull() && material.hasProperty(PropertyKey.WOOD);
    }

    public static ItemMaterialInfo getRecyclingIngredients(int outputCount, @NotNull Object... recipe) {
        Char2IntOpenHashMap inputCountMap = new Char2IntOpenHashMap();
        Reference2LongOpenHashMap<Material> materialStacksExploded = new Reference2LongOpenHashMap<>();

        int itr = 0;
        while (recipe[itr] instanceof String s) {
            for (char c : s.toCharArray()) {
                if (ToolHelper.getToolFromSymbol(c) != null) continue; // skip tools
                inputCountMap.addTo(c, 1);
            }
            itr++;
        }

        char lastChar = ' ';
        for (int i = itr; i < recipe.length; i++) {
            Object ingredient = recipe[i];

            // Track the current working ingredient symbol
            if (ingredient instanceof Character) {
                lastChar = (char) ingredient;
                continue;
            }

            // Should never happen if recipe is formatted correctly
            // In the case that it isn't, this error should be handled
            // by an earlier method call parsing the recipe.
            if (lastChar == ' ') {
                throw new IllegalArgumentException("Invalid recipe format, no symbol found for ingredient: " + ingredient);
            }

            ItemLike itemLike;
            switch (ingredient) {
                case Ingredient ingr -> {
                    ItemStack[] stacks = ingr.getItems();
                    if (stacks.length == 0) continue;
                    ItemStack stack = stacks[0];
                    if (stack == ItemStack.EMPTY) continue;
                    itemLike = stack.getItem();
                }
                case ItemStack itemStack -> itemLike = itemStack.getItem();
                case TagKey<?> key -> {
                    continue; // todo can this be improved?
                }
                case ItemLike like -> itemLike = like;
                case MaterialEntry(TagPrefix tagPrefix, Material material) -> {
                    itemLike = ChemicalHelper.getItem(tagPrefix, material);
                    if (itemLike == Items.AIR) continue;
                }
                default -> {
                    continue; // throw out bad entries
                }
            }

            // First try to get ItemMaterialInfo
            ItemMaterialInfo info = ItemMaterialData.getMaterialInfo(itemLike);
            if (info != null) {
                for (MaterialStack ms : info.getMaterials()) {
                    if (!(ms.material() instanceof MarkerMaterial)) {
                        addMaterialStack(materialStacksExploded, inputCountMap.get(lastChar), outputCount, ms);
                    }
                }
                continue;
            }

            // Then try to get a single Material (UnificationEntry needs this, for example)
            MaterialStack materialStack = ChemicalHelper.getMaterialStack(itemLike);
            if (!materialStack.isEmpty() && !(materialStack.material() instanceof MarkerMaterial)) {
                addMaterialStack(materialStacksExploded, inputCountMap.get(lastChar), outputCount, materialStack);
            }

            // Gather any secondary materials if this item has an OrePrefix
            TagPrefix prefix = ChemicalHelper.getPrefix(itemLike);
            if (!prefix.isEmpty() && !prefix.secondaryMaterials().isEmpty()) {
                for (MaterialStack ms : prefix.secondaryMaterials()) {
                    addMaterialStack(materialStacksExploded, inputCountMap.get(lastChar), outputCount, ms);
                }
            }
        }

        return new ItemMaterialInfo(materialStacksExploded);
    }

    private static void addMaterialStack(@NotNull Reference2LongOpenHashMap<Material> materialStacksExploded,
                                         int inputCount, int outputCount, @NotNull MaterialStack ms) {
        materialStacksExploded.addTo(ms.material(), (ms.amount() * inputCount / outputCount));
    }
}
