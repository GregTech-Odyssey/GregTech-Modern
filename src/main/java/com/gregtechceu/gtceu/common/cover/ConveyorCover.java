package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ItemHandlerDelegate;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.data.DistributionMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.datasynclib.util.ItemStackHashStrategy;
import com.gto.fastcollection.fastutil.O2OOpenCustomCacheHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ConveyorCover extends CoverBehavior implements IUICover, IControllable {

    // 8 32 128 512 1024
    public static final Int2IntFunction CONVEYOR_SCALING = tier -> 2 * (int) Math.pow(4, Math.min(tier, GTValues.LuV));
    public final int tier;
    public final int maxItemTransferRate;
    @Getter
    @SaveToDisk
    protected int transferRate;
    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    protected IO io;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected DistributionMode distributionMode;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected boolean isWorkingEnabled = true;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected final FilterHandler<ItemStack, ItemFilter> filterHandler;
    protected final ConditionalSubscriptionHandler subscriptionHandler;

    public ConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier, int maxTransferRate) {
        super(definition, coverHolder, attachedSide);
        this.tier = tier;
        this.maxItemTransferRate = maxTransferRate;
        this.transferRate = maxItemTransferRate;
        this.io = IO.OUT;
        this.distributionMode = DistributionMode.INSERT_FIRST;
        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, 20, this::isSubscriptionActive);
        filterHandler = FilterHandlers.item(this).onFilterLoaded(f -> configureFilter()).onFilterUpdated(f -> configureFilter()).onFilterRemoved(f -> configureFilter());
    }

    public ConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, CONVEYOR_SCALING.applyAsInt(tier));
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled() && getAdjacentItemHandler() != null;
    }

    @Nullable
    protected ICustomItemStackHandler getOwnItemHandler() {
        return coverHolder.getItemHandlerCap(attachedSide, false);
    }

    @Nullable
    protected IItemHandler getAdjacentItemHandler() {
        return coverHolder.getBlockEntityDirectionCache().getAdjacentItemHandler(coverHolder.getLevel(), coverHolder.getPos(), attachedSide).orElse(null);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnItemHandler() != null;
    }

    public void setTransferRate(int transferRate) {
        if (transferRate <= maxItemTransferRate) {
            this.transferRate = transferRate;
            coverHolder.onChanged();
        }
    }

    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            this.io = io;
        }
        subscriptionHandler.updateSubscription();
        coverHolder.onChanged();
    }

    public void setDistributionMode(DistributionMode distributionMode) {
        this.distributionMode = distributionMode;
        coverHolder.onChanged();
    }

    protected void setManualIOMode(ManualIOMode manualIOMode) {
        this.manualIOMode = manualIOMode;
        coverHolder.onChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscriptionHandler.initialize(coverHolder.getLevel());
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        subscriptionHandler.unsubscribe();
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        if (!filterHandler.getFilterItem().isEmpty()) {
            list.add(filterHandler.getFilterItem());
        }
        return list;
    }

    //////////////////////////////////////
    // ***** Transfer Logic *****//
    //////////////////////////////////////
    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        subscriptionHandler.updateSubscription();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            subscriptionHandler.updateSubscription();
        }
    }

    protected void update() {
        var adjacent = this.getAdjacentItemHandler();
        var self = this.getOwnItemHandler();
        if (adjacent != null && self != null) {
            switch (this.io) {
                case IN -> this.doTransferItems(adjacent, self, this.transferRate);
                case OUT -> this.doTransferItems(self, adjacent, this.transferRate);
            }
        }
        this.subscriptionHandler.updateSubscription();
    }

    protected int doTransferItems(IItemHandler sourceInventory, IItemHandler targetInventory, int maxTransferAmount) {
        return moveInventoryItems(sourceInventory, targetInventory, maxTransferAmount);
    }

    protected int moveInventoryItems(IItemHandler sourceInventory, IItemHandler targetInventory, int maxTransferAmount) {
        return GTTransferUtils.transferItemsFiltered(sourceInventory, targetInventory,
                filterHandler.getFilter(), maxTransferAmount);
    }

    protected static int moveInventoryItemsExact(IItemHandler sourceInventory, IItemHandler targetInventory,
                                                 TypeItemInfo itemInfo) {
        // first, compute how much can we extract in reality from the machine,
        // because totalCount is based on what getStackInSlot returns, which may differ from what
        // extractItem() will return
        ItemStack resultStack = itemInfo.itemStack.copy();
        int totalExtractedCount = 0;
        int itemsLeftToExtract = itemInfo.totalCount;
        for (int i = 0; i < itemInfo.slots.size(); i++) {
            int slotIndex = itemInfo.slots.getInt(i);
            ItemStack extractedStack = sourceInventory.extractItem(slotIndex, itemsLeftToExtract, true);
            if (!extractedStack.isEmpty() && ItemStack.isSameItemSameTags(resultStack, extractedStack)) {
                totalExtractedCount += extractedStack.getCount();
                itemsLeftToExtract -= extractedStack.getCount();
            }
            if (itemsLeftToExtract == 0) {
                break;
            }
        }
        // if amount of items extracted is not equal to the amount of items we
        // wanted to extract, abort item extraction
        if (totalExtractedCount != itemInfo.totalCount) {
            return 0;
        }
        // adjust size of the result stack accordingly
        resultStack.setCount(totalExtractedCount);
        // now, see how much we can insert into destination inventory
        ItemStack remainder = GTTransferUtils.insertItemStacked(targetInventory, resultStack, true);
        if (!remainder.isEmpty()) {
            return 0;
        }
        // Extract first so a source-side state change cannot duplicate items.
        itemsLeftToExtract = itemInfo.totalCount;
        totalExtractedCount = 0;
        for (int i = 0; i < itemInfo.slots.size(); i++) {
            int slotIndex = itemInfo.slots.getInt(i);
            ItemStack extractedStack = sourceInventory.extractItem(slotIndex, itemsLeftToExtract, false);
            if (!extractedStack.isEmpty() && ItemStack.isSameItemSameTags(resultStack, extractedStack)) {
                totalExtractedCount += extractedStack.getCount();
                itemsLeftToExtract -= extractedStack.getCount();
            } else if (!extractedStack.isEmpty()) {
                GTTransferUtils.returnItemToSource(sourceInventory, slotIndex, extractedStack);
            }
            if (itemsLeftToExtract == 0) {
                break;
            }
        }
        if (totalExtractedCount != itemInfo.totalCount) {
            if (totalExtractedCount > 0) {
                resultStack.setCount(totalExtractedCount);
                GTTransferUtils.returnItemToSource(sourceInventory, itemInfo.slots.getInt(0), resultStack);
            }
            return 0;
        }

        resultStack.setCount(totalExtractedCount);
        remainder = GTTransferUtils.insertItemStacked(targetInventory, resultStack, false);
        int transferred = totalExtractedCount - Math.min(totalExtractedCount, remainder.getCount());
        if (!remainder.isEmpty()) {
            GTTransferUtils.returnItemToSource(sourceInventory, itemInfo.slots.getInt(0), remainder);
        }
        return transferred;
    }

    protected int moveInventoryItems(IItemHandler sourceInventory, IItemHandler targetInventory, Map<ItemStack, GroupItemInfo> itemInfos, int maxTransferAmount) {
        ItemFilter filter = filterHandler.getFilter();
        int itemsLeftToTransfer = maxTransferAmount;
        for (int i = 0; i < sourceInventory.getSlots(); i++) {
            ItemStack itemStack = sourceInventory.getStackInSlot(i);
            if (itemStack.isEmpty() || !filter.test(itemStack) || !itemInfos.containsKey(itemStack)) {
                continue;
            }
            GroupItemInfo itemInfo = itemInfos.get(itemStack);
            int transferred = GTTransferUtils.transferItemsFromSlot(sourceInventory, i, targetInventory, filter,
                    Math.min(itemInfo.totalCount, itemsLeftToTransfer));
            if (transferred > 0) {
                itemsLeftToTransfer -= transferred;
                itemInfo.totalCount -= transferred;
                if (itemInfo.totalCount == 0) {
                    itemInfos.remove(itemStack);
                    if (itemInfos.isEmpty()) {
                        break;
                    }
                }
                if (itemsLeftToTransfer == 0) {
                    break;
                }
            }
        }
        return maxTransferAmount - itemsLeftToTransfer;
    }

    protected O2OOpenCustomCacheHashMap<ItemStack, TypeItemInfo> countInventoryItemsByType(IItemHandler inventory) {
        ItemFilter filter = filterHandler.getFilter();
        var result = new O2OOpenCustomCacheHashMap<ItemStack, TypeItemInfo>(ItemStackHashStrategy.ITEM_AND_TAG);
        for (int srcIndex = 0; srcIndex < inventory.getSlots(); srcIndex++) {
            ItemStack itemStack = inventory.getStackInSlot(srcIndex);
            if (itemStack.isEmpty() || !filter.test(itemStack)) {
                continue;
            }
            var itemInfo = result.computeIfAbsent(itemStack, s -> new TypeItemInfo(itemStack, new IntArrayList(), 0));
            itemInfo.totalCount += itemStack.getCount();
            itemInfo.slots.add(srcIndex);
        }
        return result;
    }

    protected O2OOpenCustomCacheHashMap<ItemStack, GroupItemInfo> countInventoryItemsByMatchSlot(IItemHandler inventory) {
        ItemFilter filter = filterHandler.getFilter();
        var result = new O2OOpenCustomCacheHashMap<ItemStack, GroupItemInfo>(ItemStackHashStrategy.ITEM_AND_TAG);
        for (int srcIndex = 0; srcIndex < inventory.getSlots(); srcIndex++) {
            ItemStack itemStack = inventory.getStackInSlot(srcIndex);
            if (itemStack.isEmpty() || !filter.test(itemStack)) {
                continue;
            }
            var itemInfo = result.computeIfAbsent(itemStack, s -> new GroupItemInfo(itemStack, 0));
            itemInfo.totalCount += itemStack.getCount();
        }
        return result;
    }

    protected static class TypeItemInfo {

        public final ItemStack itemStack;
        public final IntList slots;
        public int totalCount;

        public TypeItemInfo(final ItemStack itemStack, final IntList slots, final int totalCount) {
            this.itemStack = itemStack;
            this.slots = slots;
            this.totalCount = totalCount;
        }
    }

    protected static class GroupItemInfo {

        public final ItemStack itemStack;
        public int totalCount;

        public GroupItemInfo(final ItemStack itemStack, final int totalCount) {
            this.itemStack = itemStack;
            this.totalCount = totalCount;
        }
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var transfer = CoverUIs.section("cover.ui.transfer").addChildren(
                CoverUIs.numberRow("cover.conveyor.ui.transfer_rate",
                        NumberField.of(LayoutStyle.AUTO, () -> transferRate, value -> setTransferRate((int) value), 1, maxItemTransferRate)),
                CoverUIs.enumRow("cover.ui.io", List.of(IO.IN, IO.OUT), this::getIo, this::setIo, "cover.conveyor.ui.io.tooltip"));
        buildAdditionalUI(transfer);
        var distribution = CoverUIs.enumRow("cover.conveyor.ui.distribution", List.of(DistributionMode.VALUES),
                this::getDistributionMode, this::setDistributionMode)
                .disabled(() -> !shouldDisplayDistributionMode(), "cover.conveyor.ui.distribution_no_pipe");
        var modes = CoverUIs.section("cover.ui.modes").addChildren(distribution,
                CoverUIs.enumRow("cover.ui.manual_io", List.of(ManualIOMode.VALUES), this::getManualIOMode, this::setManualIOMode,
                        "cover.universal.manual_import_export.mode.description.0",
                        "cover.universal.manual_import_export.mode.description.1",
                        "cover.universal.manual_import_export.mode.description.2"));
        return CoverUIs.page().addChildren(transfer, modes, CoverUIs.filterSection(filterHandler));
    }

    private boolean shouldDisplayDistributionMode() {
        return coverHolder.holder() instanceof ItemPipeBlockEntity || getNeighbor() instanceof ItemPipeBlockEntity;
    }

    protected void buildAdditionalUI(UIElement section) {}

    protected void configureFilter() {}

    /////////////////////////////////////
    // *** CAPABILITY OVERRIDE ***//
    /////////////////////////////////////
    private CoverableItemHandlerWrapper itemHandlerWrapper;

    @Nullable
    @Override
    public ICustomItemStackHandler getItemHandlerCap(@Nullable ICustomItemStackHandler defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (itemHandlerWrapper == null || itemHandlerWrapper.delegate != defaultValue) {
            this.itemHandlerWrapper = new CoverableItemHandlerWrapper(defaultValue);
        }
        return itemHandlerWrapper;
    }

    private class CoverableItemHandlerWrapper extends ItemHandlerDelegate {

        public CoverableItemHandlerWrapper(ICustomItemStackHandler delegate) {
            super(delegate);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (io == IO.OUT && manualIOMode == ManualIOMode.DISABLED) {
                return stack;
            }
            if (manualIOMode == ManualIOMode.FILTERED && !filterHandler.test(stack)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack insertItemStacked(ItemStack stack, boolean simulate) {
            if (io == IO.OUT && manualIOMode == ManualIOMode.DISABLED) {
                return stack;
            }
            if (manualIOMode == ManualIOMode.FILTERED && !filterHandler.test(stack)) {
                return stack;
            }
            return delegate.insertItemStacked(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (io == IO.IN && manualIOMode == ManualIOMode.DISABLED) {
                return ItemStack.EMPTY;
            }
            if (manualIOMode == ManualIOMode.FILTERED) {
                ItemStack result = super.extractItem(slot, amount, true);
                if (result.isEmpty() || !filterHandler.test(result)) {
                    return ItemStack.EMPTY;
                }
                return simulate ? result : super.extractItem(slot, amount, false);
            }
            return super.extractItem(slot, amount, simulate);
        }
    }
}
