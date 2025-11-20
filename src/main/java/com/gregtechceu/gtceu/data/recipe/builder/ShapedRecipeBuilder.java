package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.api.recipe.StrictShapedRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.core.mixins.ShapedRecipeAccessor;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.collection.O2OOpenCacheHashMap;

import com.lowdragmc.lowdraglib.utils.Builder;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ShapedRecipeBuilder extends Builder<Ingredient, ShapedRecipeBuilder> {

    public static String[] shapeToPattern(List<String[]> shape) {
        var pattern = new ObjectArrayList<String>();
        for (String[] strings : shape) {
            pattern.addAll(Arrays.asList(strings));
        }
        return pattern.toArray(new String[0]);
    }

    public static Map<String, Ingredient> symbolMapTokeys(Map<Character, Ingredient> symbolMap) {
        Map<String, Ingredient> keys = new O2OOpenCacheHashMap<>();
        symbolMap.forEach((k, v) -> keys.put(k.toString(), v));
        keys.put(" ", Ingredient.EMPTY);
        return keys;
    }

    public static Function<Item, Ingredient> INGREDIENT_ITEM_FUNCTION = Ingredient::of;
    public static Function<TagKey<Item>, Ingredient> INGREDIENT_TAG_FUNCTION = Ingredient::of;

    protected ItemStack output = ItemStack.EMPTY;
    protected ResourceLocation id;
    protected String group;
    protected boolean isStrict;

    public ShapedRecipeBuilder(@Nullable ResourceLocation id) {
        this.id = id;
    }

    public ShapedRecipeBuilder() {
        this(null);
    }

    public ShapedRecipeBuilder pattern(String slice) {
        return aisle(slice);
    }

    public ShapedRecipeBuilder define(char cha, TagKey<Item> tagKey) {
        return where(cha, INGREDIENT_TAG_FUNCTION.apply(tagKey));
    }

    public ShapedRecipeBuilder define(char cha, ItemStack itemStack) {
        return where(cha, itemStack.hasTag() ? StrictNBTIngredient.of(itemStack) : INGREDIENT_ITEM_FUNCTION.apply(itemStack.getItem()));
    }

    public ShapedRecipeBuilder define(char cha, ItemLike itemLike) {
        return where(cha, INGREDIENT_ITEM_FUNCTION.apply(itemLike.asItem()));
    }

    public ShapedRecipeBuilder define(char cha, Ingredient ingredient) {
        return where(cha, ingredient);
    }

    public ShapedRecipeBuilder output(ItemStack itemStack) {
        this.output = itemStack.copy();
        return this;
    }

    public ShapedRecipeBuilder output(ItemStack itemStack, int count) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        return this;
    }

    public ShapedRecipeBuilder output(ItemStack itemStack, int count, CompoundTag nbt) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        this.output.setTag(nbt);
        return this;
    }

    public ShapedRecipeBuilder id(ResourceLocation id) {
        this.id = id;
        return this;
    }

    public ShapedRecipeBuilder id(String id) {
        this.id = new ResourceLocation(id);
        return this;
    }

    public ShapedRecipeBuilder group(String group) {
        this.group = group;
        return this;
    }

    public ShapedRecipeBuilder isStrict(boolean isStrict) {
        this.isStrict = isStrict;
        return this;
    }

    @Override
    public ShapedRecipeBuilder shallowCopy() {
        var builder = super.shallowCopy();
        builder.output = output.copy();
        return builder;
    }

    public ResourceLocation getId() {
        var ID = id == null ? defaultId() : id;
        return new ResourceLocation(ID.getNamespace(), "shaped" + "/" + ID.getPath());
    }

    protected ResourceLocation defaultId() {
        return GTUtil.ITEM_ID.apply(output.getItem());
    }

    public void save() {
        var id = getId();
        var key = ShapedRecipeBuilder.symbolMapTokeys(symbolMap);
        String[] pattern = ShapedRecipeBuilder.shapeToPattern(shape);
        int xSize = pattern[0].length();
        int ySize = pattern.length;
        NonNullList<Ingredient> dissolved = ShapedRecipeAccessor.callDissolvePattern(pattern, key, xSize, ySize);
        GTRecipes.RECIPE_MAP.put(id, isStrict ? new StrictShapedRecipe(id, group, CraftingBookCategory.MISC, xSize, ySize, dissolved, output) : new ShapedRecipe(id, group, CraftingBookCategory.MISC, xSize, ySize, dissolved, output));
    }
}
