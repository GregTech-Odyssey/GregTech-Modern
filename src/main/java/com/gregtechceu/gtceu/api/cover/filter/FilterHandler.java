package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class FilterHandler<T, F extends Filter<T, F>> implements IFieldDataHolder {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);
    private final CoverBehavior container;

    @Getter
    @SaveToDisk
    @SyncToClient
    @NotNull
    private ItemStack filterItem = ItemStack.EMPTY;
    @Nullable
    private F filter;
    @Nullable
    private CustomItemStackHandler filterSlot;
    @Nullable
    private WidgetGroup filterGroup;
    @NotNull
    private Consumer<F> onFilterLoaded = filter -> {};
    @NotNull
    private Consumer<F> onFilterRemoved = filter -> {};
    @NotNull
    private Consumer<F> onFilterUpdated = filter -> {};

    public FilterHandler(CoverBehavior container) {
        this.container = container;
    }

    protected abstract F loadFilter(ItemStack filterItem);

    protected abstract F getEmptyFilter();

    protected abstract boolean canInsertFilterItem(ItemStack itemStack);

    //////////////////////////////////
    // ***** PUBLIC API ******//
    //////////////////////////////////
    public Widget createFilterSlotUI(int xPos, int yPos) {
        return new SlotWidget(getFilterSlot(), 0, xPos, yPos).setChangeListener(this::updateFilter).setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.FILTER_SLOT_OVERLAY));
    }

    public Widget createFilterConfigUI(int xPos, int yPos, int width, int height) {
        this.filterGroup = new WidgetGroup(xPos, yPos, width, height);
        if (!this.filterItem.isEmpty()) {
            this.filterGroup.addWidget(getFilter().openConfigurator(0, 0));
        }
        return this.filterGroup;
    }

    public boolean isFilterPresent() {
        return filter != null || !filterItem.isEmpty();
    }

    public F getFilter() {
        if (this.filter == null) {
            if (this.filterItem.isEmpty()) {
                return getEmptyFilter();
            } else {
                loadFilterFromItem();
            }
        }
        return this.filter;
    }

    public boolean test(T resource) {
        return getFilter().test(resource);
    }

    public FilterHandler<T, F> onFilterLoaded(Consumer<F> onFilterLoaded) {
        this.onFilterLoaded = onFilterLoaded;
        return this;
    }

    public FilterHandler<T, F> onFilterRemoved(Consumer<F> onFilterRemoved) {
        this.onFilterRemoved = onFilterRemoved;
        return this;
    }

    public FilterHandler<T, F> onFilterUpdated(Consumer<F> onFilterUpdated) {
        this.onFilterUpdated = onFilterUpdated;
        return this;
    }

    ///////////////////////////////////////
    // ***** FILTER HANDLING ******//
    ///////////////////////////////////////
    private CustomItemStackHandler getFilterSlot() {
        if (this.filterSlot == null) {
            this.filterSlot = new SingleCustomItemStackHandler(this.filterItem);
            this.filterSlot.setFilter(this::canInsertFilterItem);
        }
        return this.filterSlot;
    }

    private void updateFilter() {
        var filterContainer = getFilterSlot();
        if (GTCEu.isClientThread()) {
            if (!filterContainer.getStackInSlot(0).isEmpty() && !this.filterItem.isEmpty()) {
                return;
            }
        }
        this.filterItem = filterContainer.getStackInSlot(0);
        if (this.filter != null) {
            this.filter = null;
            this.onFilterRemoved.accept(null);
        }
        loadFilterFromItem();
    }

    private void loadFilterFromItem() {
        if (!this.filterItem.isEmpty()) {
            this.filter = loadFilter(this.filterItem);
            filter.setOnUpdated(this.onFilterUpdated);
            if (filter instanceof SmartItemFilter smart && container instanceof CoverBehavior cover && cover.coverHolder instanceof MachineCoverContainer mcc) {
                var machine = MetaMachine.getMachine(mcc.holder());
                if (machine != null) {
                    smart.setModeFromMachine(machine.getDefinition().getName());
                }
            }
            this.onFilterLoaded.accept(this.filter);
        }
        updateFilterGroupUI();
    }

    private void updateFilterGroupUI() {
        if (this.filterGroup == null) return;
        this.filterGroup.clearAllWidgets();
        if (!this.filterItem.isEmpty() && this.filter != null) {
            this.filterGroup.addWidget(this.filter.openConfigurator(0, 0));
        }
    }

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        container.scheduleUpdate(side);
    }
}
