package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import org.jetbrains.annotations.Nullable;

public class ShapelessRecipeBuilder {

    private final NonNullList<Ingredient> ingredients = NonNullList.create();
    protected String group;
    private ItemStack output = ItemStack.EMPTY;
    protected ResourceLocation id;

    public ShapelessRecipeBuilder(@Nullable ResourceLocation id) {
        this.id = id;
    }

    public ShapelessRecipeBuilder requires(TagKey<Item> tagKey) {
        return requires(ShapedRecipeBuilder.INGREDIENT_TAG_FUNCTION.apply(tagKey));
    }

    public ShapelessRecipeBuilder requires(ItemStack itemStack) {
        requires(itemStack.hasTag() ? StrictNBTIngredient.of(itemStack) : ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemStack.getItem()));
        return this;
    }

    public ShapelessRecipeBuilder requires(ItemLike itemLike) {
        return requires(ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemLike.asItem()));
    }

    public ShapelessRecipeBuilder requires(Ingredient ingredient) {
        ingredients.add(ingredient);
        return this;
    }

    public ShapelessRecipeBuilder output(ItemStack itemStack) {
        this.output = itemStack.copy();
        return this;
    }

    public ShapelessRecipeBuilder output(ItemStack itemStack, int count) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        return this;
    }

    public ShapelessRecipeBuilder output(ItemStack itemStack, int count, CompoundTag nbt) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        this.output.setTag(nbt);
        return this;
    }

    protected ResourceLocation defaultId() {
        return GTUtil.ITEM_ID.apply(output.getItem());
    }

    public ResourceLocation getId() {
        var ID = id == null ? defaultId() : id;
        return GTUtil.getResourceLocation(ID.getNamespace(), "shapeless" + "/" + ID.getPath());
    }

    public void save() {
        var id = getId();
        GTRecipes.RECIPE_MAP.put(id, new ShapelessRecipe(id, group, CraftingBookCategory.MISC, output, ingredients));
    }

    /**
     * @return {@code this}.
     */
    public ShapelessRecipeBuilder group(final String group) {
        this.group = group;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public ShapelessRecipeBuilder experience(final float experience) {
        return this;
    }

    /**
     * @return {@code this}.
     */
    public ShapelessRecipeBuilder cookingTime(final int cookingTime) {
        return this;
    }

    /**
     * @return {@code this}.
     */
    public ShapelessRecipeBuilder id(final ResourceLocation id) {
        this.id = id;
        return this;
    }
}
