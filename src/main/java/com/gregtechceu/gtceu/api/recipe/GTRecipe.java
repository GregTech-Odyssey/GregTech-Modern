package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.*;
import com.gregtechceu.gtceu.api.recipe.extension.RecipeExtension;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.DataComponentMap;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.codec.DataDecoder;
import com.gto.datasynclib.datasream.codec.DataEncoder;
import com.gto.datasynclib.datasream.data.*;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipe {

    public static final ByteStreamCodec<GTRecipe> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public GTRecipe decode(FriendlyByteBuf buf) {
            var recipe = new GTRecipe(GTRecipeDefinition.STREAM_CODEC.decode(buf), buf.readList(SerializerItemIngredient.INSTANCE::fromNetworkContent), buf.readList(SerializerItemIngredient.INSTANCE::fromNetworkContent), buf.readList(SerializerFluidIngredient.INSTANCE::fromNetworkContent), buf.readList(SerializerFluidIngredient.INSTANCE::fromNetworkContent), GTRecipeDataKeys.REGISTRY.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
            recipe.parallels = buf.readVarLong();
            recipe.batchParallels = buf.readVarLong();
            recipe.ocLevel = buf.readVarInt();
            return recipe;
        }

        @Override
        public void encode(FriendlyByteBuf buf, GTRecipe recipe) {
            GTRecipeDefinition.STREAM_CODEC.encode(buf, recipe.definition);
            buf.writeCollection(recipe.itemInputs, SerializerItemIngredient.INSTANCE::toNetworkContent);
            buf.writeCollection(recipe.itemOutputs, SerializerItemIngredient.INSTANCE::toNetworkContent);
            buf.writeCollection(recipe.fluidInputs, SerializerFluidIngredient.INSTANCE::toNetworkContent);
            buf.writeCollection(recipe.fluidOutputs, SerializerFluidIngredient.INSTANCE::toNetworkContent);
            GTRecipeDataKeys.REGISTRY.encode(buf, recipe.data);
            buf.writeVarLong(recipe.eut);
            buf.writeVarInt(recipe.tier);
            buf.writeVarInt(recipe.duration);
            buf.writeVarLong(recipe.parallels);
            buf.writeVarLong(recipe.batchParallels);
            buf.writeVarInt(recipe.ocLevel);
        }
    };

    public static final DataCodec<GTRecipe> DATA_CODEC = new DataCodec<>() {

        @Override
        public Data encode(GTRecipe recipe) {
            var list = new ListData(13);
            list.add(GTRecipeDefinition.DATA_CODEC, recipe.definition);
            list.add(DataEncoder.collection(SerializerItemIngredient.INSTANCE::toDataContent), recipe.itemInputs);
            list.add(DataEncoder.collection(SerializerItemIngredient.INSTANCE::toDataContent), recipe.itemOutputs);
            list.add(DataEncoder.collection(SerializerFluidIngredient.INSTANCE::toDataContent), recipe.fluidInputs);
            list.add(DataEncoder.collection(SerializerFluidIngredient.INSTANCE::toDataContent), recipe.fluidOutputs);
            list.add(GTRecipeDataKeys.REGISTRY.encode(recipe.data));
            list.addLong(recipe.eut);
            list.addInt(recipe.tier);
            list.addInt(recipe.duration);
            list.addLong(recipe.parallels);
            list.addLong(recipe.batchParallels);
            list.addInt(recipe.ocLevel);
            list.addInt(recipe.outputColor);
            return list;
        }

        @Override
        public GTRecipe decode(Data data, int dataVersion) {
            if (dataVersion == -1 && data instanceof MapData mapData) {
                var compoundTag = DataCodecs.COMPOUND_TAG_CODEC.decode(mapData, dataVersion);
                var definition = GTRecipe.EMPTY.definition;
                var duration = compoundTag.getInt("duration");
                var tier = compoundTag.getInt("tier");
                var eu = compoundTag.getLong("eu");
                List<Content<ItemIngredient>> itemInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
                List<Content<ItemIngredient>> itemOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
                List<Content<FluidIngredient>> fluidInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
                List<Content<FluidIngredient>> fluidOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
                return new GTRecipe(definition, itemInput, itemOutput, fluidInput, fluidOutput, new DataComponentMap(), eu, tier, duration);
            }
            if (data instanceof ByteArrayData arrayData) {
                data = Data.readData(arrayData.getByteArray());
            }
            var list = data.getList();
            var definition = GTRecipeDefinition.DATA_CODEC.decode(list.getFirst(), dataVersion);
            var recipeData = GTRecipeDataKeys.REGISTRY.decode(list.get(5), dataVersion);
            if (definition == definition.recipeType.defaultDefinition && !recipeData.isEmpty()) {
                List<RecipeExtension> extensions = null;
                List<RecipeExtension> tickExtensions = null;
                for (var k : recipeData.keySet()) {
                    if (k instanceof RecipeExtension extension) {
                        if (extension.isTick) {
                            if (tickExtensions == null) tickExtensions = new ArrayList<>();
                            tickExtensions.add(extension);
                        } else {
                            if (extensions == null) extensions = new ArrayList<>();
                            extensions.add(extension);
                        }
                    }
                }
                if (extensions != null || tickExtensions != null) {
                    var b = definition.recipeType.recipeBuilder(definition.id);
                    if (extensions != null) extensions.forEach(b::addExtension);
                    if (tickExtensions != null) tickExtensions.forEach(b::addTickExtension);
                    definition = b.build();
                }
            }
            var recipe = new GTRecipe(definition, DataDecoder.notNullCollection(ArrayList::new, SerializerItemIngredient.INSTANCE::fromDataContent).decode(list.get(1), dataVersion), DataDecoder.notNullCollection(ArrayList::new, SerializerItemIngredient.INSTANCE::fromDataContent).decode(list.get(2), dataVersion), DataDecoder.notNullCollection(ArrayList::new, SerializerFluidIngredient.INSTANCE::fromDataContent).decode(list.get(3), dataVersion), DataDecoder.notNullCollection(ArrayList::new, SerializerFluidIngredient.INSTANCE::fromDataContent).decode(list.get(4), dataVersion), recipeData, list.get(6).getLong(), list.get(7).getInt(), list.get(8).getInt());
            recipe.parallels = list.get(9).getLong();
            recipe.batchParallels = list.get(10).getLong();
            recipe.ocLevel = list.get(11).getInt();
            recipe.outputColor = list.get(12).getInt();
            return recipe;
        }

        private static <T extends ContentInner> List<Content<T>> fromNbt(RecipeInfo capability, CompoundTag tag) {
            if (tag.tags.get(capability.name) instanceof ListTag listTag) {
                var list = new ArrayList<Content<T>>();
                for (var t : listTag) {
                    var content = fromNbtContent(capability, t);
                    if (content != null) {
                        list.add((Content<T>) content);
                    }
                }
                if (!list.isEmpty()) return list;
            }
            return Collections.emptyList();
        }

        @Nullable
        private static <T extends ContentInner> Content<T> fromNbtContent(RecipeInfo capability, @Nullable Tag tag) {
            if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
                var ingredient = capability == ItemRecipeInfo.INSTANCE ? ItemIngredient.fromNbt(content) : FluidIngredient.fromNbt(content);
                if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content(ingredient, getChance(compoundTag), getTierChanceBoost(compoundTag));
            }
            return null;
        }

        private static int getChance(CompoundTag tag) {
            if (tag.tags.get("chance") instanceof IntTag chance) {
                return chance.getAsInt();
            }
            return Content.MAX_CHANCE;
        }

        private static int getTierChanceBoost(CompoundTag tag) {
            if (tag.tags.get("tierChanceBoost") instanceof IntTag tierChanceBoost) {
                return tierChanceBoost.getAsInt();
            }
            return 0;
        }
    };

    public static final GTRecipe EMPTY = GTRecipeTypes.DUMMY_RECIPES.defaultDefinition.toRuntime();

    public final GTRecipeDefinition definition;

    public List<Content<ItemIngredient>> itemInputs;
    public List<Content<ItemIngredient>> itemOutputs;
    public List<Content<FluidIngredient>> fluidInputs;
    public List<Content<FluidIngredient>> fluidOutputs;
    public DataComponentMap data;
    public long eut;
    public int tier;
    public int duration;

    public long parallels = 1;
    public long contentParallel;
    public long batchParallels = 1;
    public int ocLevel = 0;
    public int outputColor = -1;
    public boolean perfect;

    public GTRecipe(GTRecipeDefinition definition, List<Content<ItemIngredient>> itemInputs, List<Content<ItemIngredient>> itemOutputs, List<Content<FluidIngredient>> fluidInputs, List<Content<FluidIngredient>> fluidOutputs, DataComponentMap data, long eut, int tier, int duration) {
        this.definition = definition;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.data = data;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
    }

    public GTRecipe copy() {
        return new GTRecipe(definition, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data, eut, tier, duration);
    }

    public void durationMultiplier(double multiplier) {
        this.duration = Math.max(1, (int) (duration * multiplier));
    }

    public void euMultiplier(double multiplier) {
        this.eut = (long) (eut * multiplier);
    }

    public void modifier(@Range(from = 1, to = ParallelLogic.MAX_PARALLEL) long multiplier, boolean tick) {
        if (multiplier == 1) return;
        parallels *= multiplier;
        itemInputs = RecipeHelper.modifierContents(itemInputs, multiplier);
        itemOutputs = RecipeHelper.modifierContents(itemOutputs, multiplier);
        fluidInputs = RecipeHelper.modifierContents(fluidInputs, multiplier);
        fluidOutputs = RecipeHelper.modifierContents(fluidOutputs, multiplier);
        for (var extension : definition.recipeExtensions) {
            extension.setParallel(this, multiplier);
        }
        if (tick) {
            eut *= multiplier;
            for (var extension : definition.tickRecipeExtensions) {
                extension.setParallel(this, multiplier);
            }
        }
    }

    public void setEUt(long eu) {
        eut = eu;
    }

    public void setCWUt(long cwu) {
        data.put(GTRecipeDataKeys.CWUT, cwu);
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

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputCWUt() {
        var cwu = data.getLong(GTRecipeDataKeys.CWUT);
        if (cwu > 0) return cwu;
        return 0;
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

    @Nullable
    public static GTRecipe fromNbt(@Nullable Tag t) {
        if (t instanceof ByteArrayTag tag) {
            return DATA_CODEC.decode(Data.readData(tag.getAsByteArray()));
        } else if (t instanceof CompoundTag compoundTag) {
            var definition = GTRecipe.EMPTY.definition;
            var data = new DataComponentMap();
            var duration = compoundTag.getInt("duration");
            var tier = compoundTag.getInt("tier");
            var eu = compoundTag.getLong("eu");
            List<Content<ItemIngredient>> itemInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
            List<Content<ItemIngredient>> itemOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
            List<Content<FluidIngredient>> fluidInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
            List<Content<FluidIngredient>> fluidOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
            return new GTRecipe(definition, itemInput, itemOutput, fluidInput, fluidOutput, data, eu, tier, duration);
        }
        return null;
    }

    private static <T extends ContentInner> List<Content<T>> fromNbt(RecipeInfo capability, CompoundTag tag) {
        if (tag.tags.get(capability.name) instanceof ListTag listTag) {
            var list = new ArrayList<Content<T>>();
            for (var t : listTag) {
                var content = fromNbtContent(capability, t);
                if (content != null) {
                    list.add((Content<T>) content);
                }
            }
            if (!list.isEmpty()) return list;
        }
        return Collections.emptyList();
    }

    @Nullable
    private static <T extends ContentInner> Content<T> fromNbtContent(RecipeInfo capability, @Nullable Tag tag) {
        if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
            var ingredient = capability == ItemRecipeInfo.INSTANCE ? ItemIngredient.fromNbt(content) : FluidIngredient.fromNbt(content);
            if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content(ingredient, getChance(compoundTag), getTierChanceBoost(compoundTag));
        }
        return null;
    }

    private static int getChance(CompoundTag tag) {
        if (tag.tags.get("chance") instanceof IntTag chance) {
            return chance.getAsInt();
        }
        return Content.MAX_CHANCE;
    }

    private static int getTierChanceBoost(CompoundTag tag) {
        if (tag.tags.get("tierChanceBoost") instanceof IntTag tierChanceBoost) {
            return tierChanceBoost.getAsInt();
        }
        return 0;
    }

    public static ByteArrayTag toNbt(GTRecipe recipe) {
        return new ByteArrayTag(DATA_CODEC.encode(recipe).writeToBytes());
    }
}
