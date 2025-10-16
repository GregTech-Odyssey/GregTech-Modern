package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.data.pack.GTDynamicDataPack;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.NBTToJsonConverter;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public class CampfireRecipeBuilder {

    private Ingredient input;
    protected String group;
    private ItemStack output = ItemStack.EMPTY;
    private float experience;
    private int cookingTime;
    protected ResourceLocation id;

    public CampfireRecipeBuilder(@Nullable ResourceLocation id) {
        this.id = id;
    }

    public CampfireRecipeBuilder input(TagKey<Item> tagKey) {
        return input(ShapedRecipeBuilder.INGREDIENT_TAG_FUNCTION.apply(tagKey));
    }

    public CampfireRecipeBuilder input(ItemStack itemStack) {
        input = itemStack.hasTag() ? StrictNBTIngredient.of(itemStack) : ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemStack.getItem());
        return this;
    }

    public CampfireRecipeBuilder input(ItemLike itemLike) {
        return input(ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(itemLike.asItem()));
    }

    public CampfireRecipeBuilder input(Ingredient ingredient) {
        input = ingredient;
        return this;
    }

    public CampfireRecipeBuilder output(ItemStack itemStack) {
        this.output = itemStack.copy();
        return this;
    }

    public CampfireRecipeBuilder output(ItemStack itemStack, int count) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        return this;
    }

    public CampfireRecipeBuilder output(ItemStack itemStack, int count, CompoundTag nbt) {
        this.output = itemStack.copy();
        this.output.setCount(count);
        this.output.setTag(nbt);
        return this;
    }

    protected ResourceLocation defaultId() {
        return GTUtil.ITEM_ID.apply(output.getItem());
    }

    public void toJson(JsonObject json) {
        if (group != null) {
            json.addProperty("group", group);
        }
        if (!input.isEmpty()) {
            json.add("ingredient", input.toJson());
        }
        if (output.isEmpty()) {
            GTCEu.LOGGER.error("shapeless recipe {} output is empty", id);
            throw new IllegalArgumentException(id + ": output items is empty");
        } else {
            JsonObject result = new JsonObject();
            result.addProperty("item", GTUtil.ITEM_ID.apply(output.getItem()).toString());
            if (output.getCount() > 1) {
                result.addProperty("count", output.getCount());
            }
            if (output.hasTag() && output.getTag() != null) {
                result.add("nbt", NBTToJsonConverter.getObject(output.getTag()));
            }
            json.add("result", result);
        }
        json.addProperty("experience", experience);
        json.addProperty("cookingtime", cookingTime);
    }

    public void save() {
        GTDynamicDataPack.addRecipe(new FinishedRecipe() {

            @Override
            public void serializeRecipeData(JsonObject pJson) {
                toJson(pJson);
            }

            @Override
            public ResourceLocation getId() {
                var ID = id == null ? defaultId() : id;
                return new ResourceLocation(ID.getNamespace(), "campfire" + "/" + ID.getPath());
            }

            @Override
            public RecipeSerializer<?> getType() {
                return RecipeSerializer.CAMPFIRE_COOKING_RECIPE;
            }

            @Nullable
            @Override
            public JsonObject serializeAdvancement() {
                return null;
            }

            @Nullable
            @Override
            public ResourceLocation getAdvancementId() {
                return null;
            }
        });
    }

    /**
     * @return {@code this}.
     */
    public CampfireRecipeBuilder group(final String group) {
        this.group = group;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public CampfireRecipeBuilder experience(final float experience) {
        this.experience = experience;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public CampfireRecipeBuilder cookingTime(final int cookingTime) {
        this.cookingTime = cookingTime;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public CampfireRecipeBuilder id(final ResourceLocation id) {
        this.id = id;
        return this;
    }
}
