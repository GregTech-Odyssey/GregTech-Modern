package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerDelegate;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidFilterCover extends CoverBehavior implements IUICover {

    protected FluidFilter fluidFilter;
    private ItemStack loadedFilterStack;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected FilterMode filterMode = FilterMode.FILTER_INSERT;
    private FilteredFluidHandlerWrapper fluidFilterWrapper;
    @Getter
    @SaveToDisk
    protected ManualIOMode allowFlow = ManualIOMode.DISABLED;

    public FluidFilterCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
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
        return super.canAttach() && coverHolder.getFluidHandlerCap(attachedSide, false) != null;
    }

    public FluidFilter getFluidFilter() {
        if (fluidFilter == null || loadedFilterStack != attachItem) {
            var loader = FluidFilter.FILTERS.get(attachItem.getItem());
            if (loader == null) {
                GTCEu.LOGGER.warn("Unregistered fluid filter stack {} on cover at {} {}; replacing it with the default fluid filter", attachItem, coverHolder.getPos(), attachedSide);
                attachItem = GTItems.FLUID_FILTER.asStack();
                if (!coverHolder.isRemote()) coverHolder.onChanged();
                fluidFilter = SimpleFluidFilter.loadFilter(attachItem);
            } else {
                fluidFilter = loader.apply(attachItem);
            }
            loadedFilterStack = attachItem;
            fluidFilter.setOnUpdated(filter -> coverHolder.onChanged());
        }
        return fluidFilter;
    }

    @Override
    @Nullable
    public ICustomFluidStackHandler getFluidHandlerCap(@Nullable ICustomFluidStackHandler defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (fluidFilterWrapper == null || fluidFilterWrapper.delegate != defaultValue) {
            this.fluidFilterWrapper = new FilteredFluidHandlerWrapper(defaultValue);
        }
        return fluidFilterWrapper;
    }

    @Override
    public Widget createUIWidget() {
        var modes = UIElement.section().addChildren(
                CoverUIs.enumRow("cover.filter.mode.title", List.of(FilterMode.VALUES), this::getFilterMode, this::setFilterMode),
                CoverUIs.enumRow("cover.ui.manual_io", List.of(ManualIOMode.VALUES), this::getAllowFlow, this::setAllowFlow,
                        "cover.universal.manual_import_export.mode.description.0",
                        "cover.universal.manual_import_export.mode.description.1",
                        "cover.universal.manual_import_export.mode.description.2"));
        return CoverUIs.page().addChildren(modes, UIElement.section().addChild(getFluidFilter().createConfigUI()));
    }

    private class FilteredFluidHandlerWrapper extends FluidHandlerDelegate {

        public FilteredFluidHandlerWrapper(ICustomFluidStackHandler delegate) {
            super(delegate);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if ((filterMode == FilterMode.FILTER_EXTRACT) && allowFlow == ManualIOMode.UNFILTERED) return super.fill(resource, action);
            if (filterMode != FilterMode.FILTER_EXTRACT && getFluidFilter().test(resource)) return super.fill(resource, action);
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if ((filterMode == FilterMode.FILTER_INSERT) && allowFlow == ManualIOMode.UNFILTERED) return super.drain(resource, action);
            if (filterMode != FilterMode.FILTER_INSERT && getFluidFilter().test(resource)) return super.drain(resource, action);
            return FluidStack.EMPTY;
        }
    }
}
