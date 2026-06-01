package com.gregtechceu.gtceu.api.transfer.fluid;

import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import com.gto.datasynclib.IDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.util.DataCodecs;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class CustomFluidTank extends FluidTank implements ICustomFluidStackHandler, IDataSerializable {

    @NotNull
    @Getter
    @Setter
    protected Runnable onContentsChanged = GTUtil.NOOP;
    protected boolean syncChange = true;

    public CustomFluidTank(int capacity) {
        super(capacity, GTUtil.FAVORABLE);
    }

    public CustomFluidTank(int capacity, Predicate<FluidStack> validator) {
        super(capacity, validator);
    }

    public CustomFluidTank(FluidStack stack) {
        super(stack.getAmount());
        setFluid(stack);
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        setFluid(stack);
    }

    @Override
    public void setFluid(FluidStack stack) {
        super.setFluid(stack);
        this.onContentsChanged();
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt) {
        if (fluid.isEmpty()) return nbt;
        fluid.writeToNBT(nbt);
        return nbt;
    }

    @Override
    public void onContentsChanged() {
        onContentsChanged.run();
        syncChange = true;
    }

    @Override
    public void markAsChanged() {
        syncChange = true;
    }

    @Override
    public void clearChanged() {
        syncChange = false;
    }

    @Override
    public boolean isChanged() {
        return syncChange;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        if (fluid.isEmpty()) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            fluid.writeToPacket(data);
        }
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        if (data.readBoolean()) {
            setFluid(FluidStack.readFromPacket(data));
        } else {
            setFluid(FluidStack.EMPTY);
        }
    }

    public CompoundTag serializeNBT() {
        return writeToNBT(new CompoundTag());
    }

    public void deserializeNBT(CompoundTag nbt) {
        readFromNBT(nbt);
    }

    @Override
    public Data writeData() {
        return DataCodecs.COMPOUND_TAG_CODEC.encode(this.writeToNBT(new CompoundTag()));
    }

    @Override
    public void readData(@NotNull Data data, int dataVersion) {
        this.readFromNBT(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
    }

    @Override
    public boolean detectChange() {
        return syncChange;
    }
}
