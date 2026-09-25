package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

import java.util.Objects;
import java.util.function.Consumer;

public class TagItemFilter extends TagFilter<ItemStack, ItemFilter> implements ItemFilter {

    private final Reference2BooleanOpenHashMap<Item> cache = new Reference2BooleanOpenHashMap<>();

    protected TagItemFilter() {}

    public static TagItemFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static TagItemFilter loadFilter(CompoundTag tag, Consumer<ItemFilter> itemWriter) {
        var handler = new TagItemFilter();
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
    public boolean test(ItemStack itemStack) {
        if (oreDictFilterExpression.isEmpty()) return false;
        return cache.computeIfAbsent(itemStack.getItem(), k -> TagExprFilter.tagsMatch(matchExpr, itemStack));
    }

    @Override
    Widget createQuerySlot(TagQuery query) {
        var handler = new ItemStackTransfer(1);
        query.bind(() -> handler.getStackInSlot(0).getItem(), () -> handler.getStackInSlot(0).getTags().map(t -> t));
        var slot = new PhantomItemSlot(handler, 0).xeiPhantom();
        slot.setMaxStackSize(1);
        slot.setHoverTooltips("cover.tag_filter.lookup_item", "cover.tag_filter.lookup_only");
        return slot;
    }

    @Override
    public int testItemCount(ItemStack itemStack) {
        return test(itemStack) ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean supportsAmounts() {
        return false;
    }
}
