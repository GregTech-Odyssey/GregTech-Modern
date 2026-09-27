package com.gregtechceu.gtceu.utils;

import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.fastcollection.fastutil.O2OOpenCustomCacheHashMap;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * 归并同类栈的列表：继承 {@link ObjectArrayList}，另有一张内部 O2O 表记录「栈 → 条目状态」。
 *
 * <p>
 * 状态里是条目在列表中的<b>下标</b>与<b>是否已复制</b>：
 * <ul>
 * <li>新出现的栈直接放进列表，<b>不复制</b>，状态标记未复制——这份是调用方给的共享对象，
 * 不可修改；</li>
 * <li>再次加入同类栈时，若列表里那份还没复制，就先复制它、累加数量、按下标替换回列表，
 * 状态改成已复制；已复制则直接累加。</li>
 * </ul>
 * 于是「只出现一次的栈」一次复制都不做，只有真正需要改数量的才复制一次。
 *
 * <p>
 * 下标由内部维护，所以<b>不要用继承来的 {@code remove}</b>；{@link #clear} 会同时清掉索引。
 *
 * <p>
 * 典型用法是当掉落缓冲区：{@code dropCache.forEach(level, state, pos, stackList::add)}。
 */
public class StackList<K> extends ObjectArrayList<K> {

    /** 栈的复制与数量读写；ItemStack / FluidStack 各给一份实现即可。 */
    public interface StackOps<K> {

        @NotNull
        K copy(@NotNull K stack);

        long getCount(@NotNull K stack);

        K setCount(@NotNull K stack, long amount);
    }

    public static final StackOps<ItemStack> ITEM_STACK = new StackOps<>() {

        @Override
        public ItemStack copy(ItemStack stack) {
            return stack.copy();
        }

        @Override
        public long getCount(ItemStack stack) {
            return stack.getCount();
        }

        @Override
        public ItemStack setCount(ItemStack stack, long amount) {
            stack.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return stack;
        }
    };

    public static final StackOps<FluidStack> FLUID_STACK = new StackOps<>() {

        @Override
        public FluidStack copy(FluidStack stack) {
            return stack.copy();
        }

        @Override
        public long getCount(FluidStack stack) {
            return stack.getAmount();
        }

        @Override
        public FluidStack setCount(FluidStack stack, long amount) {
            stack.setAmount((int) Math.min(Integer.MAX_VALUE, amount));
            return stack;
        }
    };

    public static final StackOps<GenericStack> GENERIC_STACK = new StackOps<>() {

        @Override
        public GenericStack copy(GenericStack stack) {
            return stack;
        }

        @Override
        public long getCount(GenericStack stack) {
            return stack.amount();
        }

        @Override
        public GenericStack setCount(GenericStack stack, long amount) {
            return new GenericStack(stack.what(),stack.amount()+amount);
        }
    };

    /** 条目状态：列表下标 + 是否已复制（未复制的那份是共享对象，不可修改）。 */
    private record Entry(int index, boolean copied) {}

    private final O2OOpenCustomCacheHashMap<K, Entry> index;
    private final StackOps<K> ops;

    public StackList(@NotNull Hash.Strategy<? super K> strategy, @NotNull StackOps<K> ops) {
        this.index = new O2OOpenCustomCacheHashMap<>(strategy);
        this.ops = ops;
    }

    /**
     * 加入一个栈：列表里没有同类就原样放进去（不复制），有就并进那一份。
     *
     * <p>
     * 并入时若列表里那份还是共享对象，会先复制再改，因此不会写坏调用方给的栈。
     */
    @Override
    public boolean add(@NotNull K stack) {
        var entry = index.get(stack);
        if (entry == null) {
            index.put(stack, new Entry(size(), false));
            return super.add(stack);
        }
        var current = get(entry.index());
        if (entry.copied()) {
            ops.setCount(current, ops.getCount(current) + ops.getCount(stack));
        } else {
            var merged = ops.copy(current);
            ops.setCount(merged, ops.getCount(merged) + ops.getCount(stack));
            set(entry.index(), merged);
            index.put(stack, new Entry(entry.index(), true));
        }
        return true;
    }

    /** 清空列表与索引。 */
    @Override
    public void clear() {
        super.clear();
        index.clear();
    }
}
