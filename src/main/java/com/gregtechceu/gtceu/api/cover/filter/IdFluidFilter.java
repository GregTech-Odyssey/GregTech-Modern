package com.gregtechceu.gtceu.api.cover.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.hooks.IAEFluid;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 流体注册 id 过滤器，规则见 {@link IdFilter}。
 *
 * <p>
 * 和 {@link TagFluidFilter} 一样按流体的 AE uid 缓存结果：注册 id 是跟着流体走的，所以一个流体最多真算一次。
 */
public class IdFluidFilter extends IdFilter<FluidStack, FluidFilter> implements FluidFilter {

    private final Int2BooleanOpenHashMap cache = new Int2BooleanOpenHashMap();

    protected IdFluidFilter() {}

    public static IdFluidFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static IdFluidFilter loadFilter(CompoundTag tag, Consumer<FluidFilter> itemWriter) {
        var handler = new IdFluidFilter();
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
    public boolean test(FluidStack fluidStack) {
        if (isBlank()) return false;
        return cache.computeIfAbsent(((IAEFluid) fluidStack.getFluid()).ae2$getUid(), k -> matchesAny(fluidStack));
    }

    private boolean matchesAny(FluidStack fluidStack) {
        var id = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
        return id != null && super.matchesAny(id);
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
