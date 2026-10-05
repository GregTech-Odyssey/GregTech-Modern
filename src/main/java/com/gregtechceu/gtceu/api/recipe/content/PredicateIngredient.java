package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import org.jetbrains.annotations.Nullable;

public final class PredicateIngredient implements KeyIngredient {

    public final Ingredient ingredient;

    PredicateIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    @Override
    public byte kind() {
        return PREDICATE;
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
        return k instanceof AEItemKey ik && ingredient.test(ik.getReadOnlyStack());
    }

    @Override
    public @Nullable AEKey displayKey() {
        var items = ingredient.getItems();
        return items.length == 0 ? null : AEItemKey.of(items[0]);
    }

    @Override
    public ItemStack[] getItems() {
        return ingredient.getItems();
    }

    @Override
    public Component getName() {
        return Component.literal(ingredient.toString());
    }

    @Override
    public Data toData() {
        var list = new ListData(4);
        list.addByte(PREDICATE);
        list.addBoolean(true);
        list.addBoolean(true);
        list.addString(ingredient.toJson().toString());
        return list;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(PREDICATE);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        ingredient.toNetwork(buf);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PredicateIngredient o && o.ingredient == ingredient;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(ingredient);
    }

    @Override
    public String toString() {
        return "Predicate[" + ingredient + "]";
    }
}
