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
import com.gto.datasynclib.datastream.codec.JavaValueOps;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ValueCodecs;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipe {

    private static final int MAGIC = 0x47545234;

    public static final StreamCodec<FriendlyByteBuf, GTRecipe> STREAM_CODEC = new StreamCodec<>() {

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

    public static final ValueCodec<GTRecipe> DATA_CODEC = new ValueCodec<>() {

        @Override
        public Object encode(ValueOps ops, GTRecipe recipe) {
            var list = new ArrayList<Object>(15);
            ops.addInt(list, MAGIC);
            list.add(GTRecipeDefinition.DATA_CODEC.encode(ops, recipe.definition));
            list.add(recipe.itemInputs.toData(ops));
            list.add(recipe.itemOutputs.toData(ops));
            list.add(recipe.fluidInputs.toData(ops));
            list.add(recipe.fluidOutputs.toData(ops));
            list.add(GTRecipeDataKeys.REGISTRY.encode(ops, recipe.data));
            ops.addLong(list, recipe.eut);
            ops.addInt(list, recipe.tier);
            ops.addInt(list, recipe.duration);
            ops.addLong(list, recipe.parallels);
            ops.addLong(list, recipe.batchParallels);
            ops.addInt(list, recipe.ocLevel);
            ops.addInt(list, recipe.outputColor);
            ops.addLong(list, recipe.scale);
            return ops.createList(list);
        }

        @Override
        public GTRecipe decode(ValueOps ops, Object data) {
            if (ops.isByteArray(data)) {
                data = ops.fromBytes(ops.getByteArray(data));
            }
            var list = ops.getList(data);
            boolean current = ops.isInt(list.getFirst()) && ops.getInt(list, 0) == MAGIC;
            int o = current ? 1 : 0;
            var definition = GTRecipeDefinition.DATA_CODEC.decode(ops, list.get(o));
            var recipeData = GTRecipeDataKeys.REGISTRY.decode(ops, list.get(o + 5));
            definition = withExtensions(definition, recipeData);
            ContentList itemIn, itemOut, fluidIn, fluidOut;
            if (current) {
                itemIn = ContentList.fromData(list.get(o + 1), ops);
                itemOut = ContentList.fromData(list.get(o + 2), ops);
                fluidIn = ContentList.fromData(list.get(o + 3), ops);
                fluidOut = ContentList.fromData(list.get(o + 4), ops);
            } else {
                itemIn = LegacyRecipeCodec.items(list.get(1), ops);
                itemOut = LegacyRecipeCodec.items(list.get(2), ops);
                fluidIn = LegacyRecipeCodec.fluids(list.get(3), ops);
                fluidOut = LegacyRecipeCodec.fluids(list.get(4), ops);
            }
            var recipe = new GTRecipe(definition, itemIn, itemOut, fluidIn, fluidOut, recipeData, ops.getLong(list, o + 6), ops.getInt(list, o + 7), ops.getInt(list, o + 8));
            recipe.parallels = ops.getLong(list, o + 9);
            recipe.batchParallels = ops.getLong(list, o + 10);
            recipe.ocLevel = ops.getInt(list, o + 11);
            recipe.outputColor = ops.getInt(list, o + 12);
            if (current) recipe.scale = ops.getLong(list, o + 13);
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
        long max = ParallelLogic.MAX_PARALLEL / scale;
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
            var ops = JavaValueOps.create(GTDataFixer.VERSION);
            return DATA_CODEC.decode(ops, ops.fromBytes(tag.getAsByteArray()));
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
        return new ByteArrayTag(JavaValueOps.INSTANCE.toBytes(DATA_CODEC.encode(JavaValueOps.INSTANCE, recipe)));
    }
}
