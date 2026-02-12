package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Tuple;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GTRecipeSerializer {

    public static final Codec<GTRecipe> CODEC = makeCodec();

    public static final GTRecipeSerializer SERIALIZER = new GTRecipeSerializer();

    public @NotNull GTRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
        GTRecipe recipe = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, GTCEu.LOGGER::error);
        recipe.setId(id);
        return recipe;
    }

    public static Tuple<RecipeCapability<?>, List<Content>> entryReader(FriendlyByteBuf buf) {
        RecipeCapability<?> capability = GTRegistries.RECIPE_CAPABILITIES.get(buf.readUtf());
        List<Content> contents = buf.readList(capability.serializer::fromNetworkContent);
        return new Tuple<>(capability, contents);
    }

    public static void entryWriter(FriendlyByteBuf buf, Map.Entry<RecipeCapability<?>, ? extends List<Content>> entry) {
        RecipeCapability<?> capability = entry.getKey();
        List<Content> contents = entry.getValue();
        buf.writeUtf(GTRegistries.RECIPE_CAPABILITIES.getKey(capability));
        buf.writeCollection(contents, capability.serializer::toNetworkContent);
    }

    public static Map<RecipeCapability<?>, List<Content>> tuplesToMap(List<Tuple<RecipeCapability<?>, List<Content>>> entries) {
        Map<RecipeCapability<?>, List<Content>> map = new Reference2ReferenceOpenHashMap<>();
        entries.forEach(entry -> map.put(entry.getA(), entry.getB()));
        return map;
    }

    @NotNull
    public GTRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buf) {
        ResourceLocation recipeType = buf.readResourceLocation();
        int duration = buf.readVarInt();
        Map<RecipeCapability<?>, List<Content>> inputs = tuplesToMap(
                buf.readCollection(c -> new ArrayList<>(), GTRecipeSerializer::entryReader));
        Map<RecipeCapability<?>, List<Content>> tickInputs = tuplesToMap(
                buf.readCollection(c -> new ArrayList<>(), GTRecipeSerializer::entryReader));
        Map<RecipeCapability<?>, List<Content>> outputs = tuplesToMap(
                buf.readCollection(c -> new ArrayList<>(), GTRecipeSerializer::entryReader));
        Map<RecipeCapability<?>, List<Content>> tickOutputs = tuplesToMap(
                buf.readCollection(c -> new ArrayList<>(), GTRecipeSerializer::entryReader));

        CompoundTag data = buf.readNbt();
        if (data == null) {
            data = new CompoundTag();
        }
        ResourceLocation categoryLoc = buf.readResourceLocation();

        GTRecipeType type = (GTRecipeType) BuiltInRegistries.RECIPE_TYPE.get(recipeType);
        GTRecipeCategory category = GTRegistries.RECIPE_CATEGORIES.get(categoryLoc);

        return new GTRecipe(type, id,
                inputs, outputs, tickInputs, tickOutputs, data, duration, category);
    }

    public void toNetwork(FriendlyByteBuf buf, GTRecipe recipe) {
        buf.writeResourceLocation(recipe.recipeType.registryName);
        buf.writeVarInt(recipe.duration);
        buf.writeCollection(recipe.inputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.tickInputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.outputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.tickOutputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeNbt(recipe.data);
        buf.writeResourceLocation(recipe.recipeCategory.registryKey);
    }

    private static Codec<GTRecipe> makeCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                GTRegistries.RECIPE_TYPES.codec().fieldOf("type").forGetter(val -> val.recipeType),
                RecipeCapabilityMap.CODEC.optionalFieldOf("inputs", Map.of()).forGetter(val -> val.inputs),
                RecipeCapabilityMap.CODEC.optionalFieldOf("outputs", Map.of()).forGetter(val -> val.outputs),
                RecipeCapability.CODEC.optionalFieldOf("tickInputs", Map.of()).forGetter(val -> val.tickInputs),
                RecipeCapability.CODEC.optionalFieldOf("tickOutputs", Map.of()).forGetter(val -> val.tickOutputs),
                CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(val -> val.data),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("duration").forGetter(val -> val.duration),
                GTRegistries.RECIPE_CATEGORIES.codec().optionalFieldOf("category", GTRecipeCategory.DEFAULT).forGetter(val -> val.recipeCategory))
                .apply(instance, GTRecipe::new));
    }
}
