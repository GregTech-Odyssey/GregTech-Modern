package com.gregtechceu.gtceu.api.cover.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.hooks.IAEItem;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 物品注册 id 过滤器，规则见 {@link IdFilter}。
 *
 * <p>
 * 和 {@link TagItemFilter} 一样按物品的 AE uid 缓存结果：注册 id 是跟着物品走的，所以一个物品最多真算一次。
 */
public class IdItemFilter extends IdFilter<ItemStack, ItemFilter> implements ItemFilter {

    private final Int2BooleanOpenHashMap cache = new Int2BooleanOpenHashMap();

    protected IdItemFilter() {}

    public static IdItemFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static IdItemFilter loadFilter(CompoundTag tag, Consumer<ItemFilter> itemWriter) {
        var handler = new IdItemFilter();
        handler.itemWriter = itemWriter;
        handler.cache.clear();
        handler.loadIds(tag.getString("ids"));
        return handler;
    }

    @Override
    public void setIds(String text) {
        cache.clear();
        super.setIds(text);
    }

    @Override
    public boolean test(ItemStack itemStack) {
        if (isBlank()) return false;
        return cache.computeIfAbsent(((IAEItem) itemStack.getItem()).ae2$getUid(), k -> matchesAny(itemStack));
    }

    private boolean matchesAny(ItemStack itemStack) {
        var id = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        return id != null && super.matchesAny(id);
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
