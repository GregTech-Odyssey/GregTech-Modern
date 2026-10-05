package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;

public final class ForgeAdapters {

    private ForgeAdapters() {}

    public static IItemHandler items(IKeyHandler<AEItemKey> handler) {
        return handler instanceof ForgeItemSource source ? source.forgeItemHandler() : ForgeItemAdapter.of(handler);
    }

    public static IFluidHandler fluids(IKeyHandler<AEFluidKey> handler) {
        return ForgeFluidAdapter.of(handler);
    }
}
