package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.PhantomSlotWidget;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
    StackHandlerWidget<ItemStack, ItemFilter> getItemHandler() {
        return new PhantomSlot(new CustomItemStackHandler(1));
    }

    @Override
    public int testItemCount(ItemStack itemStack) {
        return test(itemStack) ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean supportsAmounts() {
        return false;
    }

    public static class PhantomSlot extends PhantomSlotWidget implements StackHandlerWidget<ItemStack, ItemFilter> {

        final CustomItemStackHandler handler;

        public PhantomSlot(CustomItemStackHandler handler) {
            super(handler, 0, 90, 30);
            this.handler = handler;
            setBackground(GuiTextures.SLOT);
        }

        @Override
        public void updateScreen() {
            super.updateScreen();
            setMaxStackSize(1);
        }

        @Override
        public void detectAndSendChanges() {
            super.detectAndSendChanges();
            setMaxStackSize(1);
        }

        @Override
        public ItemStack getStack() {
            return handler.getStackInSlot(0);
        }

        @Override
        public void setOnContentsChanged(Runnable runnable) {
            handler.setOnContentsChanged(runnable);
        }

        @Override
        public boolean isEmpty() {
            return getStack().isEmpty();
        }

        @Override
        public Stream<TagKey<?>> getTags() {
            return getStack().getTags().map(t -> t);
        }
    }
}
