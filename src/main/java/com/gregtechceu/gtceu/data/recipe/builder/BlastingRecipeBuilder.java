package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import org.jetbrains.annotations.Nullable;

public class BlastingRecipeBuilder {

    private Ingredient input;
    protected String group;
    private ItemStack output = ItemStack.EMPTY;
    private float experience;
    private int cookingTime;
    protected ResourceLocation id;

    public BlastingRecipeBuilder(@Nullable ResourceLocation id) {
        this.id = id;
    }

    public BlastingRecipeBuilder input(TagKey<Item> tagKey) {
        return input(ShapedRecipeBuilder.INGREDIENT_TAG_FUNCTION.apply(tagKey));
    }

    public BlastingRecipeBuilder input(ItemStack itemStack) {
        input = itemStack.hasTag() ? StrictNBTIngredient.of(itemStack) : ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemStack.getItem());
        return this;
    }

    public BlastingRecipeBuilder input(ItemLike itemLike) {
        return input(ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemLike.asItem()));
    }

    public BlastingRecipeBuilder input(Ingredient ingredient) {
        input = ingredient;
        return this;
    }

    public BlastingRecipeBuilder output(ItemStack itemStack) {
        this.output = itemStack.copy();
        return this;
    }

    public BlastingRecipeBuilder output(ItemStack itemStack, int count) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        return this;
    }

    public BlastingRecipeBuilder output(ItemStack itemStack, int count, CompoundTag nbt) {
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
        return new ResourceLocation(ID.getNamespace(), "blasting" + "/" + ID.getPath());
    }

    public void save() {
        var id = getId();
        GTRecipes.RECIPE_MAP.put(id, new BlastingRecipe(id, group, CookingBookCategory.MISC, input, output, experience, cookingTime));
    }

    /**
     * @return {@code this}.
     */
    public BlastingRecipeBuilder group(final String group) {
        this.group = group;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public BlastingRecipeBuilder experience(final float experience) {
        this.experience = experience;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public BlastingRecipeBuilder cookingTime(final int cookingTime) {
        this.cookingTime = cookingTime;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public BlastingRecipeBuilder id(final ResourceLocation id) {
        this.id = id;
        return this;
    }
}
