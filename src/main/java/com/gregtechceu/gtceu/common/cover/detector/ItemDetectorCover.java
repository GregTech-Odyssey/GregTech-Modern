package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEItemKey;

public class ItemDetectorCover extends DetectorCover {

    public ItemDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && getItemHandler() != null;
    }

    @Override
    protected void update() {
        IKeyHandler<AEItemKey> handler = getItemHandler();
        if (handler == null)
            return;

        int size = handler.size();
        if (size == 0)
            return;

        long storedItems = 0;
        long itemCapacity = Keys.multiply(size, handler.slotLimit(0));

        if (itemCapacity == 0)
            return;

        for (int i = 0; i < size; i++) {
            storedItems = Keys.add(storedItems, handler.amountAt(i));
        }

        setRedstoneSignalOutput(RedstoneUtil.computeRedstoneValue(storedItems, itemCapacity, isInverted()));
    }

    protected IKeyHandler<AEItemKey> getItemHandler() {
        return GTCapabilityHelper.getItemKeyHandler(coverHolder.holder(), attachedSide);
    }
}
