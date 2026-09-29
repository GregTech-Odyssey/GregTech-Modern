package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.function.Function;

final class StockOrder {

    private static final int AVAILABLE = 0;
    private static final int SECONDARY = 1;
    private static final int NONE = 2;

    private StockOrder() {}

    static Comparator<ItemStack> of(PatternBuilderModel.Sort sort, Function<ItemStack, PatternBuilderModel.Stock> stock) {
        Comparator<ItemStack> rank = sort == PatternBuilderModel.Sort.CRAFTABLE ? Comparator.comparingInt(stack -> craftRank(stock.apply(stack))) :
                Comparator.comparingInt(stack -> stockRank(stock.apply(stack)));
        return rank.thenComparingLong(stack -> -stored(stock.apply(stack)));
    }

    private static int stockRank(PatternBuilderModel.Stock entry) {
        if (entry == null) return NONE;
        if (entry.total() > 0) return AVAILABLE;
        return entry.craftable() ? SECONDARY : NONE;
    }

    private static int craftRank(PatternBuilderModel.Stock entry) {
        if (entry == null) return NONE;
        if (entry.craftable()) return AVAILABLE;
        return entry.total() > 0 ? SECONDARY : NONE;
    }

    private static long stored(PatternBuilderModel.Stock entry) {
        return entry == null ? 0 : entry.total();
    }
}
