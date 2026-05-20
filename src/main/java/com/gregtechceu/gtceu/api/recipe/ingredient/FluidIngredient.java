package com.gregtechceu.gtceu.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.gto.datasynclib.datasream.data.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class FluidIngredient extends ContentInner implements Predicate<FluidStack> {

    public static Codec<FluidIngredient> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> FluidIngredient.fromData(dynamic.convert(DataOps.INSTANCE).getValue()), ingredient -> new Dynamic<>(DataOps.INSTANCE, ingredient.toData()));
    public static FluidIngredient EMPTY = new FluidIngredient(null, 0, null);

    public static final FluidStack[] EMPTY_STACKS = new FluidStack[0];

    public final Object value;
    public final CompoundTag nbt;

    private FluidStack[] stacks;

    private FluidIngredient(Object value, long amount, @Nullable CompoundTag nbt) {
        super(amount);
        this.value = value;
        this.nbt = nbt;
    }

    private FluidIngredient(FluidIngredient ingredient, long amount) {
        super(amount);
        this.value = ingredient.value;
        this.nbt = ingredient.nbt;
        this.hashCode = ingredient.hashCode;
    }

    public int getAmount() {
        if (amount > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return (int) amount;
        }
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        switch (value) {
            case null -> {
                buffer.writeByte(0);
                return;
            }
            case Fluid fluid -> {
                buffer.writeByte(1);
                buffer.writeInt(BuiltInRegistries.FLUID.getId(fluid));
            }
            case TagKey<?> tagKey -> {
                buffer.writeByte(2);
                buffer.writeResourceLocation(tagKey.location());
            }
            default -> throw new IllegalStateException("Unknown fluid ingredient type");
        }
        buffer.writeVarLong(amount);
        buffer.writeNbt(nbt);
    }

    public static FluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        return switch (buffer.readByte()) {
            case 0 -> EMPTY;
            case 1 -> new FluidIngredient(BuiltInRegistries.FLUID.byId(buffer.readInt()), buffer.readVarLong(), buffer.readNbt());
            case 2 -> new FluidIngredient(TagKey.create(Registries.FLUID, buffer.readResourceLocation()), buffer.readVarLong(), buffer.readNbt());
            default -> throw new IllegalStateException("Unknown fluid ingredient type");
        };
    }

    public Data toData() {
        var data = new MapData();
        switch (value) {
            case null -> {
                return NullData.INSTANCE;
            }
            case Fluid fluid -> data.putString("f", GTUtil.FLUID_ID.apply(fluid).toString());
            case TagKey<?> tagKey -> data.putString("t", tagKey.location().toString());
            default -> throw new IllegalStateException("Unknown fluid ingredient type");
        }
        data.putLong("a", amount);
        if (nbt != null) data.put("n", NbtOps.INSTANCE.convertTo(DataOps.INSTANCE, nbt));
        return data;
    }

    public static FluidIngredient fromData(Data data) {
        if (data.isNull()) return EMPTY;
        var map = data.getMap();
        var amount = map.get("a").getLong();
        var nbt = map.get("n") instanceof MapData mapData ? (CompoundTag) DataOps.INSTANCE.convertTo(NbtOps.INSTANCE, mapData) : null;
        var fluid = map.get("f");
        if (fluid != null) {
            return new FluidIngredient(GTUtil.FLUID_VALUE.apply(GTUtil.getResourceLocation(fluid.getString())), amount, nbt);
        } else if (map.get("t") instanceof StringData(String s)) {
            return new FluidIngredient(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(s)), amount, nbt);
        } else {
            throw new IllegalStateException("Unknown fluid ingredient type");
        }
    }

    public JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        switch (value) {
            case null -> {
                jsonObject.addProperty("empty", true);
                return jsonObject;
            }
            case Fluid fluid -> jsonObject.addProperty("fluid", GTUtil.FLUID_ID.apply(fluid).toString());
            case TagKey<?> tagKey -> jsonObject.addProperty("tag", tagKey.location().toString());
            default -> throw new IllegalStateException("Unknown fluid ingredient type");
        }
        jsonObject.addProperty("amount", amount);
        if (nbt != null) jsonObject.addProperty("nbt", nbt.getAsString());
        return jsonObject;
    }

    public static FluidIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) throw new JsonSyntaxException("Fluid ingredient cannot be null");
        var jsonObject = json.getAsJsonObject();
        if (jsonObject.has("empty")) return EMPTY;
        var amount = jsonObject.get("amount").getAsLong();
        var nbtjson = jsonObject.get("nbt");
        var nbt = nbtjson != null ? CraftingHelper.getNBT(nbtjson) : null;
        var fluid = jsonObject.get("fluid");
        if (fluid != null) {
            return new FluidIngredient(GTUtil.FLUID_VALUE.apply(GTUtil.getResourceLocation(fluid.getAsString())), amount, nbt);
        }
        var tag = jsonObject.get("tag");
        if (tag != null) {
            return new FluidIngredient(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(tag.getAsString())), amount, nbt);
        }
        throw new JsonSyntaxException("Unknown fluid ingredient type");
    }

    public FluidIngredient copy(long amount) {
        return new FluidIngredient(this, amount);
    }

    public boolean testFluid(@NotNull Fluid fluid) {
        if (value == null) return fluid == Fluids.EMPTY;
        return ((Value) value).gtceu$testFluid(fluid);
    }

    public boolean testAeKay(@NotNull AEFluidKey key) {
        Fluid fluid = key.getFluid();
        if (value == null) return fluid == Fluids.EMPTY;
        if (!((Value) value).gtceu$testFluid(fluid)) return false;
        return nbt == null || nbt.equals(key.getTag());
    }

    @Override
    public boolean test(@Nullable FluidStack stack) {
        if (stack == null) return false;
        if (value == null) return stack.isEmpty();
        if (!((Value) value).gtceu$testFluid(stack.getFluid())) return false;
        return this.nbt == null || this.nbt.equals(stack.getTag());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof FluidIngredient other) {
            return this.value == other.value && Objects.equals(this.nbt, other.nbt);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) hashCode = Objects.hashCode(value) + 31 * Objects.hashCode(nbt);
        return hashCode;
    }

    public boolean isEmpty() {
        return value == null;
    }

    @Nullable
    public Fluid getFluid() {
        if (value instanceof Fluid fluid) return fluid;
        return null;
    }

    @NotNull
    public FluidStack getFluidStack(int amount) {
        var stacks = getStacks();
        if (stacks.length == 0) return FluidStack.EMPTY;
        var stack = stacks[0].copy();
        stack.setAmount(amount);
        return stack;
    }

    @NotNull
    public FluidStack getFluidStack() {
        var stacks = getStacks();
        if (stacks.length == 0) return FluidStack.EMPTY;
        return stacks[0];
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public FluidStack[] getStacks() {
        if (this.stacks == null) {
            switch (value) {
                case null -> this.stacks = EMPTY_STACKS;
                case Fluid fluid -> this.stacks = new FluidStack[] { new FluidStack(fluid, getAmount()) };
                case TagKey tagKey -> {
                    Optional<HolderSet.Named<Fluid>> optional = BuiltInRegistries.FLUID.getTag(tagKey);
                    if (optional.isPresent()) {
                        var fluids = optional.get();
                        var size = fluids.size();
                        var stacks = new FluidStack[size];
                        for (int i = 0; i < size; i++) {
                            stacks[i] = new FluidStack(fluids.get(i).value(), getAmount());
                        }
                        this.stacks = stacks;
                    } else {
                        this.stacks = EMPTY_STACKS;
                    }
                }
                default -> throw new IllegalStateException("Unknown fluid ingredient type");
            }
        }
        return this.stacks;
    }

    public static FluidIngredient of() {
        return EMPTY;
    }

    public static FluidIngredient of(Fluid fluid, long amount) {
        return fromValues(fluid, amount, null);
    }

    public static FluidIngredient of(Fluid fluid, long amount, CompoundTag nbt) {
        return fromValues(fluid, amount, nbt);
    }

    public static FluidIngredient of(FluidStack stack) {
        return fromValues(stack.getFluid(), stack.getAmount(), stack.getTag());
    }

    public static FluidIngredient of(TagKey<Fluid> tag, long amount) {
        return fromValues(tag, amount, null);
    }

    public static FluidIngredient of(TagKey<Fluid> tag, long amount, CompoundTag nbt) {
        return fromValues(tag, amount, nbt);
    }

    private static FluidIngredient fromValues(@Nullable Object value, long amount, @Nullable CompoundTag nbt) {
        if (value == null) return EMPTY;
        return new FluidIngredient(value, amount, nbt);
    }

    public interface Value {

        boolean gtceu$testFluid(Object o);
    }
}
