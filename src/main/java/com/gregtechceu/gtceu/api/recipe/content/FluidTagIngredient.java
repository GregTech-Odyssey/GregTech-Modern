package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.core.registries.BuiltInRegistries;
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
import com.gto.fastcollection.cache.IdentityHashCache;
import org.jetbrains.annotations.Nullable;

public final class FluidTagIngredient implements KeyIngredient {

    private static final IdentityHashCache<TagKey<Fluid>, FluidTagIngredient> CACHE = new IdentityHashCache<>(FluidTagIngredient::new);

    public final TagKey<Fluid> tag;

    private FluidTagIngredient(TagKey<Fluid> tag) {
        this.tag = tag;
    }

    static FluidTagIngredient of(TagKey<Fluid> tag) {
        return CACHE.getCache(tag);
    }

    @Override
    public byte kind() {
        return TAG;
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
        return k instanceof AEFluidKey fk && fk.fluid.is(tag);
    }

    @Override
    public @Nullable AEKey displayKey() {
        var set = BuiltInRegistries.FLUID.getTag(tag);
        if (set.isEmpty()) return null;
        var named = set.get();
        return named.size() == 0 ? null : AEFluidKey.of(named.get(0).value());
    }

    @Override
    public FluidStack[] getFluids(int amount) {
        var set = BuiltInRegistries.FLUID.getTag(tag);
        if (set.isEmpty()) return new FluidStack[0];
        var named = set.get();
        var stacks = new FluidStack[named.size()];
        int i = 0;
        for (var holder : named) stacks[i++] = new FluidStack(holder.value(), amount);
        return stacks;
    }

    @Override
    public Component getName() {
        return Component.literal("FluidTag[" + tag.location() + "]");
    }

    @Override
    public Data toData() {
        var list = new ListData(4);
        list.addByte(TAG);
        list.addBoolean(false);
        list.addBoolean(false);
        list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(tag.location()));
        return list;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(TAG);
        buf.writeBoolean(false);
        buf.writeBoolean(true);
        buf.writeResourceLocation(tag.location());
    }

    @Override
    public String toString() {
        return "Tag[" + tag.location() + "]";
    }
}
