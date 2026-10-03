package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEFluidKey;

public class FluidDetectorCover extends DetectorCover {

    public FluidDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && getFluidHandler() != null;
    }

    @Override
    protected void update() {
        IKeyHandler<AEFluidKey> fluidHandler = getFluidHandler();
        if (fluidHandler == null)
            return;

        long storedFluid = 0;
        long fluidCapacity = 0;

        int size = fluidHandler.size();
        for (int tank = 0; tank < size; tank++) {
            storedFluid = Keys.add(storedFluid, fluidHandler.amountAt(tank));
            fluidCapacity = Keys.add(fluidCapacity, fluidHandler.slotLimit(tank));
        }

        if (fluidCapacity == 0)
            return;

        setRedstoneSignalOutput(RedstoneUtil.computeRedstoneValue(storedFluid, fluidCapacity, isInverted()));
    }

    protected IKeyHandler<AEFluidKey> getFluidHandler() {
        return GTCapabilityHelper.getFluidKeyHandler(coverHolder.holder(), attachedSide);
    }
}
