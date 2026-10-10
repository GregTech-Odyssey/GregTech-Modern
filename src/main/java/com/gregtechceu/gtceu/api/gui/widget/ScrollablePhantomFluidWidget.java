package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ScrollablePhantomFluidWidget extends PhantomFluidWidget {

    private static final int MAX_SCROLL_DELTA = 1_000_000;

    private final RPC<Integer> scrollRequest;

    public ScrollablePhantomFluidWidget(@Nullable IFluidHandler fluidTank, int tank, int x, int y, int width,
                                        int height, Supplier<FluidStack> phantomFluidGetter,
                                        Consumer<FluidStack> phantomFluidSetter) {
        super(fluidTank, tank, x, y, width, height, phantomFluidGetter, phantomFluidSetter);
        this.scrollRequest = addRPC(ByteBufCodecs.INT, (player, delta) -> handleScrollAction(delta))
                .validate(delta -> delta >= -MAX_SCROLL_DELTA && delta <= MAX_SCROLL_DELTA);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isMouseOverElement(mouseX, mouseY))
            return false;

        var delta = getModifiedChangeAmount((wheelDelta > 0) ? 1 : -1);
        scrollRequest.send(delta);

        return true;
    }

    private int getModifiedChangeAmount(int amount) {
        if (GTUtil.isShiftDown())
            amount *= 10;

        if (GTUtil.isCtrlDown())
            amount *= 100;

        if (!GTUtil.isAltDown())
            amount *= 1000;

        return amount;
    }

    private void handleScrollAction(int delta) {
        if (!(getFluidTank() instanceof ForgeFluidAdapter adapter) || !(adapter.getHandler() instanceof KeyInventory<?> inventory) || tank >= inventory.size())
            return;

        var key = inventory.keyAt(tank);
        if (key == null) return;

        long amount = Math.min(Math.max(inventory.amountAt(tank) + delta, 0L), inventory.slotLimit(tank));
        inventory.set(tank, key, amount);
        detectAndSendChanges();
    }
}
