package com.gregtechceu.gtceu.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class FluidIngredient extends ContentInner implements Predicate<FluidStack> {

    public static Codec<FluidIngredient> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> FluidIngredient.fromJson(dynamic.convert(JsonOps.INSTANCE).getValue()), ingredient -> new Dynamic<>(JsonOps.INSTANCE, ingredient.toJson()));
    public static FluidIngredient EMPTY = new FluidIngredient(null, 0, null);

    public static final FluidStack[] EMPTY_STACKS = new FluidStack[0];

    public final Object value;
    public final CompoundTag nbt;

    private FluidStack[] stacks;
    private boolean changed = true;

    private FluidIngredient(Object value, long amount, @Nullable CompoundTag nbt) {
        this.value = value;
        this.amount = amount;
        this.nbt = nbt;
    }

    private FluidIngredient(FluidIngredient ingredient, long amount) {
        this.amount = amount;
        this.value = ingredient.value;
        this.nbt = ingredient.nbt;
        this.stacks = ingredient.stacks;
        this.hashCode = ingredient.hashCode;
    }

    private FluidIngredient(FluidIngredient ingredient) {
        this.amount = ingredient.amount;
        this.value = ingredient.value;
        this.nbt = ingredient.nbt;
        this.stacks = ingredient.stacks;
        this.changed = ingredient.changed;
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

    @Override
    public CompoundTag toNbt() {
        var tag = new CompoundTag();
        switch (value) {
            case null -> {
                tag.putBoolean("empty", true);
                return tag;
            }
            case Fluid fluid -> tag.putString("fluid", GTUtil.FLUID_ID.apply(fluid).toString());
            case TagKey<?> tagKey -> tag.putString("tag", tagKey.location().toString());
            default -> throw new IllegalStateException("Unknown fluid ingredient type");
        }
        tag.putLong("amount", amount);
        if (nbt != null) tag.put("nbt", nbt);
        return tag;
    }

    public static FluidIngredient fromNbt(CompoundTag tag) {
        if (tag.getBoolean("empty")) return EMPTY;
        var amount = tag.getLong("amount");
        var nbt = tag.tags.get("nbt") instanceof CompoundTag nbtTag ? nbtTag : null;
        if (tag.tags.get("fluid") instanceof StringTag stringTag) {
            return new FluidIngredient(GTUtil.FLUID_VALUE.apply(GTUtil.getResourceLocation(stringTag.getAsString())), amount, nbt);
        } else if (tag.tags.get("tag") instanceof StringTag stringTag) {
            return new FluidIngredient(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(stringTag.getAsString())), amount, nbt);
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

    public FluidIngredient copy() {
        return new FluidIngredient(this);
    }

    public FluidIngredient depthCopy() {
        return new FluidIngredient(value, amount, nbt);
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
    public FluidStack getFluidStack() {
        var stacks = getStacks();
        if (stacks.length == 0) return FluidStack.EMPTY;
        return stacks[0];
    }

    public FluidStack[] getLatestStacks() {
        if (changed) this.stacks = null;
        return getStacks();
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
            this.changed = false;
        }
        return this.stacks;
    }

    public void changeAmount(long amount) {
        this.amount = amount;
        this.changed = true;
    }

    public void shrink(long amount) {
        this.amount -= amount;
        this.changed = true;
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
