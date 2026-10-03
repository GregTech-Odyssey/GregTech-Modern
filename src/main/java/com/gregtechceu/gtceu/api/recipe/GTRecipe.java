package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.extension.RecipeExtension;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.DataComponentMap;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.datastream.codec.DataCodec;
import com.gto.datasynclib.datastream.data.ByteArrayData;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.IntData;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.datastream.data.StringMapData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipe {

    private static final int MAGIC = 0x47545234;

    public static final ByteStreamCodec<GTRecipe> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public GTRecipe decode(FriendlyByteBuf buf) {
            var recipe = new GTRecipe(GTRecipeDefinition.STREAM_CODEC.decode(buf), ContentList.fromNetwork(buf), ContentList.fromNetwork(buf), ContentList.fromNetwork(buf), ContentList.fromNetwork(buf), GTRecipeDataKeys.REGISTRY.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
            recipe.parallels = buf.readVarLong();
            recipe.batchParallels = buf.readVarLong();
            recipe.ocLevel = buf.readVarInt();
            recipe.scale = buf.readVarLong();
            return recipe;
        }

        @Override
        public void encode(FriendlyByteBuf buf, GTRecipe recipe) {
            GTRecipeDefinition.STREAM_CODEC.encode(buf, recipe.definition);
            recipe.itemInputs.toNetwork(buf);
            recipe.itemOutputs.toNetwork(buf);
            recipe.fluidInputs.toNetwork(buf);
            recipe.fluidOutputs.toNetwork(buf);
            GTRecipeDataKeys.REGISTRY.encode(buf, recipe.data);
            buf.writeVarLong(recipe.eut);
            buf.writeVarInt(recipe.tier);
            buf.writeVarInt(recipe.duration);
            buf.writeVarLong(recipe.parallels);
            buf.writeVarLong(recipe.batchParallels);
            buf.writeVarInt(recipe.ocLevel);
            buf.writeVarLong(recipe.scale);
        }
    };

    public static final DataCodec<GTRecipe> DATA_CODEC = new DataCodec<>() {

        @Override
        public Data encode(GTRecipe recipe) {
            var list = new ListData(15);
            list.addInt(MAGIC);
            list.add(GTRecipeDefinition.DATA_CODEC, recipe.definition);
            list.add(recipe.itemInputs.toData());
            list.add(recipe.itemOutputs.toData());
            list.add(recipe.fluidInputs.toData());
            list.add(recipe.fluidOutputs.toData());
            list.add(GTRecipeDataKeys.REGISTRY.encode(recipe.data));
            list.addLong(recipe.eut);
            list.addInt(recipe.tier);
            list.addInt(recipe.duration);
            list.addLong(recipe.parallels);
            list.addLong(recipe.batchParallels);
            list.addInt(recipe.ocLevel);
            list.addInt(recipe.outputColor);
            list.addLong(recipe.scale);
            return list;
        }

        @Override
        public GTRecipe decode(Data data, int dataVersion) {
            if (dataVersion == -1 && data instanceof StringMapData mapData) {
                return fromLegacyNbt(DataCodecs.COMPOUND_TAG_CODEC.decode(mapData, dataVersion));
            }
            if (data instanceof ByteArrayData arrayData) {
                data = Data.readData(arrayData.getByteArray());
            }
            var list = data.getList();
            boolean current = list.getFirst() instanceof IntData(int magic) && magic == MAGIC;
            int o = current ? 1 : 0;
            var definition = GTRecipeDefinition.DATA_CODEC.decode(list.get(o), dataVersion);
            var recipeData = GTRecipeDataKeys.REGISTRY.decode(list.get(o + 5), dataVersion);
            definition = withExtensions(definition, recipeData);
            ContentList itemIn, itemOut, fluidIn, fluidOut;
            if (current) {
                itemIn = ContentList.fromData(list.get(o + 1), dataVersion);
                itemOut = ContentList.fromData(list.get(o + 2), dataVersion);
                fluidIn = ContentList.fromData(list.get(o + 3), dataVersion);
                fluidOut = ContentList.fromData(list.get(o + 4), dataVersion);
            } else {
                itemIn = LegacyRecipeCodec.items(list.get(1), dataVersion);
                itemOut = LegacyRecipeCodec.items(list.get(2), dataVersion);
                fluidIn = LegacyRecipeCodec.fluids(list.get(3), dataVersion);
                fluidOut = LegacyRecipeCodec.fluids(list.get(4), dataVersion);
            }
            var recipe = new GTRecipe(definition, itemIn, itemOut, fluidIn, fluidOut, recipeData, list.get(o + 6).getLong(), list.get(o + 7).getInt(), list.get(o + 8).getInt());
            recipe.parallels = list.get(o + 9).getLong();
            recipe.batchParallels = list.get(o + 10).getLong();
            recipe.ocLevel = list.get(o + 11).getInt();
            recipe.outputColor = list.get(o + 12).getInt();
            if (current) recipe.scale = list.get(o + 13).getLong();
            return recipe;
        }
    };

    private static GTRecipeDefinition withExtensions(GTRecipeDefinition definition, DataComponentMap recipeData) {
        if (definition != definition.recipeType.defaultDefinition || recipeData.isEmpty()) return definition;
        List<RecipeExtension<?>> extensions = null;
        List<RecipeExtension<?>> tickExtensions = null;
        for (var k : recipeData.keySet()) {
            if (k instanceof RecipeExtension<?> extension) {
                if (extension.isTick) {
                    if (tickExtensions == null) tickExtensions = new ArrayList<>();
                    tickExtensions.add(extension);
                } else {
                    if (extensions == null) extensions = new ArrayList<>();
                    extensions.add(extension);
                }
            }
        }
        if (extensions == null && tickExtensions == null) return definition;
        var b = definition.recipeType.recipeBuilder(definition.id);
        if (extensions != null) extensions.forEach(b::addExtension);
        if (tickExtensions != null) tickExtensions.forEach(b::addTickExtension);
        return b.build();
    }

    public static final GTRecipe EMPTY = GTRecipeTypes.DUMMY_RECIPES.defaultDefinition.toRuntime();

    public final GTRecipeDefinition definition;

    public ContentList itemInputs;
    public ContentList itemOutputs;
    public ContentList fluidInputs;
    public ContentList fluidOutputs;
    public DataComponentMap data;
    public long eut;
    public int tier;
    public int duration;

    public long scale = 1;
    public long parallels = 1;
    public long contentParallel;
    public long batchParallels = 1;
    public int ocLevel = 0;
    public int outputColor = -1;
    public boolean perfect;
    private boolean sharedData;

    public static GTRecipe runtime(GTRecipeDefinition definition, GTRecipeDefinition source) {
        var r = new GTRecipe(definition, source.itemInputs, source.itemOutputs, source.fluidInputs, source.fluidOutputs, source.data, source.eut, source.tier, source.duration);
        r.sharedData = true;
        return r;
    }

    public GTRecipe(GTRecipeDefinition definition, ContentList itemInputs, ContentList itemOutputs, ContentList fluidInputs, ContentList fluidOutputs, DataComponentMap data, long eut, int tier, int duration) {
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
        var r = new GTRecipe(definition, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data, eut, tier, duration);
        r.scale = scale;
        r.sharedData = sharedData;
        return r;
    }

    public DataComponentMap mutableData() {
        if (sharedData) {
            data = data.clone();
            sharedData = false;
        }
        return data;
    }

    public long inputAmount(ContentList list, int i) {
        return list.effective(i, scale);
    }

    public void durationMultiplier(double multiplier) {
        this.duration = Math.max(1, (int) (duration * multiplier));
    }

    public void euMultiplier(double multiplier) {
        var eu = this.eut;
        if (eu > 0) {
            this.eut = Math.max(1, (long) (eu * multiplier));
        } else if (eu < 0) {
            this.eut = (long) (eu * multiplier);
        }
    }

    public void modifier(@Range(from = 1, to = ParallelLogic.MAX_PARALLEL) long multiplier, boolean tick) {
        if (multiplier == 1) return;
        parallels = Keys.multiply(parallels, multiplier);
        scale = Keys.multiply(scale, multiplier);
        for (var extension : definition.recipeExtensions) {
            extension.setParallel(this, multiplier);
        }
        if (tick) {
            eut = Keys.multiply(eut, multiplier);
            for (var extension : definition.tickRecipeExtensions) {
                extension.setParallel(this, multiplier);
            }
        }
    }

    public long maxModifier() {
        long max = ParallelLogic.MAX_PARALLEL / Math.max(1, scale);
        long m = maxScalable();
        if (m > 1) max /= m;
        return Math.max(1, max);
    }

    private long maxScalable() {
        long m = Math.max(itemInputs.maxScalable(), itemOutputs.maxScalable());
        m = Math.max(m, fluidInputs.maxScalable());
        return Math.max(m, fluidOutputs.maxScalable());
    }

    public void bake() {
        if (scale == 1) return;
        itemInputs = itemInputs.scaled(scale);
        itemOutputs = itemOutputs.scaled(scale);
        fluidInputs = fluidInputs.scaled(scale);
        fluidOutputs = fluidOutputs.scaled(scale);
        scale = 1;
    }

    public void setEUt(long eu) {
        eut = eu;
    }

    public void setCWUt(long cwu) {
        mutableData().put(GTRecipeDataKeys.CWUT, cwu);
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
            return DATA_CODEC.decode(Data.readData(tag.getAsByteArray()), GTDataFixer.VERSION);
        } else if (t instanceof CompoundTag compoundTag) {
            return fromLegacyNbt(compoundTag);
        }
        return null;
    }

    @SuppressWarnings("removal")
    private static GTRecipe fromLegacyNbt(CompoundTag tag) {
        var definition = GTRecipe.EMPTY.definition;
        var inputs = tag.get("inputs") instanceof CompoundTag i ? i : new CompoundTag();
        var outputs = tag.get("outputs") instanceof CompoundTag o ? o : new CompoundTag();
        return new GTRecipe(definition, LegacyRecipeCodec.itemsFromNbt(inputs), LegacyRecipeCodec.itemsFromNbt(outputs), LegacyRecipeCodec.fluidsFromNbt(inputs), LegacyRecipeCodec.fluidsFromNbt(outputs), new DataComponentMap(), tag.getLong("eu"), tag.getInt("tier"), tag.getInt("duration"));
    }

    public static ByteArrayTag toNbt(GTRecipe recipe) {
        return new ByteArrayTag(DATA_CODEC.encode(recipe).writeToBytes());
    }
}
