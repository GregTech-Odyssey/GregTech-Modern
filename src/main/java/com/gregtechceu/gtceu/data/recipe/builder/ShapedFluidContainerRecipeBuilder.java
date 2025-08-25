package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.api.recipe.ShapedFluidContainerRecipe;
import com.gregtechceu.gtceu.data.pack.GTDynamicDataPack;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public class ShapedFluidContainerRecipeBuilder extends ShapedRecipeBuilder {

    public ShapedFluidContainerRecipeBuilder(@Nullable ResourceLocation id) {
        super(id);
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
                return new ResourceLocation(ID.getNamespace(), "shaped_fluid_container/" + ID.getPath());
            }

            @Override
            public RecipeSerializer<?> getType() {
                return ShapedFluidContainerRecipe.SERIALIZER;
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
}
