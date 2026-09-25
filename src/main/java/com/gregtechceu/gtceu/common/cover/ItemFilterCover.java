package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SmartItemFilter;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ItemHandlerDelegate;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ItemFilterCover extends CoverBehavior implements IUICover {

    protected ItemFilter itemFilter;
    private ItemStack loadedFilterStack;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected FilterMode filterMode = FilterMode.FILTER_INSERT;
    private FilteredItemHandlerWrapper itemFilterWrapper;
    @Getter
    @SaveToDisk
    protected ManualIOMode allowFlow = ManualIOMode.DISABLED;

    public ItemFilterCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    public ItemFilter getItemFilter() {
        if (itemFilter == null || loadedFilterStack != attachItem) {
            var loader = ItemFilter.FILTERS.get(attachItem.getItem());
            if (loader == null) {
                GTCEu.LOGGER.warn("Unregistered item filter stack {} on cover at {} {}; replacing it with the default item filter", attachItem, coverHolder.getPos(), attachedSide);
                attachItem = GTItems.ITEM_FILTER.asStack();
                if (!coverHolder.isRemote()) coverHolder.onChanged();
                itemFilter = SimpleItemFilter.loadFilter(attachItem);
            } else {
                itemFilter = loader.apply(attachItem);
            }
            loadedFilterStack = attachItem;
            if (itemFilter instanceof SmartItemFilter smart && coverHolder instanceof MachineCoverContainer mcc) {
                var machine = MetaMachine.getMachine(mcc.holder());
                if (machine != null) smart.setModeFromMachine(machine.getDefinition().getName());
            }
            itemFilter.setOnUpdated(filter -> coverHolder.onChanged());
        }
        return itemFilter;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
        coverHolder.onChanged();
    }

    public void setAllowFlow(ManualIOMode allowFlow) {
        this.allowFlow = allowFlow;
        coverHolder.onChanged();
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && coverHolder.getItemHandlerCap(attachedSide, false) != null;
    }

    @Override
    @Nullable
    public ICustomItemStackHandler getItemHandlerCap(ICustomItemStackHandler defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (itemFilterWrapper == null || itemFilterWrapper.delegate != defaultValue) {
            this.itemFilterWrapper = new FilteredItemHandlerWrapper(defaultValue);
        }
        return itemFilterWrapper;
    }

    @Override
    public void onAttached(ItemStack itemStack, ServerPlayer player) {
        super.onAttached(itemStack, player);
    }

    @Override
    public Widget createUIWidget() {
        var modes = UIElement.section().addChildren(
                CoverUIs.enumRow("cover.filter.mode.title", List.of(FilterMode.VALUES), this::getFilterMode, this::setFilterMode),
                CoverUIs.enumRow("cover.ui.manual_io", List.of(ManualIOMode.VALUES), this::getAllowFlow, this::setAllowFlow,
                        "cover.universal.manual_import_export.mode.description.0",
                        "cover.universal.manual_import_export.mode.description.1",
                        "cover.universal.manual_import_export.mode.description.2"));
        return CoverUIs.page().addChildren(modes, UIElement.section().addChild(getItemFilter().createConfigUI()));
    }

    private class FilteredItemHandlerWrapper extends ItemHandlerDelegate {

        public FilteredItemHandlerWrapper(ICustomItemStackHandler delegate) {
            super(delegate);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if ((filterMode == FilterMode.FILTER_EXTRACT) && allowFlow == ManualIOMode.UNFILTERED) return super.insertItem(slot, stack, simulate);
            if (filterMode != FilterMode.FILTER_EXTRACT && getItemFilter().test(stack)) {
                return super.insertItem(slot, stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack insertItemStacked(ItemStack stack, boolean simulate) {
            if ((filterMode == FilterMode.FILTER_EXTRACT) && allowFlow == ManualIOMode.UNFILTERED) {
                return delegate.insertItemStacked(stack, simulate);
            }
            if (filterMode != FilterMode.FILTER_EXTRACT && getItemFilter().test(stack)) {
                return delegate.insertItemStacked(stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack result = super.extractItem(slot, amount, true);
            if (result.isEmpty() && (filterMode == FilterMode.FILTER_INSERT) && allowFlow == ManualIOMode.UNFILTERED) {
                return super.extractItem(slot, amount, simulate);
            }
            if (filterMode != FilterMode.FILTER_INSERT && getItemFilter().test(result)) {
                return super.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }
    }
}
