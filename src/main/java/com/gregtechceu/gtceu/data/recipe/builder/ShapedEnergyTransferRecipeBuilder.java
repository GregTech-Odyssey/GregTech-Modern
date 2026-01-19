package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.api.recipe.ShapedEnergyTransferRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.core.mixins.ShapedRecipeAccessor;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.Builder;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import org.jetbrains.annotations.Nullable;

public class ShapedEnergyTransferRecipeBuilder extends Builder<Ingredient, ShapedEnergyTransferRecipeBuilder> {

    protected ItemStack output = ItemStack.EMPTY;
    protected Ingredient chargeIngredient = Ingredient.EMPTY;
    protected ResourceLocation id;
    protected String group;
    protected boolean transferMaxCharge;
    protected boolean overrideCharge;

    public ShapedEnergyTransferRecipeBuilder(@Nullable ResourceLocation id) {
        this.id = id;
    }

    public ShapedEnergyTransferRecipeBuilder() {
        this(null);
    }

    public ShapedEnergyTransferRecipeBuilder pattern(String slice) {
        return aisle(slice);
    }

    public ShapedEnergyTransferRecipeBuilder define(char cha, TagKey<Item> tagKey) {
        return where(cha, ShapedRecipeBuilder.INGREDIENT_TAG_FUNCTION.apply(tagKey));
    }

    public ShapedEnergyTransferRecipeBuilder define(char cha, ItemStack itemStack) {
        return where(cha, itemStack.hasTag() ? StrictNBTIngredient.of(itemStack) : ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemStack.getItem()));
    }

    public ShapedEnergyTransferRecipeBuilder define(char cha, ItemLike itemLike) {
        return where(cha, ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemLike.asItem()));
    }

    public ShapedEnergyTransferRecipeBuilder define(char cha, Ingredient ingredient) {
        return where(cha, ingredient);
    }

    public ShapedEnergyTransferRecipeBuilder chargeIngredient(Ingredient chargeIngredient) {
        this.chargeIngredient = chargeIngredient;
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder overrideCharge(boolean overrideCharge) {
        this.overrideCharge = overrideCharge;
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder transferMaxCharge(boolean transferMaxCharge) {
        this.transferMaxCharge = transferMaxCharge;
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder output(ItemStack itemStack) {
        this.output = itemStack.copy();
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder output(ItemStack itemStack, int count) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder output(ItemStack itemStack, int count, CompoundTag nbt) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        this.output.setTag(nbt);
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder id(ResourceLocation id) {
        this.id = id;
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder id(String id) {
        this.id = GTUtil.getResourceLocation(id);
        return this;
    }

    public ShapedEnergyTransferRecipeBuilder group(String group) {
        this.group = group;
        return this;
    }

    @Override
    public ShapedEnergyTransferRecipeBuilder shallowCopy() {
        var builder = super.shallowCopy();
        builder.output = output.copy();
        return builder;
    }

    public ResourceLocation getId() {
        var ID = id == null ? defaultId() : id;
        return GTUtil.getResourceLocation(ID.getNamespace(), "shaped" + "/" + ID.getPath());
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
        GTRecipes.RECIPE_MAP.put(id, new ShapedEnergyTransferRecipe(id, group, xSize, ySize, chargeIngredient, overrideCharge, transferMaxCharge, dissolved, output));
    }
}
