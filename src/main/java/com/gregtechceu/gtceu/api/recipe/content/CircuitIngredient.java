package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;

public final class CircuitIngredient implements KeyIngredient {

    private static final CircuitIngredient[] ALL = new CircuitIngredient[Circuits.MAX + 1];

    static {
        for (int i = 0; i <= Circuits.MAX; i++) ALL[i] = new CircuitIngredient(i);
    }

    public final int config;

    private CircuitIngredient(int config) {
        this.config = config;
    }

    static CircuitIngredient of(int configuration) {
        return ALL[configuration];
    }

    @Override
    public byte kind() {
        return CIRCUIT;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyTypes.ITEMS;
    }

    @Override
    public AEItemKey key() {
        return Circuits.key(config);
    }

    @Override
    public int uid() {
        return Circuits.uid();
    }

    @Override
    public int circuitConfiguration() {
        return config;
    }

    @Override
    public boolean test(AEKey k) {
        return k instanceof AEItemKey ik && ik.uid == Circuits.uid() && Circuits.configOf(ik.getTag()) == config;
    }

    @Override
    public Component getName() {
        return Component.translatable("item.gtceu.programmed_circuit").append("[" + config + "]");
    }

    @Override
    public Data toData() {
        var list = new ListData(3);
        list.addByte(CIRCUIT);
        list.addBoolean(true);
        list.addInt(config);
        return list;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(CIRCUIT);
        buf.writeBoolean(true);
        buf.writeVarInt(config);
    }

    @Override
    public String toString() {
        return "Circuit[" + config + "]";
    }
}
