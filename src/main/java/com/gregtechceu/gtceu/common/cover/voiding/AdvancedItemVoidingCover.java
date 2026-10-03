package com.gregtechceu.gtceu.common.cover.voiding;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.common.cover.data.VoidingMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class AdvancedItemVoidingCover extends ItemVoidingCover {

    @SaveToDisk
    @SyncToClient
    private VoidingMode voidingMode = VoidingMode.VOID_ANY;
    @SaveToDisk
    protected int globalVoidingLimit = 1;

    public AdvancedItemVoidingCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    //////////////////////////////////////////////
    // *********** COVER LOGIC ***********//
    //////////////////////////////////////////////
    @Override
    protected void doVoidItems() {
        IKeyHandler<AEItemKey> handler = getOwnItemHandler();
        if (handler == null) {
            return;
        }
        switch (voidingMode) {
            case VOID_ANY -> voidAny(handler);
            case VOID_OVERFLOW -> voidOverflow(handler);
        }
    }

    private void voidOverflow(IKeyHandler<AEItemKey> handler) {
        var sourceItemAmounts = countInventoryItemsByType(handler);
        for (TypeItemInfo itemInfo : sourceItemAmounts.values()) {
            long itemToVoidAmount = itemInfo.totalCount - getFilteredItemAmount(itemInfo.key);
            if (itemToVoidAmount <= 0) {
                continue;
            }
            var slots = itemInfo.slots;
            for (int i = 0; i < slots.size() && itemToVoidAmount > 0; i++) {
                itemToVoidAmount -= handler.extract(slots.getInt(i), itemInfo.key, itemToVoidAmount, false);
            }
        }
    }

    private int getFilteredItemAmount(AEItemKey key) {
        if (!filterHandler.isFilterPresent()) return globalVoidingLimit;
        ItemFilter filter = filterHandler.getFilter();
        return filter.isBlackList() ? globalVoidingLimit : filter.testItemCount(key.getReadOnlyStack());
    }

    public void setVoidingMode(VoidingMode voidingMode) {
        this.voidingMode = voidingMode;
        if (!this.isRemote()) {
            configureFilter();
        }
    }

    public void setGlobalVoidingLimit(int globalVoidingLimit) {
        this.globalVoidingLimit = globalVoidingLimit;
        coverHolder.onChanged();
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    protected void buildAdditionalUI(UIElement section) {
        var amount = NumberField.ofInt(LayoutStyle.AUTO, () -> globalVoidingLimit, this::setGlobalVoidingLimit,
                () -> 1, () -> voidingMode.maxStackSize);
        amount.disabled(() -> voidingMode != VoidingMode.VOID_ANY && isAmountFromFilter(), "cover.conveyor.ui.amount_from_filter");
        section.addChildren(
                CoverUIs.enumRow("cover.item_voiding.ui.mode", List.of(VoidingMode.values()), () -> voidingMode, this::setVoidingMode,
                        "cover.voiding.voiding_mode.description.0",
                        "cover.voiding.voiding_mode.description.1"),
                Form.numberRow("cover.item_voiding.ui.keep_amount", amount, "cover.item_voiding.ui.keep_amount.tooltip")
                        .disabled(() -> voidingMode == VoidingMode.VOID_ANY, "cover.item_voiding.ui.amount_unused"));
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleItemFilter filter) {
            filter.setMaxStackSize(this.voidingMode.maxStackSize);
        }
    }

    private boolean isAmountFromFilter() {
        return this.filterHandler.isFilterPresent() && !this.filterHandler.getFilter().isBlackList();
    }
}
