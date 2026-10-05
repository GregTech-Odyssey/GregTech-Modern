package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import org.jetbrains.annotations.Nullable;

public final class ForgeFluidView implements IKeyHandler<AEFluidKey> {

    private final IFluidHandler handler;

    private ForgeFluidView(IFluidHandler handler) {
        this.handler = handler;
    }

    public static IKeyHandler<AEFluidKey> of(IFluidHandler handler) {
        if (handler instanceof ForgeFluidAdapter adapter) return adapter.getHandler();
        return new ForgeFluidView(handler);
    }

    public IFluidHandler getHandler() {
        return handler;
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyTypes.FLUIDS;
    }

    @Override
    public int size() {
        return handler.getTanks();
    }

    @Override
    public @Nullable AEFluidKey keyAt(int slot) {
        return Keys.fluid(handler.getFluidInTank(slot));
    }

    @Override
    public long amountAt(int slot) {
        return handler.getFluidInTank(slot).getAmount();
    }

    @Override
    public long slotLimit(int slot) {
        return handler.getTankCapacity(slot);
    }

    @Override
    public long insert(int slot, AEFluidKey key, long amount, boolean simulate) {
        return insert(key, amount, simulate);
    }

    @Override
    public long extract(int slot, AEFluidKey key, long amount, boolean simulate) {
        return extract(key, amount, simulate);
    }

    @Override
    public long insert(AEFluidKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        return handler.fill(Keys.toFluidStack(key, amount), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public long extract(AEFluidKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        return handler.drain(Keys.toFluidStack(key, amount), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    @Override
    public long count(AEFluidKey key) {
        var h = handler;
        int size = h.getTanks();
        long total = 0;
        for (int i = 0; i < size; i++) {
            var stack = h.getFluidInTank(i);
            if (Keys.fluid(stack) == key) total = Keys.add(total, stack.getAmount());
        }
        return total;
    }
}
