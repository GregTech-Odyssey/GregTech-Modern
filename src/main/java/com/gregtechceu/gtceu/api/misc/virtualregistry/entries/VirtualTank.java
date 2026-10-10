package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.util.ValueCodecs;
import org.jetbrains.annotations.NotNull;

public class VirtualTank extends VirtualEntry {

    public static final ValueCodec<VirtualTank> DATA_CODEC = new ValueCodec<>() {

        @Override
        public VirtualTank decode(ValueOps ops, @NotNull Object data) {
            var tank = new VirtualTank();
            tank.deserializeNBT(ValueCodecs.COMPOUND_TAG.decode(ops, data));
            return tank;
        }

        @Override
        public @NotNull Object encode(ValueOps ops, VirtualTank obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, obj.serializeNBT());
        }
    };

    public static final int DEFAULT_CAPACITY = 640000; // 160B for per second transfer
    protected static final String CAPACITY_KEY = "capacity";
    protected static final String FLUID_KEY = "fluid";
    @NotNull
    private final KeyInventory<AEFluidKey> fluidTank;
    private int capacity;

    public VirtualTank(int capacity) {
        this.capacity = capacity;
        fluidTank = KeyInventory.fluids(1, this.capacity);
    }

    public VirtualTank() {
        this(DEFAULT_CAPACITY);
    }

    @Override
    public EntryTypes<VirtualTank> getType() {
        return EntryTypes.ENDER_FLUID;
    }

    public void setFluid(FluidStack fluid) {
        this.fluidTank.set(0, Keys.fluid(fluid), fluid.getAmount());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualTank other)) return false;
        return this.fluidTank == other.fluidTank;
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = super.serializeNBT();
        tag.putInt(CAPACITY_KEY, this.capacity);
        var key = this.fluidTank.keyAt(0);
        if (key != null) tag.put(FLUID_KEY, Keys.toFluidStack(key, this.fluidTank.amountAt(0)).writeToNBT(new CompoundTag()));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        this.capacity = nbt.getInt(CAPACITY_KEY);
        if (nbt.contains(FLUID_KEY)) setFluid(FluidStack.loadFluidStackFromNBT(nbt.getCompound(FLUID_KEY)));
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && this.fluidTank.isEmpty();
    }

    @NotNull
    public KeyInventory<AEFluidKey> getFluidTank() {
        return this.fluidTank;
    }
}
