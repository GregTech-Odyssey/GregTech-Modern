package com.gregtechceu.gtceu.api.cover.filter;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.IFieldDataHolder;

public interface FilterHandlers {

    /** Host may be a cover or a machine (e.g. item export bus). */
    static FilterHandler<ItemStack, ItemFilter> item(IFieldDataHolder container) {
        return new FilterHandler<>(container) {

            @Override
            protected ItemFilter loadFilter(ItemStack filterItem) {
                return ItemFilter.loadFilter(filterItem);
            }

            @Override
            protected ItemFilter getEmptyFilter() {
                return ItemFilter.EMPTY;
            }

            @Override
            protected boolean canInsertFilterItem(ItemStack itemStack) {
                return ItemFilter.FILTERS.containsKey(itemStack.getItem());
            }
        };
    }

    /** Host may be a cover or a machine. */
    static FilterHandler<FluidStack, FluidFilter> fluid(IFieldDataHolder container) {
        return new FilterHandler<>(container) {

            @Override
            protected FluidFilter loadFilter(ItemStack filterItem) {
                return FluidFilter.loadFilter(filterItem);
            }

            @Override
            protected FluidFilter getEmptyFilter() {
                return FluidFilter.EMPTY;
            }

            @Override
            protected boolean canInsertFilterItem(ItemStack itemStack) {
                return FluidFilter.FILTERS.containsKey(itemStack.getItem());
            }
        };
    }
}
