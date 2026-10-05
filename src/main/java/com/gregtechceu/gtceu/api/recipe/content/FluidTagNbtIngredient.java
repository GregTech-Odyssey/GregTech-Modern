package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.Nullable;

public final class FluidTagNbtIngredient implements KeyIngredient {

    public final TagKey<Fluid> tag;
    public final CompoundTag nbt;

    FluidTagNbtIngredient(TagKey<Fluid> tag, CompoundTag nbt) {
        this.tag = tag;
        this.nbt = nbt;
    }

    @Override
    public byte kind() {
        return PREDICATE;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyTypes.FLUIDS;
    }

    @Override
    public @Nullable AEKey key() {
        return null;
    }

    @Override
    public TagKey<Fluid> tagKey() {
        return tag;
    }

    @Override
    public boolean test(AEKey k) {
        return k instanceof AEFluidKey fk && fk.fluid.is(tag) && nbt.equals(fk.getTag());
    }

    @Override
    public @Nullable AEKey displayKey() {
        var set = BuiltInRegistries.FLUID.getTag(tag);
        if (set.isEmpty()) return null;
        var named = set.get();
        return named.size() == 0 ? null : AEFluidKey.of(named.get(0).value(), nbt);
    }

    @Override
    public FluidStack[] getFluids(int amount) {
        var set = BuiltInRegistries.FLUID.getTag(tag);
        if (set.isEmpty()) return new FluidStack[0];
        var named = set.get();
        var stacks = new FluidStack[named.size()];
        int i = 0;
        for (var holder : named) stacks[i++] = new FluidStack(holder.value(), amount, nbt);
        return stacks;
    }

    @Override
    public Component getName() {
        return Component.literal("FluidTag[" + tag.location() + "]");
    }

    @Override
    public Data toData() {
        var list = new ListData(5);
        list.addByte(PREDICATE);
        list.addBoolean(false);
        list.addBoolean(false);
        list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(tag.location()));
        list.add(DataCodecs.COMPOUND_TAG_CODEC.encode(nbt));
        return list;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(PREDICATE);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeResourceLocation(tag.location());
        buf.writeNbt(nbt);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FluidTagNbtIngredient o && o.tag == tag && o.nbt.equals(nbt);
    }

    @Override
    public int hashCode() {
        return tag.hashCode() * 31 + nbt.hashCode();
    }

    @Override
    public String toString() {
        return "Predicate[" + tag.location() + "]";
    }
}
