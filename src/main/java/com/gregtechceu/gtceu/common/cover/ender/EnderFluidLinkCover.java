package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualTank;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class EnderFluidLinkCover extends AbstractEnderLinkCover<VirtualTank> {

    @SaveToDisk
    @SyncToClient
    protected VirtualTank visualTank;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected final FilterHandler<FluidStack, FluidFilter> filterHandler;
    private final AEKeyFilter fluidKeyFilter = this::matchesFilter;

    public EnderFluidLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        filterHandler = FilterHandlers.fluid(this);
        if (!isRemote()) visualTank = VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), EntryTypes.ENDER_FLUID, getChannelName());
    }

    @Override
    protected VirtualTank getEntry() {
        var storage = this.visualTank;
        if (storage == null) return new VirtualTank();
        return storage;
    }

    @Override
    protected void setEntry(VirtualEntry entry) {
        visualTank = (VirtualTank) entry;
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnFluidHandler() != null;
    }

    @Override
    protected EntryTypes<VirtualTank> getEntryType() {
        return EntryTypes.ENDER_FLUID;
    }

    @Override
    protected String identifier() {
        return "EFLink#";
    }

    @Nullable
    protected IKeyHandler<AEFluidKey> getOwnFluidHandler() {
        return coverHolder.getFluidHandlerCap(attachedSide, false);
    }

    @Override
    protected void transfer() {
        var ownFluidHandler = getOwnFluidHandler();
        if (ownFluidHandler == null) return;
        switch (io) {
            case IN -> KeyTransfer.transfer(ownFluidHandler, visualTank.getFluidTank(), VirtualTank.DEFAULT_CAPACITY, fluidKeyFilter);
            case OUT -> KeyTransfer.transfer(visualTank.getFluidTank(), ownFluidHandler, VirtualTank.DEFAULT_CAPACITY, fluidKeyFilter);
        }
    }

    @Override
    protected void addEntryStatus(StatusPanel panel, BooleanSupplier visible) {
        panel.addLine("cover.ender_link.ui.fluid", () -> visible.getAsBoolean() ? fluidName(Keys.displayFluid(getEntry().getFluidTank().keyAt(0))) : EnderLinkUI.NO_VALUE);
        panel.addLine("cover.ender_link.ui.amount", () -> {
            if (!visible.getAsBoolean()) return EnderLinkUI.NO_VALUE;
            var tank = getEntry().getFluidTank();
            return Component.literal(FormattingUtil.formatNumbers(tank.amountAt(0)) + " / " + FormattingUtil.formatNumbers(tank.slotLimit(0)) + " mB");
        });
    }

    @Override
    protected Component describeEntry(VirtualTank entry) {
        var tank = entry.getFluidTank();
        var key = tank.keyAt(0);
        if (key == null) return Component.translatable("cover.ender_link.ui.empty");
        return key.getDisplayName().copy().append(" " + FormattingUtil.formatNumbers(tank.amountAt(0)) + " mB");
    }

    private static Component fluidName(FluidStack fluid) {
        return fluid.isEmpty() ? Component.translatable("cover.ender_link.ui.empty") : fluid.getDisplayName();
    }

    private boolean matchesFilter(AEKey key) {
        return key instanceof AEFluidKey k && filterHandler.test(k.getReadOnlyStack());
    }
}
