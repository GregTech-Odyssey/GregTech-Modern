package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RobotArmCover extends ConveyorCover {

    private static final Component SUPPLY_AMOUNT = Component.translatable("cover.robotic_arm.ui.supply_amount");
    private static final Component KEEP_AMOUNT = Component.translatable("cover.robotic_arm.ui.keep_amount");

    @Getter
    @SaveToDisk
    @SyncToClient
    protected TransferMode transferMode;
    @Getter
    @SaveToDisk
    protected int globalTransferLimit;
    protected int itemsTransferBuffered;

    public RobotArmCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier, int maxTransferRate) {
        super(definition, coverHolder, attachedSide, tier, maxTransferRate);
        setTransferMode(TransferMode.TRANSFER_ANY);
    }

    public RobotArmCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, CONVEYOR_SCALING.applyAsInt(tier));
    }

    @Override
    protected int doTransferItems(IKeyHandler<AEItemKey> itemHandler, IKeyHandler<AEItemKey> myItemHandler, int maxTransferAmount) {
        if (transferMode == TransferMode.KEEP_EXACT && coverHolder.holder() instanceof ItemPipeBlockEntity) {
            return 0;
        }
        return switch (transferMode) {
            case TRANSFER_ANY -> moveInventoryItems(itemHandler, myItemHandler, maxTransferAmount);
            case TRANSFER_EXACT -> doTransferExact(itemHandler, myItemHandler, maxTransferAmount);
            case KEEP_EXACT -> doKeepExact(itemHandler, myItemHandler, maxTransferAmount);
        };
    }

    protected int doTransferExact(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, int maxTransferAmount) {
        var sourceItemAmount = countInventoryItemsByType(sourceInventory);
        var iterator = sourceItemAmount.reference2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            TypeItemInfo sourceInfo = iterator.next().getValue();
            long itemAmount = sourceInfo.totalCount;
            int itemToMoveAmount = getFilteredItemAmount(sourceInfo.key);
            if (itemAmount >= itemToMoveAmount) {
                sourceInfo.totalCount = itemToMoveAmount;
            } else {
                iterator.remove();
            }
        }
        int itemsTransferred = 0;
        long maxTotalTransferAmount = (long) maxTransferAmount + itemsTransferBuffered;
        boolean notEnoughTransferRate = false;
        for (TypeItemInfo itemInfo : sourceItemAmount.values()) {
            if (maxTotalTransferAmount >= itemInfo.totalCount) {
                int transferred = moveInventoryItemsExact(sourceInventory, targetInventory, itemInfo);
                itemsTransferred += transferred;
                maxTotalTransferAmount -= transferred;
            } else {
                notEnoughTransferRate = true;
            }
        }
        // if we didn't transfer anything because of too small transfer rate, buffer it
        if (itemsTransferred == 0 && notEnoughTransferRate) {
            itemsTransferBuffered += maxTransferAmount;
        } else {
            // otherwise, if transfer succeed, empty transfer buffer value
            itemsTransferBuffered = 0;
        }
        return Math.min(itemsTransferred, maxTransferAmount);
    }

    protected int doKeepExact(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, int maxTransferAmount) {
        var targetItemAmounts = countInventoryItemsByMatchSlot(targetInventory);
        var sourceItemAmounts = countInventoryItemsByMatchSlot(sourceInventory);
        var iterator = sourceItemAmounts.reference2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var filteredItem = iterator.next();
            GroupItemInfo sourceInfo = filteredItem.getValue();
            int itemToKeepAmount = getFilteredItemAmount(sourceInfo.key);
            long itemAmount = 0;
            GroupItemInfo destItemInfo = targetItemAmounts.get(filteredItem.getKey());
            if (destItemInfo != null) {
                itemAmount = destItemInfo.totalCount;
            }
            if (itemAmount < itemToKeepAmount) {
                sourceInfo.totalCount = itemToKeepAmount - itemAmount;
            } else {
                iterator.remove();
            }
        }
        return moveInventoryItems(sourceInventory, targetInventory, sourceItemAmounts, maxTransferAmount);
    }

    private int getFilteredItemAmount(AEItemKey key) {
        if (!filterHandler.isFilterPresent()) return globalTransferLimit;
        ItemFilter filter = filterHandler.getFilter();
        return filter.supportsAmounts() ? filter.testItemCount(key.getReadOnlyStack()) : globalTransferLimit;
    }

    public int getBuffer() {
        return itemsTransferBuffered;
    }

    public void buffer(int amount) {
        itemsTransferBuffered += amount;
    }

    public void clearBuffer() {
        itemsTransferBuffered = 0;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    protected void buildAdditionalUI(UIElement section) {
        var amountLabel = TextLine.of(LayoutStyle.AUTO, () -> transferMode == TransferMode.KEEP_EXACT ? KEEP_AMOUNT : SUPPLY_AMOUNT)
                .bindClientColor(UITheme::panelText);
        amountLabel.tooltips("cover.robotic_arm.ui.amount.tooltip");
        var amount = NumberField.ofInt(LayoutStyle.AUTO, () -> globalTransferLimit, this::setGlobalTransferLimit,
                () -> 1, () -> transferMode.maxStackSize);
        amount.disabled(() -> transferMode != TransferMode.TRANSFER_ANY && isAmountFromFilter(), "cover.conveyor.ui.amount_from_filter");
        var amountRow = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(amountLabel, amount)
                .disabled(() -> transferMode == TransferMode.TRANSFER_ANY, "cover.robotic_arm.ui.amount_unused");
        section.addChildren(
                CoverUIs.enumRow("cover.robotic_arm.ui.transfer_mode", List.of(TransferMode.values()), this::getTransferMode, this::setTransferMode,
                        "cover.robotic_arm.transfer_mode.description.0",
                        "cover.robotic_arm.transfer_mode.description.1",
                        "cover.robotic_arm.transfer_mode.description.2"),
                amountRow);
    }

    private void setTransferMode(TransferMode transferMode) {
        this.transferMode = transferMode;
        if (!this.isRemote()) {
            configureFilter();
        }
    }

    public void setGlobalTransferLimit(int globalTransferLimit) {
        this.globalTransferLimit = globalTransferLimit;
        coverHolder.onChanged();
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleItemFilter filter) {
            filter.setMaxStackSize(filter.isBlackList() ? 1 : transferMode.maxStackSize);
        }
    }

    private boolean isAmountFromFilter() {
        return this.filterHandler.isFilterPresent() && this.filterHandler.getFilter().supportsAmounts();
    }
}
