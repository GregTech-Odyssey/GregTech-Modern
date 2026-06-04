package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.expand.ContentExpander;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.fast.recipesearch.IntMapContainer;
import com.gto.datasynclib.datasream.DataComponentMap;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.util.DataCodecs;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Range;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipeDefinition {

    public static final ByteStreamCodec<GTRecipeDefinition> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public GTRecipeDefinition decode(FriendlyByteBuf buf) {
            var type = GTRegistries.RECIPE_TYPES.streamCodec().decode(buf);
            if (buf.readBoolean()) {
                var id = StreamCodecs.RESOURCE_LOCATION_CODEC.decode(buf);
                var definition = type.recipes.get(id);
                return definition == null ? type.defaultDefinition : definition;
            }
            return type.defaultDefinition;
        }

        @Override
        public void encode(FriendlyByteBuf buf, GTRecipeDefinition recipe) {
            GTRegistries.RECIPE_TYPES.streamCodec().encode(buf, recipe.recipeType);
            if (recipe.registered) {
                buf.writeBoolean(true);
                StreamCodecs.RESOURCE_LOCATION_CODEC.encode(buf, recipe.id);
            } else {
                buf.writeBoolean(false);
            }
        }
    };

    public static final DataCodec<GTRecipeDefinition> DATA_CODEC = new DataCodec<>() {

        @Override
        public Data encode(GTRecipeDefinition recipe) {
            var list = new ListData(2);
            list.add(GTRegistries.RECIPE_TYPES.dataCodec().encode(recipe.recipeType));
            if (recipe.registered) {
                list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(recipe.id));
            } else {
                list.addNull();
            }
            return list;
        }

        @Override
        public GTRecipeDefinition decode(Data data, int dataVersion) {
            var list = data.getList();
            var type = GTRegistries.RECIPE_TYPES.dataCodec().decode(list.getFirst(), dataVersion);
            var idData = list.get(1);
            if (idData.isNull()) return type.defaultDefinition;
            var id = DataCodecs.RESOURCE_LOCATION_CODEC.decode(idData, dataVersion);
            var definition = type.recipes.get(id);
            return definition == null ? type.defaultDefinition : definition;
        }
    };

    IntMapContainer container;

    public final boolean registered;
    public final GTRecipeType recipeType;
    public final GTRecipeCategory recipeCategory;

    public final ResourceLocation id;

    public final List<Content<ItemIngredient>> itemInputs;
    public final List<Content<ItemIngredient>> itemOutputs;
    public final List<Content<FluidIngredient>> fluidInputs;
    public final List<Content<FluidIngredient>> fluidOutputs;
    public final RecipeCondition[] conditions;
    public final ContentExpander[] contentExpanders;
    public final ContentExpander[] tickContentExpanders;
    public final DataComponentMap data;
    public final ChanceBoostFunction chanceFunction;
    public final long eut;
    public final int tier;
    public final int duration;
    public final int priority;

    public GTRecipeDefinition(boolean registered, GTRecipeType recipeType, GTRecipeCategory recipeCategory, ResourceLocation id, List<Content<ItemIngredient>> itemInputs, List<Content<ItemIngredient>> itemOutputs, List<Content<FluidIngredient>> fluidInputs, List<Content<FluidIngredient>> fluidOutputs, List<RecipeCondition> conditions, List<ContentExpander> contentExpanders, List<ContentExpander> tickContentExpanders, DataComponentMap data, ChanceBoostFunction chanceFunction, long eut, int tier, int duration, int priority) {
        this.registered = registered;
        this.recipeType = recipeType;
        this.recipeCategory = recipeCategory;
        this.id = id;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.conditions = conditions.toArray(new RecipeCondition[0]);
        this.contentExpanders = contentExpanders.toArray(new ContentExpander[0]);
        this.tickContentExpanders = tickContentExpanders.toArray(new ContentExpander[0]);
        this.data = data;
        this.chanceFunction = chanceFunction;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
        this.priority = priority;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var eu = eut;
        if (eu > 0) return eu;
        return 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var eu = eut;
        if (eu < 0) return -eu;
        return 0;
    }

    public GTRecipe toRuntime() {
        return new GTRecipe(this, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data.clone(), eut, tier, duration);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
