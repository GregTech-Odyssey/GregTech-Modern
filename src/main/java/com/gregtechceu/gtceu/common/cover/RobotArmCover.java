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
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import lombok.Getter;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RobotArmCover extends ConveyorCover {

    private static final Component SUPPLY_AMOUNT = Component.translatable("cover.robotic_arm.ui.supply_amount");
    private static final Component KEEP_AMOUNT = Component.translatable("cover.robotic_arm.ui.keep_amount");
    private static final Component AMOUNT_TOOLTIP = Component.translatable("cover.robotic_arm.ui.amount.tooltip");
    private static final Component AMOUNT_FROM_FILTER = Component.translatable("cover.conveyor.ui.amount_from_filter");
    private static final Component AMOUNT_UNUSED = Component.translatable("cover.robotic_arm.ui.amount_unused");
    private static final Component TRANSFER_MODE_TITLE = Component.translatable("cover.robotic_arm.ui.transfer_mode");
    private static final Component[] TRANSFER_MODE_TOOLTIPS = {
            Component.translatable("cover.robotic_arm.transfer_mode.description.0"),
            Component.translatable("cover.robotic_arm.transfer_mode.description.1"),
            Component.translatable("cover.robotic_arm.transfer_mode.description.2")
    };

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
        var sourceItemAmounts = countInventoryItems(sourceInventory);
        int itemsTransferred = 0;
        long maxTotalTransferAmount = (long) maxTransferAmount + itemsTransferBuffered;
        boolean notEnoughTransferRate = false;
        for (var it = Reference2LongMaps.fastIterator(sourceItemAmounts); it.hasNext();) {
            var entry = it.next();
            AEItemKey key = entry.getKey();
            int itemToMoveAmount = getFilteredItemAmount(key);
            if (itemToMoveAmount <= 0 || entry.getLongValue() < itemToMoveAmount) {
                continue;
            }
            if (maxTotalTransferAmount >= itemToMoveAmount) {
                int transferred = moveInventoryItemsExact(sourceInventory, targetInventory, key, itemToMoveAmount);
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
        var sourceItemAmounts = countInventoryItems(sourceInventory);
        for (var it = Reference2LongMaps.fastIterator(sourceItemAmounts); it.hasNext();) {
            var entry = it.next();
            long missing = getFilteredItemAmount(entry.getKey()) - targetInventory.count(entry.getKey());
            if (missing > 0) {
                entry.setValue(Math.min(missing, entry.getLongValue()));
            } else {
                it.remove();
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
        amountLabel.tooltips(AMOUNT_TOOLTIP);
        var amount = NumberField.ofInt(LayoutStyle.AUTO, () -> globalTransferLimit, this::setGlobalTransferLimit,
                () -> 1, () -> transferMode.maxStackSize);
        amount.disabled(() -> transferMode != TransferMode.TRANSFER_ANY && isAmountFromFilter(), AMOUNT_FROM_FILTER);
        var amountRow = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(amountLabel, amount)
                .disabled(() -> transferMode == TransferMode.TRANSFER_ANY, AMOUNT_UNUSED);
        section.addChildren(
                CoverUIs.enumRow(TRANSFER_MODE_TITLE, List.of(TransferMode.values()), this::getTransferMode, this::setTransferMode,
                        TRANSFER_MODE_TOOLTIPS),
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
