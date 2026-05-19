package com.gregtechceu.gtceu.api.recipe.ingredient;

import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.datasream.data.ByteData;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IntCircuitIngredient extends ItemIngredient {

    public static final Item PROGRAMMED_CIRCUIT = GTItems.PROGRAMMED_CIRCUIT.get();

    public static final String Configuration = "Configuration";

    public static final IntCircuitIngredient[] CIRCUIT_INPUTS = new IntCircuitIngredient[33];

    static {
        for (int i = 0; i < 33; i++) {
            CIRCUIT_INPUTS[i] = new IntCircuitIngredient(i);
        }
    }

    public static IntCircuitIngredient of(int configuration) {
        return CIRCUIT_INPUTS[configuration];
    }

    public final int configuration;

    private IntCircuitIngredient(int configuration) {
        super(StrictNBTIngredient.of(IntCircuitBehaviour.stack(configuration)), 1);
        this.configuration = configuration;
    }

    public static int getConfiguration(@Nullable CompoundTag tag) {
        if (tag != null && tag.tags.get(IntCircuitIngredient.Configuration) instanceof IntTag intTag) {
            return intTag.getAsInt();
        }
        return -1;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeByte(-configuration);
    }

    @Override
    public Data toData() {
        return ByteData.valueOf((byte) configuration);
    }

    @Override
    public ItemIngredient copy(long amount) {
        return this;
    }

    @Override
    public boolean testItem(Item item) {
        return item == PROGRAMMED_CIRCUIT;
    }

    @Override
    public boolean testAeKay(@NotNull AEItemKey key) {
        var item = key.getItem();
        if (item != PROGRAMMED_CIRCUIT) return false;
        return getConfiguration(key.getTag()) == configuration;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null) return false;
        var item = stack.getItem();
        if (item != PROGRAMMED_CIRCUIT) return false;
        return getConfiguration(stack.getTag()) == configuration;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return configuration;
    }
}
