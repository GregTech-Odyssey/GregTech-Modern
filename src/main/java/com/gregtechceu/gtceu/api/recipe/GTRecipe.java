package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;
import com.gregtechceu.gtceu.api.codec.data.DataKeys;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.TickContent;
import com.gregtechceu.gtceu.api.recipe.content.TickContentMap;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipe {

    public final GTRecipeDefinition definition;

    public final Map<RecipeCapability<?>, List<Content>> inputs;
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    public final TickContentMap ticks;
    public int tier;
    public int duration;
    public long parallels = 1;
    public int ocLevel = 0;

    public int outputColor = -1;

    public long batchParallels = 1;

    public GTRecipe(Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, TickContentMap ticks, int duration, int tier) {
        this(GTRecipeDefinition.DUMMY, inputs, outputs, ticks, duration, tier);
    }

    public GTRecipe(GTRecipeDefinition definition, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, TickContentMap ticks, int duration, int tier) {
        this.definition = definition;
        this.inputs = inputs;
        this.outputs = outputs;
        this.ticks = ticks;
        this.duration = duration;
        this.tier = tier;
    }

    public GTRecipe copy() {
        return copy(ContentModifier.IDENTITY, false);
    }

    public GTRecipe copy(ContentModifier modifier) {
        return copy(modifier, true);
    }

    public GTRecipe copy(ContentModifier modifier, boolean modifyDuration) {
        var copied = new GTRecipe(definition, modifier.copyContents(inputs), modifier.copyContents(outputs), modifier.copyContents(ticks), duration, tier);
        if (modifyDuration) {
            copied.duration = modifier.apply(this.duration);
        }
        copied.ocLevel = ocLevel;
        copied.parallels = parallels;
        return copied;
    }

    public boolean handleTickRecipe(IRecipeCapabilityHolder holder, boolean simulated) {
        return ticks.handleRecipe(holder, this, simulated);
    }

    public List<Content> getInputContents(RecipeCapability<?> capability) {
        return inputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getOutputContents(RecipeCapability<?> capability) {
        return outputs.getOrDefault(capability, Collections.emptyList());
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var eut = ticks.getData(DataKeys.EUT);
        return eut > 0 ? eut : 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var eut = ticks.getData(DataKeys.EUT);
        return eut < 0 ? -eut : 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getCWUt() {
        return ticks.getData(DataKeys.CWUT);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GTRecipe recipe)) return false;
        return this.definition == recipe.definition;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(definition);
    }

    @Override
    public String toString() {
        return String.valueOf(definition);
    }

    public CompoundTag toNbt() {
        var tag = new CompoundTag();
        if (definition.registered) {
            tag.putString("d", definition.id.toString());
        }
        var input = RecipeCapabilityMap.toNbt(inputs);
        if (input != null) {
            tag.put("inputs", input);
        }
        var output = RecipeCapabilityMap.toNbt(outputs);
        if (output != null) {
            tag.put("outputs", output);
        }
        if (duration > 1) {
            tag.putInt("duration", duration);
        }
        if (tier > 0) {
            tag.putInt("tier", tier);
        }
        if (ocLevel != 0) {
            tag.putInt("ocLevel", ocLevel);
        }
        if (outputColor != -1) {
            tag.putInt("outputColor", outputColor);
        }
        if (parallels != 1) {
            tag.putLong("parallels", parallels);
        }
        ticks.fastForEach((k, v) -> {
            if (v != 0) tag.putLong(k.name, v);
        });
        return tag;
    }

    @Nullable
    public static GTRecipe fromNbt(@Nullable Tag t) {
        if (t instanceof CompoundTag tag) {
            var definition = GTRecipeDefinition.get(tag.get("d"));
            if (definition == null) definition = GTRecipeDefinition.DUMMY;
            var inputs = RecipeCapabilityMap.fromNbt(tag.getCompound("inputs"));
            var outputs = RecipeCapabilityMap.fromNbt(tag.getCompound("outputs"));
            var duration = tag.getInt("duration");
            var tier = tag.getInt("tier");
            var ocLevel = tag.getInt("ocLevel");
            var outputColor = tag.getInt("outputColor");
            var parallels = tag.getLong("parallels");
            var ticks = new TickContentMap();
            TickContent.ALL.forEach(k -> {
                var v = tag.getLong(k.name);
                if (v != 0) ticks.put(k, v);
            });
            var recipe = new GTRecipe(definition, inputs, outputs, ticks, duration, tier);
            if (ocLevel > 0) recipe.ocLevel = ocLevel;
            if (outputColor != -1) recipe.outputColor = outputColor;
            if (parallels > 1) recipe.parallels = parallels;
        }
        return null;
    }

    public static GTRecipe fromNetwork(FriendlyByteBuf buf) {
        var definition = buf.readBoolean() ? GTRecipeDefinition.get(buf.readResourceLocation()) : null;
        if (definition == null) definition = GTRecipeDefinition.DUMMY;
        var tier = buf.readVarInt();
        var duration = buf.readInt();
        var inputs = tuplesToMap(buf.readCollection(c -> new ArrayList<>(), GTRecipe::entryReader));
        var outputs = tuplesToMap(buf.readCollection(c -> new ArrayList<>(), GTRecipe::entryReader));
        var ticks = new TickContentMap();
        var size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            var name = buf.readUtf();
            var value = buf.readLong();
            ticks.put(TickContent.get(name), value);
        }
        return new GTRecipe(definition, inputs, outputs, ticks, duration, tier);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        if (definition.registered) {
            buf.writeBoolean(true);
            buf.writeResourceLocation(definition.id);
        } else {
            buf.writeBoolean(false);
        }
        buf.writeVarInt(tier);
        buf.writeInt(duration);
        buf.writeCollection(inputs.entrySet(), GTRecipe::entryWriter);
        buf.writeCollection(outputs.entrySet(), GTRecipe::entryWriter);
        buf.writeVarInt(ticks.size());
        ticks.fastForEach((k, v) -> {
            buf.writeUtf(k.name);
            buf.writeLong(v);
        });
    }

    public static RecipeCapabilityMap<List<Content>> tuplesToMap(List<Tuple<RecipeCapability<?>, List<Content>>> entries) {
        RecipeCapabilityMap<List<Content>> map = new RecipeCapabilityMap<>();
        entries.forEach(entry -> map.put(entry.getA(), entry.getB()));
        return map;
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
}
