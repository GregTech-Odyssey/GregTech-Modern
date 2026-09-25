package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.SwitchedContent;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
    @SaveToDisk(defaultValueGetter = "getDefaultItem")
    @SyncToClient
    @NotNull
    private ItemStack filterItem = getDefaultItem();
    @Nullable
    private F filter;
    @Nullable
    private CustomItemStackHandler filterSlot;
    @NotNull
    private Consumer<F> onFilterLoaded = filter -> {};
    @NotNull
    private Consumer<F> onFilterRemoved = filter -> {};
    @NotNull
    private Consumer<F> onFilterUpdated = filter -> {};

    public FilterHandler(CoverBehavior container) {
        this.container = container;
    }

    private ItemStack getDefaultItem() {
        return ItemStack.EMPTY;
    }

    protected abstract F loadFilter(ItemStack filterItem);

    protected abstract F getEmptyFilter();

    protected abstract boolean canInsertFilterItem(ItemStack itemStack);

    public ItemSlot createFilterSlot() {
        var slot = ItemSlot.of(getFilterSlot(), 0);
        slot.setChangeListener(this::updateFilter);
        slot.setBackgroundTexture(new GuiTextureGroup(UITheme.ITEM_SLOT, WidgetIcons.FILTER_SLOT));
        return slot;
    }

    public SwitchedContent createFilterConfig() {
        return new SwitchedContent(this::filterKey, (key, remote) -> {
            if (key == 0) return null;
            if (remote) return loadFilter(new ItemStack(BuiltInRegistries.ITEM.byId(key - 1))).createConfigUI();
            return getFilter().createConfigUI();
        });
    }

    public Component getFilterName() {
        return hasValidFilterItem() ? filterItem.getHoverName() : Component.translatable("gtceu.gui.filter.empty");
    }

    private int filterKey() {
        return hasValidFilterItem() ? BuiltInRegistries.ITEM.getId(filterItem.getItem()) + 1 : 0;
    }

    public boolean isFilterPresent() {
        return filter != null || hasValidFilterItem();
    }

    public F getFilter() {
        if (this.filter == null) {
            if (!hasValidFilterItem()) {
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
            hasValidFilterItem();
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
        if (hasValidFilterItem()) {
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
    }

    private boolean hasValidFilterItem() {
        if (this.filterItem.isEmpty()) return false;
        if (canInsertFilterItem(this.filterItem)) return true;

        GTCEu.LOGGER.warn("Unregistered filter stack {} in {} at {} {}; removing it", this.filterItem, container.getClass().getSimpleName(), container.coverHolder.getPos(), container.attachedSide);
        this.filterItem = ItemStack.EMPTY;
        if (this.filterSlot != null) {
            this.filterSlot.setStackInSlot(0, ItemStack.EMPTY);
        }
        if (this.filter != null) {
            this.filter = null;
            this.onFilterRemoved.accept(null);
        }
        if (!container.coverHolder.isRemote()) {
            container.coverHolder.onChanged();
        }
        return false;
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
