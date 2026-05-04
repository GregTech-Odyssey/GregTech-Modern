package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Tuple;

import com.google.gson.JsonObject;
import com.gto.datasynclib.datasream.DataComponentMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GTRecipeSerializer {

    public static final Codec<GTRecipe> CODEC = makeCodec();

    public static final GTRecipeSerializer SERIALIZER = new GTRecipeSerializer();

    public @NotNull GTRecipe fromJson(@NotNull JsonObject json) {
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, GTCEu.LOGGER::error);
    }

    public static Tuple<RecipeCapability<?>, List<Content>> entryReader(FriendlyByteBuf buf) {
        RecipeCapability<?> capability = RecipeCapability.STREAM_CODEC.decode(buf);
        List<Content> contents = buf.readList(capability.serializer::fromNetworkContent);
        return new Tuple<>(capability, contents);
    }

    public static void entryWriter(FriendlyByteBuf buf, Map.Entry<RecipeCapability<?>, ? extends List<Content>> entry) {
        RecipeCapability<?> capability = entry.getKey();
        List<Content> contents = entry.getValue();
        RecipeCapability.STREAM_CODEC.encode(capability, buf);
        buf.writeCollection(contents, capability.serializer::toNetworkContent);
    }

    public static Map<RecipeCapability<?>, List<Content>> tuplesToMap(List<Tuple<RecipeCapability<?>, List<Content>>> entries) {
        Map<RecipeCapability<?>, List<Content>> map = new Reference2ReferenceOpenHashMap<>();
        entries.forEach(entry -> map.put(entry.getA(), entry.getB()));
        return map;
    }

    @NotNull
    public GTRecipe fromNetwork(@NotNull FriendlyByteBuf buf) {
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

        var data = GTRecipeDataKeys.REGISTRY.decode(buf);
        if (data == null) {
            data = new DataComponentMap();
        }

        GTRecipeType type = (GTRecipeType) BuiltInRegistries.RECIPE_TYPE.get(recipeType);
        return new GTRecipe(type, inputs, outputs, tickInputs, tickOutputs, data, duration, buf.readVarInt());
    }

    public void toNetwork(FriendlyByteBuf buf, GTRecipe recipe) {
        buf.writeResourceLocation(recipe.recipeType.registryName);
        buf.writeVarInt(recipe.duration);
        buf.writeCollection(recipe.inputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.tickInputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.outputs.entrySet(), GTRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.tickOutputs.entrySet(), GTRecipeSerializer::entryWriter);
        GTRecipeDataKeys.REGISTRY.encode(buf, recipe.data);
        buf.writeVarInt(recipe.tier);
    }

    private static Codec<GTRecipe> makeCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                GTRegistries.RECIPE_TYPES.codec().fieldOf("type").forGetter(val -> val.recipeType),
                RecipeCapabilityMap.CODEC.optionalFieldOf("inputs", Collections.emptyMap()).forGetter(val -> val.inputs),
                RecipeCapabilityMap.CODEC.optionalFieldOf("outputs", Collections.emptyMap()).forGetter(val -> val.outputs),
                RecipeCapability.CODEC.optionalFieldOf("tickInputs", Collections.emptyMap()).forGetter(val -> val.tickInputs),
                RecipeCapability.CODEC.optionalFieldOf("tickOutputs", Collections.emptyMap()).forGetter(val -> val.tickOutputs),
                GTRecipeDataKeys.REGISTRY.optionalFieldOf("data", new DataComponentMap()).forGetter(val -> val.data),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("duration").forGetter(val -> val.duration),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tier").forGetter(val -> val.tier))
                .apply(instance, GTRecipe::new));
    }
}
