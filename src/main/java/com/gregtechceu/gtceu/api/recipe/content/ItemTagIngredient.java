package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.util.ValueCodecs;
import com.gto.fastcollection.cache.IdentityHashCache;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ItemTagIngredient implements KeyIngredient {

    private static final IdentityHashCache<TagKey<Item>, ItemTagIngredient> CACHE = new IdentityHashCache<>(ItemTagIngredient::new);

    public final TagKey<Item> tag;

    private ItemTagIngredient(TagKey<Item> tag) {
        this.tag = tag;
    }

    static ItemTagIngredient of(TagKey<Item> tag) {
        return CACHE.getCache(tag);
    }

    @Override
    public byte kind() {
        return TAG;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyTypes.ITEMS;
    }

    @Override
    public @Nullable AEKey key() {
        return null;
    }

    @Override
    public TagKey<Item> tagKey() {
        return tag;
    }

    @Override
    public boolean test(AEKey k) {
        return k instanceof AEItemKey ik && ik.item.builtInRegistryHolder().is(tag);
    }

    @Override
    public @Nullable AEKey displayKey() {
        var set = BuiltInRegistries.ITEM.getTag(tag);
        if (set.isEmpty()) return null;
        var named = set.get();
        return named.size() == 0 ? null : AEItemKey.of(named.get(0).value());
    }

    @Override
    public ItemStack[] getItems() {
        var set = BuiltInRegistries.ITEM.getTag(tag);
        if (set.isEmpty()) return new ItemStack[0];
        var named = set.get();
        var stacks = new ItemStack[named.size()];
        int i = 0;
        for (var holder : named) stacks[i++] = new ItemStack(holder.value());
        return stacks;
    }

    @Override
    public Component getName() {
        return Component.literal("Tag[" + tag.location() + "]");
    }

    @Override
        public Object toData(ValueOps ops) {
        return ops.createList(
                ops.createByte(TAG),
                ops.createBoolean(true),
                ops.createBoolean(false),
                ValueCodecs.RESOURCE_LOCATION.encode(ops, tag.location()));
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(TAG);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        buf.writeResourceLocation(tag.location());
    }

    @Override
    public String toString() {
        return "Tag[" + tag.location() + "]";
    }
}
