package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.uiwidgets.filter.TagLookupView;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.hooks.IAEFluid;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TagFluidFilter extends TagFilter<FluidStack, FluidFilter> implements FluidFilter {

    private final Int2BooleanOpenHashMap cache = new Int2BooleanOpenHashMap();

    protected TagFluidFilter() {}

    public static TagFluidFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static TagFluidFilter loadFilter(CompoundTag tag, Consumer<FluidFilter> itemWriter) {
        var handler = new TagFluidFilter();
        handler.itemWriter = itemWriter;
        handler.oreDictFilterExpression = tag.getString("oreDict");
        handler.matchExpr = null;
        handler.cache.clear();
        handler.matchExpr = TagExprFilter.parseExpression(handler.oreDictFilterExpression);
        return handler;
    }

    public void setOreDict(String oreDict) {
        cache.clear();
        super.setOreDict(oreDict);
    }

    @Override
    public boolean test(FluidStack fluidStack) {
        if (oreDictFilterExpression.isEmpty()) return false;
        return cache.computeIfAbsent(((IAEFluid) fluidStack.getFluid()).ae2$getUid(), k -> TagExprFilter.tagsMatch(matchExpr, fluidStack));
    }

    @Override
    TagLookupView createLookup(Supplier<String> getter, Consumer<String> setter, Consumer<String> onServerPick) {
        return TagLookupView.fluids("cover.tag_filter.tags", getter, setter, onServerPick, "cover.tag_filter.lookup_fluid", "cover.tag_filter.lookup_only");
    }

    @Override
    public int testFluidAmount(FluidStack fluidStack) {
        return test(fluidStack) ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean supportsAmounts() {
        return false;
    }
}
