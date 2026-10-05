package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualItemStorage;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.AEKeyFilter;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public class EnderItemLinkCover extends AbstractEnderLinkCover<VirtualItemStorage> {

    protected static final int TRANSFER_RATE = 8;

    @SaveToDisk
    @SyncToClient
    protected VirtualItemStorage storage;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected FilterHandler<ItemStack, ItemFilter> filterHandler;
    private final AEKeyFilter itemKeyFilter = key -> this.filterHandler.test(((AEItemKey) key).getReadOnlyStack());

    public EnderItemLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        filterHandler = FilterHandlers.item(this);
        if (!isRemote()) storage = VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(),
                EntryTypes.ENDER_ITEM, getChannelName());
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnItemHandler() != null;
    }

    @Override
    protected String identifier() {
        return "EILink#";
    }

    @Override
    protected VirtualItemStorage getEntry() {
        var storage = this.storage;
        if (storage == null) return new VirtualItemStorage();
        return storage;
    }

    @Override
    protected void setEntry(VirtualEntry entry) {
        storage = (VirtualItemStorage) entry;
    }

    @Override
    protected EntryTypes<VirtualItemStorage> getEntryType() {
        return EntryTypes.ENDER_ITEM;
    }

    @Override
    protected void transfer() {
        IKeyHandler<AEItemKey> ownHandler = getOwnItemHandler();
        if (ownHandler == null) return;
        switch (io) {
            case IN -> KeyTransfer.transfer(ownHandler, storage.getHandler(), 64, itemKeyFilter);
            case OUT -> KeyTransfer.transfer(storage.getHandler(), ownHandler, 64, itemKeyFilter);
        }
    }

    public @Nullable IKeyHandler<AEItemKey> getOwnItemHandler() {
        return coverHolder.getItemHandlerCap(attachedSide, false);
    }

    @Override
    protected void addEntryStatus(StatusPanel panel, BooleanSupplier visible) {
        panel.addLine("cover.ender_link.ui.item", () -> {
            if (!visible.getAsBoolean()) return EnderLinkUI.NO_VALUE;
            var stack = storedStack();
            return stack.isEmpty() ? Component.translatable("cover.ender_link.ui.empty") : stack.getHoverName();
        }).bindIcon(() -> visible.getAsBoolean() ? storedStack() : ItemStack.EMPTY);
        panel.addLine("cover.ender_link.ui.count", () -> visible.getAsBoolean() ?
                Component.literal(FormattingUtil.formatNumbers(getEntry().getHandler().amountAt(0))) : EnderLinkUI.NO_VALUE);
    }

    private ItemStack storedStack() {
        return Keys.displayStack(getEntry().getHandler().keyAt(0));
    }

    @Override
    protected Component describeEntry(VirtualItemStorage entry) {
        var handler = entry.getHandler();
        var key = handler.keyAt(0);
        if (key == null) return Component.translatable("cover.ender_link.ui.empty");
        return Keys.displayStack(key).getHoverName().copy().append(" ×" + FormattingUtil.formatNumbers(handler.amountAt(0)));
    }
}
