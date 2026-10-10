package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.util.ValueCodecs;
import com.gto.datasynclib.datastream.codec.ValueOps;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class UnionIngredient implements KeyIngredient {

    public final Ingredient ingredient;

    UnionIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public boolean matches(Item item) {
        var holder = item.builtInRegistryHolder();
        for (var v : ingredient.values) {
            if (v instanceof Ingredient.ItemValue itemValue) {
                if (itemValue.item.getItem() == item) return true;
            } else if (v instanceof Ingredient.TagValue tagValue && holder.is(tagValue.tag)) {
                return true;
            }
        }
        return false;
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
    public Ingredient source() {
        return ingredient;
    }

    @Override
    public boolean test(AEKey k) {
        return k instanceof AEItemKey ik && matches(ik.item);
    }

    @Override
    public @Nullable AEKey displayKey() {
        for (var stack : ingredient.getItems()) {
            var item = stack.getItem();
            if (item != Items.AIR) return AEItemKey.of(item);
        }
        return null;
    }

    @Override
    public ItemStack[] getItems() {
        return ingredient.getItems();
    }

    @Override
    public Component getName() {
        return Component.literal("Tag[union]");
    }

    @Override
        public Object toData(ValueOps ops) {
        return ops.createList(
                ops.createByte(TAG),
                ops.createBoolean(true),
                ops.createBoolean(true),
                ValueCodecs.JSON.encode(ops, ingredient.toJson()));
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(TAG);
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        ingredient.toNetwork(buf);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UnionIngredient o && o.ingredient == ingredient;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(ingredient);
    }

    @Override
    public String toString() {
        return "Tag[union]";
    }
}
