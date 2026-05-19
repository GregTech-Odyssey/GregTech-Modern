package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.Data;

public class SerializerFluidIngredient implements IContentSerializer<FluidIngredient> {

    public static SerializerFluidIngredient INSTANCE = new SerializerFluidIngredient();

    private SerializerFluidIngredient() {}

    @Override
    public void toNetwork(FriendlyByteBuf buf, FluidIngredient content) {
        content.toNetwork(buf);
    }

    @Override
    public FluidIngredient fromNetwork(FriendlyByteBuf buf) {
        return FluidIngredient.fromNetwork(buf);
    }

    @Override
    public Data toData(FluidIngredient content) {
        return content.toData();
    }

    @Override
    public FluidIngredient fromData(Data data) {
        return FluidIngredient.fromData(data);
    }
}
